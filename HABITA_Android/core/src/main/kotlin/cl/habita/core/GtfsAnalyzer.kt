package cl.habita.core

import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile
import kotlin.math.*

data class FeedMetadata(val version: String, val start: LocalDate, val end: LocalDate, val timezone: String?) {
    fun suggestedDate(): LocalDate = LocalDate.now(timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()).coerceIn(start, end)
}

/** Motor GTFS nativo. Port de las reglas v1.2.1 del repositorio, con lectura CSV en flujo. */
class GtfsAnalyzer(private val file: File) {
    private data class Stop(val id: String, val name: String, val lat: Double, val lon: Double, val boardable: Boolean)
    private data class Route(val id: String, val short: String?, val long: String?)
    private data class Trip(val id: String, val route: String, val service: String, val direction: String, val headsign: String)
    private data class Frequency(val start: Int, val end: Int, val headway: Int, val estimated: Boolean)
    private data class Calendar(val service: String, val start: String, val end: String, val days: List<Boolean>)
    private data class ExceptionDay(val service: String, val date: String, val type: String)
    private data class TimeRow(val trip: Trip, val stop: String, val departure: Int, val sequence: Int, var offset: Int = 0)
    private data class Instance(val trip: String, val block: Int, val index: Int)
    private data class Row(val header: Map<String, Int>, val cells: List<String>) {
        operator fun get(name: String): String = header[name]?.let { cells.getOrNull(it) }.orEmpty()
    }
    private val entries: Map<String, String>
    private val stops = ArrayList<Stop>()
    private val routes = linkedMapOf<String, Route>()
    private val trips = HashMap<String, Trip>()
    private val frequencies = HashMap<String, MutableList<Frequency>>()
    private val calendar = ArrayList<Calendar>()
    private val exceptions = ArrayList<ExceptionDay>()
    private var multipleTimezones = false
    val metadata: FeedMetadata

    init {
        require(file.isFile) { "No se encontró el archivo GTFS." }
        entries = ZipFile(file).use { zip ->
            val all = zip.entries().asSequence().filter { !it.isDirectory }.toList()
            require(all.all { it.size in 0..(512L * 1024 * 1024) } && all.sumOf { it.size } <= 1024L * 1024 * 1024) { "El ZIP descomprimido supera el límite admitido." }
            val text = all.filter { it.name.endsWith(".txt") }
            val names = text.map { it.name.substringAfterLast('/') }
            require(names.distinct().size == names.size) { "El ZIP tiene nombres de tablas GTFS duplicados." }
            text.associate { it.name.substringAfterLast('/') to it.name }
        }
        val required = listOf("agency.txt", "routes.txt", "trips.txt", "stop_times.txt", "stops.txt", "feed_info.txt")
        require(required.all { it in entries }) { "Faltan tablas GTFS: ${required.filterNot { it in entries }.joinToString()}" }
        require("calendar.txt" in entries || "calendar_dates.txt" in entries) { "El feed necesita calendar.txt o calendar_dates.txt." }
        // Comprobar esta cabecera antes de aceptar una importación, sin recorrer los 50 MB de horarios.
        ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(entries.getValue("stop_times.txt"))).bufferedReader().use { reader ->
            val firstLine = reader.lineSequence().firstOrNull { it.isNotBlank() } ?: error("stop_times.txt está vacío.")
            val header = Csv.parse(firstLine).map { it.trim().removePrefix("\uFEFF") }
            require(listOf("trip_id", "departure_time", "stop_id", "stop_sequence").all { it in header }) { "stop_times.txt no tiene todas las columnas obligatorias." }
        } }
        var info: Row? = null
        table("feed_info.txt", setOf("feed_start_date", "feed_end_date")) { if (info == null) info = it }
        requireNotNull(info) { "feed_info.txt está vacío." }
        val zones = sortedSetOf<String>()
        table("agency.txt") { if (it["agency_timezone"].isNotBlank()) zones.add(it["agency_timezone"]) }
        multipleTimezones = zones.size > 1
        val start = LocalDate.parse(info!!["feed_start_date"], DateTimeFormatter.BASIC_ISO_DATE)
        val end = LocalDate.parse(info!!["feed_end_date"], DateTimeFormatter.BASIC_ISO_DATE)
        require(!end.isBefore(start)) { "La vigencia del feed es inválida." }
        metadata = FeedMetadata(info!!["feed_version"].ifBlank { "Sin versión" }, start, end, zones.firstOrNull())
        table("routes.txt", setOf("route_id")) { routes[it["route_id"]] = Route(it["route_id"], it["route_short_name"].ifBlank { null }, it["route_long_name"].ifBlank { null }) }
        table("trips.txt", setOf("trip_id", "route_id", "service_id")) {
            trips[it["trip_id"]] = Trip(it["trip_id"], it["route_id"], it["service_id"], it["direction_id"], it["trip_headsign"])
        }
        table("stops.txt", setOf("stop_id", "stop_name", "stop_lat", "stop_lon")) {
            val lat = it["stop_lat"].toDoubleOrNull()
            val lon = it["stop_lon"].toDoubleOrNull()
            if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) {
                stops.add(Stop(it["stop_id"], it["stop_name"], lat, lon, it["location_type"] in setOf("", "0")))
            }
        }
        require(stops.isNotEmpty() && trips.isNotEmpty()) { "El feed no contiene paradas o viajes utilizables." }
        table("calendar.txt", setOf("service_id", "start_date", "end_date") + WEEKDAYS) {
            calendar.add(Calendar(it["service_id"], it["start_date"], it["end_date"], WEEKDAYS.map { day -> it[day] == "1" }))
        }
        table("calendar_dates.txt", setOf("service_id", "date", "exception_type")) {
            exceptions.add(ExceptionDay(it["service_id"], it["date"], it["exception_type"]))
        }
        table("frequencies.txt", setOf("trip_id", "start_time", "end_time", "headway_secs")) {
            val headway = it["headway_secs"].toIntOrNull()
            require(headway != null && headway > 0) { "frequencies.txt: headway_secs debe ser mayor que cero." }
            val from = timeToSeconds(it["start_time"])
            val until = timeToSeconds(it["end_time"])
            require(until > from) { "frequencies.txt: el fin del bloque debe ser posterior al inicio." }
            require((until - from).toLong() / headway < 1_000_000) { "El bloque GTFS genera demasiadas instancias." }
            frequencies.getOrPut(it["trip_id"]) { ArrayList() }.add(Frequency(from, until, headway, it["exact_times"] != "1"))
        }
        frequencies.values.forEach { it.sortBy { block -> block.start } }
    }

    private fun table(name: String, required: Set<String> = emptySet(), consume: (Row) -> Unit) {
        val path = entries[name] ?: return
        ZipFile(file).use { zip -> zip.getInputStream(zip.getEntry(path)).bufferedReader(Charsets.UTF_8).use { reader ->
            var header: Map<String, Int>? = null
            Csv.rows(reader) { cells ->
                if (header == null) {
                    header = cells.mapIndexed { i, value -> value.trim().removePrefix("\uFEFF") to i }.toMap()
                    require(required.all { it in header!! }) { "$name: faltan columnas ${required.filterNot { it in header!! }.joinToString()}" }
                } else consume(Row(header!!, cells))
            }
            require(header != null) { "$name está vacío." }
        } }
    }

    fun activeServices(date: LocalDate): List<String> {
        require(date in metadata.start..metadata.end) { "La fecha debe estar entre ${metadata.start} y ${metadata.end} (feed ${metadata.version})." }
        val compact = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val active = calendar.filter { compact >= it.start && compact <= it.end && it.days[date.dayOfWeek.value - 1] }.mapTo(sortedSetOf()) { it.service }
        val today = exceptions.filter { it.date == compact }
        active.addAll(today.filter { it.type == "1" }.map { it.service })
        active.removeAll(today.filter { it.type == "2" }.map { it.service }.toSet())
        return active.toList()
    }

    fun analyze(lat: Double, lon: Double, date: LocalDate, name: String = "", progress: (String) -> Unit = {}): JsonObject {
        require(lat.isFinite() && lat in -90.0..90.0 && lon.isFinite() && lon in -180.0..180.0) { "Latitud o longitud inválida." }
        val services = activeServices(date)
        progress("Buscando paraderos cercanos…")
        val distances = stops.associate { it.id to distance(lat, lon, it.lat, it.lon) }
        val nearby = stops.filter { distances.getValue(it.id) <= 1000 }
        val nearbyIds = nearby.mapTo(HashSet()) { it.id }
        require(nearby.isNotEmpty()) { "No hay paradas dentro de 1.000 m de esta ubicación en el feed seleccionado." }
        val activeTrips = trips.filterValues { it.service in services }
        val first = HashMap<String, Pair<Int, Int>>()
        val present = HashSet<String>()
        val relevant = ArrayList<TimeRow>()
        val otherPickup = sortedSetOf<String>()
        var count = 0
        progress("Leyendo horarios del feed en este dispositivo…")
        table("stop_times.txt", setOf("trip_id", "departure_time", "stop_id", "stop_sequence")) { row ->
            if (++count % 4096 == 0) progress("Leyendo horarios · ${count / 1000} mil registros…")
            val id = row["trip_id"]
            val stop = row["stop_id"]
            if (stop in nearbyIds) present.add(stop)
            val pickup = row["pickup_type"]
            if (pickup !in KNOWN_PICKUPS) otherPickup.add(pickup)
            val trip = activeTrips[id]
            if (trip != null) {
                val seq = row["stop_sequence"].toIntOrNull() ?: error("stop_times.txt: stop_sequence inválido.")
                val departure = timeToSeconds(row["departure_time"])
                if (first[id] == null || seq < first.getValue(id).first) first[id] = seq to departure
                if (stop in nearbyIds && pickup != "1") relevant.add(TimeRow(trip, stop, departure, seq))
            }
        }
        relevant.forEach { it.offset = it.departure - first.getValue(it.trip.id).second }
        val physical = nearby.filter { it.boardable && it.id in present }.minByOrNull { distances.getValue(it.id) }
            ?: error("No hay paradas abordables dentro de 1.000 m.")
        val activeIds = relevant.mapTo(HashSet()) { it.stop }
        val active = nearby.filter { it.boardable && it.id in activeIds }.minByOrNull { distances.getValue(it.id) }
        progress("Calculando recorridos, intervalos y cobertura…")
        val physicalSummary = stopSummary(physical, distances)
        val activeSummary = if (active != null) stopDetail(active, relevant.filter { it.stop == active.id }, distances).toMutableMap()
            else json("note" to "No hay paradas abordables con viajes activos dentro de 1.000 m para esta fecha.",
                "routes_serving_stop" to emptyList<String>(), "route_count" to 0, "destinations" to emptyList<Any>(),
                "service_window" to null, "peak_departures" to emptyMap<String, Any>(), "headway_by_route_direction" to emptyList<Any>()).toMutableMap()
        activeSummary["same_as_nearest_physical_stop"] = active?.id == physical.id
        val limitations = mutableListOf(
            "Distancias geodésicas en línea recta (Haversine), sin considerar calles, cruces o barreras.",
            "Caminata teórica a 1,4 m/s; no es una ruta peatonal verificada.",
            "Horarios programados de GTFS estático, sin atrasos, cancelaciones ni llegadas en tiempo real.",
            "Si exact_times es distinto de 1, las salidas de frequencies.txt son estimaciones.",
            "Eventos por paradero y viajes únicos son conteos distintos; no equivalen a vehículos físicos.",
            "Se excluyen las filas con pickup_type=1. Las horas mayores a 24:00 pertenecen al mismo día de servicio.",
            "Feed ${metadata.version}, vigente entre ${metadata.start} y ${metadata.end}.",
        )
        if (otherPickup.isNotEmpty()) limitations.add("Abordaje condicionado o no documentado: pickup_type ${otherPickup.joinToString()}; solo se excluye 1.")
        if (multipleTimezones) limitations.add("El feed declara varias zonas horarias; se utiliza la primera en orden alfabético.")
        val zone = metadata.timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        if (zone == null) limitations.add("Zona horaria ausente o desconocida: fecha de generación en UTC.")
        return json(
            "metadata" to json("feed_version" to metadata.version,
                "feed_start_date" to metadata.start.format(DateTimeFormatter.BASIC_ISO_DATE),
                "feed_end_date" to metadata.end.format(DateTimeFormatter.BASIC_ISO_DATE),
                "analysis_date" to date.toString(), "active_service_ids" to services,
                "timezone" to metadata.timezone,
                (if (zone == null) "generated_at_utc" else "generated_at") to ZonedDateTime.now(zone ?: ZoneId.of("UTC")).toString()),
            "location" to json("name" to name.take(160), "lat" to lat, "lon" to lon),
            "nearest_physical_stop" to physicalSummary, "nearest_active_stop" to activeSummary,
            "closest_stop_analysis" to activeSummary,
            "area_analysis" to json("radius_500m" to area(500, stops, relevant, distances, progress),
                "radius_800m" to area(800, stops, relevant, distances, progress)),
            "habita_inputs" to json("distancia_paradero_m" to distances.getValue(physical.id),
                "paraderos_500m" to nearby.count { it.boardable && it.id in present && distances.getValue(it.id) <= 500 },
                "source" to "Paradas físicas abordables presentes en stop_times.txt; no equivale a servicio activo."),
            "map_stops" to nearby.filter { distances.getValue(it.id) <= 800 }.map {
                json("id" to it.id, "name" to it.name, "lat" to it.lat, "lon" to it.lon, "active" to (it.id in activeIds))
            },
            "limitations" to limitations,
        )
    }

    private fun stopSummary(stop: Stop, distances: Map<String, Double>): JsonObject = json(
        "stop_id" to stop.id, "stop_name" to stop.name,
        "geodesic_distance_m" to rounded(distances.getValue(stop.id), 1),
        "theoretical_walk_time_min" to rounded(distances.getValue(stop.id) / 1.4 / 60, 1),
    )

    private fun departures(row: TimeRow, consume: (Int, Instance) -> Unit) {
        val blocks = frequencies[row.trip.id]
        if (blocks.isNullOrEmpty()) consume(row.departure, Instance(row.trip.id, -1, 0))
        else blocks.forEachIndexed { blockIndex, f ->
            var n = 0
            while (f.start + n.toLong() * f.headway < f.end) {
                consume(f.start + n * f.headway + row.offset, Instance(row.trip.id, blockIndex, n))
                n++
            }
        }
    }

    private fun stopDetail(stop: Stop, rows: List<TimeRow>, distances: Map<String, Double>): JsonObject {
        val seconds = ArrayList<Int>()
        rows.forEach { departures(it) { sec, _ -> seconds.add(sec) } }
        val destinations = rows.map { r ->
            val route = routes[r.trip.route]
            json("route_id" to r.trip.route, "route_short_name" to route?.short.orEmpty(),
                "route_long_name" to route?.long.orEmpty(), "direction_id" to r.trip.direction,
                "trip_headsign" to r.trip.headsign, "service_id" to r.trip.service)
        }.distinct()
        val headway = rows.groupBy { it.trip.route to it.trip.direction }.entries.sortedWith(compareBy({ it.key.first }, { it.key.second })).map { (key, group) ->
            json("route_id" to key.first, "route_short_name" to routes[key.first]?.short,
                "route_long_name" to routes[key.first]?.long, "direction_id" to key.second,
                "headway" to PEAKS.mapValues { (_, window) -> headway(group, window.first, window.second) })
        }
        val last = seconds.maxOrNull()
        return stopSummary(stop, distances) + json(
            "routes_serving_stop" to rows.map { it.trip.route }.distinct().sorted(),
            "route_count" to rows.map { it.trip.route }.distinct().size,
            "destinations" to destinations,
            "service_window" to if (seconds.isEmpty()) null else json(
                "first_departure" to secondsToTime(seconds.min()), "last_departure" to secondsToTime(last!!),
                "note" to if (last >= 86400) "%02d corresponde a las %02d horas del día calendario siguiente, dentro del mismo día de servicio GTFS.".format(last / 3600, last / 3600 - 24) else null),
            "peak_departures" to PEAKS.mapValues { (_, window) -> json("window" to windowLabel(window), "total_departures" to seconds.count { it >= window.first && it < window.second }) },
            "headway_by_route_direction" to headway,
        )
    }

    private fun headway(rows: List<TimeRow>, from: Int, until: Int): JsonObject? {
        var overlap = 0L
        var weighted = 0.0
        val intervals = ArrayList<Triple<Int, Int, String>>()
        val generated = ArrayList<Int>()
        rows.forEach { row ->
            frequencies[row.trip.id]?.forEach { f ->
                val start = f.start + row.offset
                val end = f.end + row.offset
                intervals.add(Triple(start, end, row.trip.id))
                val amount = (min(end, until) - max(start, from)).coerceAtLeast(0)
                overlap += amount
                weighted += amount.toDouble() * f.headway
            }
            departures(row) { sec, _ -> if (sec >= from && sec < until) generated.add(sec) }
        }
        val relevant = intervals.filter { min(it.second, until) > max(it.first, from) }
        val concurrent = relevant.indices.any { i -> (i + 1 until relevant.size).any { j ->
            val a = relevant[i]; val b = relevant[j]
            a.third != b.third && max(a.first, b.first) < min(a.second, b.second)
        } }
        if (overlap == 0L && generated.isEmpty()) return null
        val result = linkedMapOf<String, Any?>("scheduled_departures" to generated.size,
            "distinct_trip_id_patterns" to rows.map { it.trip.id }.distinct().size,
            "concurrent_patterns_detected" to concurrent)
        if (overlap > 0) {
            result["weighted_block_headway_min"] = rounded(weighted / overlap / 60, 2)
            result["method"] = "headway_ponderado_por_solapamiento_temporal_con_offset"
        }
        if (generated.size >= 2) result["effective_interval_from_generated_departures_min"] = rounded((generated.max() - generated.min()).toDouble() / (generated.size - 1) / 60, 2)
        if (concurrent) result["note"] = CONCURRENT_NOTE
        return result
    }

    private fun area(radius: Int, stops: List<Stop>, rows: List<TimeRow>, distances: Map<String, Double>, progress: (String) -> Unit): JsonObject {
        val within = stops.filter { distances.getValue(it.id) <= radius }.mapTo(HashSet()) { it.id }
        val localRows = rows.filter { it.stop in within }
        val activeRoutes = localRows.map { it.trip.route }.distinct().sorted().map { json("route_id" to it, "route_short_name" to (routes[it]?.short ?: it)) }
        val peaks = PEAKS.mapValues { (_, window) ->
            var count = 0
            val identities = HashSet<Instance>()
            val byRoute = sortedMapOf<String, Pair<Int, MutableSet<Instance>>>()
            localRows.forEachIndexed { i, row ->
                if (i % 1000 == 0) progress("Calculando cobertura de $radius m…")
                departures(row) { sec, instance -> if (sec >= window.first && sec < window.second) {
                    count++
                    identities.add(instance)
                    val previous = byRoute[row.trip.route] ?: (0 to HashSet())
                    previous.second.add(instance)
                    byRoute[row.trip.route] = previous.first + 1 to previous.second
                } }
            }
            json("window" to if (within.isEmpty()) null else windowLabel(window),
                "stop_departure_events" to count, "unique_trip_instances" to identities.size,
                "by_route" to byRoute.mapValues { (_, v) -> json("stop_departure_events" to v.first, "unique_trip_instances" to v.second.size) })
        }
        return json("radius_m" to radius, "stop_points" to within.size,
            "active_routes" to activeRoutes, "active_routes_count" to activeRoutes.size,
            "morning_peak" to peaks["morning_peak"], "afternoon_peak" to peaks["afternoon_peak"])
    }

    companion object {
        private val WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        private val KNOWN_PICKUPS = setOf("", "0", "1")
        private val PEAKS = linkedMapOf("morning_peak" to (25200 to 32400), "afternoon_peak" to (61200 to 68400))
        private val TIME = Regex("^\\s*(\\d{1,3}):([0-5]\\d):([0-5]\\d)\\s*$")
        private const val CONCURRENT_NOTE = "Se detectaron bloques de distintos trip_id del mismo route_id+direction_id solapados en el tiempo dentro de esta ventana. weighted_block_headway_min es un promedio ponderado de los headways declarados, NO la frecuencia combinada real de servicios simultáneos (que sería menor)."
        private fun windowLabel(window: Pair<Int, Int>) = "${secondsToTime(window.first)}-${secondsToTime(window.second)}"
        fun timeToSeconds(value: String): Int {
            val match = TIME.matchEntire(value)
                ?: throw IllegalArgumentException("Hora GTFS inválida: $value")
            return match.groupValues[1].toInt() * 3600 + match.groupValues[2].toInt() * 60 + match.groupValues[3].toInt()
        }
        fun secondsToTime(seconds: Int): String {
            require(seconds >= 0) { "Hora GTFS negativa." }
            return "%02d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
        }
        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val a = sin(Math.toRadians(lat2 - lat1) / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
            return 2 * 6371000 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        }
        // Igual que round(float, n) de Python: redondea el valor binario exacto, no value*10^n.
        private fun rounded(value: Double, decimals: Int): Double = BigDecimal(value).setScale(decimals, RoundingMode.HALF_EVEN).toDouble()
    }
}
