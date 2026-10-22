package cl.habita.core

import kotlin.math.*

/** Evaluación geográfica nativa de Pruebas/modelo.py, versión 0.3.0-prueba.1.
 * Los puntajes de series se preparan desde los originales: no se puntúa su media.
 */
class TerritoryAnalyzer(private val catalog: JsonObject) {
    init {
        require(catalog["schema"] == 1 || (catalog["schema"] as? Number)?.toInt() == 1)
        require(catalog["model"] == VERSION) { "Versión del catálogo no compatible." }
    }

    fun analyze(lat: Double, lon: Double, name: String): JsonObject {
        require(lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) { "Coordenadas no válidas." }
        val parameters = linkedMapOf(
            "aire" to station(lat, lon, catalog.rows("air"), listOf("mp25" to .7, "o3" to .3)),
            "paraderos" to accessibility(lat, lon, catalog.rows("stops"), 500.0, 1000.0, 10.0, .6),
            "salud" to accessibility(lat, lon, catalog.rows("health"), 2000.0, 5000.0, 20.0, .7),
            "bienestar" to station(lat, lon, catalog.rows("thermal"), listOf("temperatura" to .6, "humedad" to .4)),
        )
        return json("model" to VERSION, "internal_score_scale" to 100, "display_score_scale" to 10,
            "location" to json("name" to name, "lat" to lat, "lon" to lon),
            "parameters" to parameters, "source_commit" to catalog["source_commit"],
            "air_period" to catalog["air_period"], "thermal_period" to catalog["thermal_period"],
            "catalogs" to catalog["catalogs"], "warnings" to catalog["warnings"],
            "sources" to catalog["sources"],
            "note" to "Fuentes históricas del equipo. Estaciones por cercanía hasta 30 km. Distancias en línea recta. Propuesta pendiente de aprobación.")
    }

    private fun station(lat: Double, lon: Double, stations: List<JsonObject>, weights: List<Pair<String, Double>>): JsonObject {
        val closest = stations.filter { s -> weights.any { s.map("resumenes").map(it.first).num("puntaje_100") != null } }
            .map { distance(lat, lon, it.num("latitud")!!, it.num("longitud")!!) to it }
            .sortedWith(compareBy<Pair<Double, JsonObject>> { it.first }.thenBy { it.second.str("codigo").toLongOrNull() ?: Long.MAX_VALUE })
            .firstOrNull()?.takeIf { it.first <= 30_000 }
        val summaries = closest?.second?.map("resumenes").orEmpty()
        val components = weights.associate { (key, _) -> key to summaries.map(key).num("puntaje_100") }
        return combined(components, weights) + json("station" to closest?.second, "distance_m" to closest?.first,
            "status" to if (closest == null) "Sin estación admisible a 30 km" else "Fuente histórica disponible")
    }

    private fun accessibility(lat: Double, lon: Double, entries: List<JsonObject>, radius: Double, limit: Double, countFactor: Double, weight: Double): JsonObject {
        val neighbors = entries.map { distance(lat, lon, it.num("latitud")!!, it.num("longitud")!!) to it }
            .sortedWith(compareBy<Pair<Double, JsonObject>> { it.first }.thenBy { it.second.str("codigo") })
        val closest = neighbors.firstOrNull()
        val count = if (closest == null) null else neighbors.count { it.first <= radius }
        val scores = linkedMapOf("distancia" to closest?.first?.let { (100 * (1 - it / limit)).coerceIn(0.0, 100.0) },
            "cantidad" to count?.let { (it * countFactor).coerceAtMost(100.0) })
        return combined(scores, listOf("distancia" to weight, "cantidad" to 1 - weight)) + json(
            "nearest" to closest?.second, "distance_m" to closest?.first, "count" to count, "radius_m" to radius,
            "nearby" to neighbors.filter { it.first <= radius }.take(100).map { it.second + ("distance_m" to it.first) },
            "status" to if (closest == null) "Sin catálogo admisible" else "Catálogo incluido disponible")
    }

    companion object {
        const val VERSION = "0.3.0-prueba.1"
        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val a = Math.toRadians(lat1); val b = Math.toRadians(lat2)
            val h = sin((b-a)/2).pow(2) + cos(a)*cos(b)*sin(Math.toRadians(lon2-lon1)/2).pow(2)
            return 2 * 6_371_000 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
        private fun combined(values: Map<String, Double?>, weights: List<Pair<String, Double>>): JsonObject {
            val coverage = weights.filter { values[it.first] != null }.sumOf { it.second }
            return json("score" to if (coverage > 0) weights.sumOf { (values[it.first] ?: 0.0) * it.second } / coverage else null,
                "coverage" to coverage, "components" to values)
        }
        fun index(report: JsonObject?): IndexResult {
            val parameters = report.orEmpty().map("parameters")
            fun group(key: String, id: String, title: String, station: Boolean, weights: List<Pair<String, Double>>): Variable {
                val p = parameters.map(key)
                val s = p.map("station"); val summaries = s.map("resumenes")
                val scope = if (station) s.str("nombre").ifBlank { "Sin estación admisible a 30 km" } else "Distancia en línea recta"
                val components = weights.map { (component, weight) ->
                    val r = summaries.map(component)
                    val (label, value, unit) = when (component) {
                        "mp25" -> Triple("MP2,5 · media descriptiva", r.num("valor_medio"), "µg/m³")
                        "o3" -> Triple("O₃ · media original", r.num("valor_medio"), "ppb")
                        "temperatura" -> Triple("Temperatura exterior · media", r.num("valor_medio"), "°C")
                        "humedad" -> Triple("Humedad relativa · media", r.num("valor_medio"), "%")
                        "distancia" -> Triple("${if (key == "salud") "Centro de salud" else "Paradero"} más cercano", p.num("distance_m"), "m")
                        else -> Triple("Cantidad en ${if (key == "salud") "2 km" else "500 m"}", p.num("count"), "puntos")
                    }
                    Component(label, value, unit, weight, p.map("components").num(component))
                }
                return Variable(id, title, scope, components, p.num("score"), p.num("coverage") ?: 0.0)
            }
            val groups = listOf(
                group("aire", "calidad_aire", "Calidad del aire", true, listOf("mp25" to .7, "o3" to .3)),
                group("bienestar", "confort_termico", "Confort térmico", true, listOf("temperatura" to .6, "humedad" to .4)),
                group("paraderos", "paraderos", "Accesibilidad a paraderos", false, listOf("distancia" to .6, "cantidad" to .4)),
                group("salud", "salud", "Accesibilidad a salud", false, listOf("distancia" to .7, "cantidad" to .3)),
            )
            val coverage = groups.sumOf { .25 * it.coverage }
            val updated = groups.map { it.copy(effectiveWeight = .25 * it.coverage,
                finalWeight = if (coverage > 0) .25 * it.coverage / coverage else 0.0) }
            val score = if (coverage >= .75 - 1e-12) updated.sumOf { (it.score ?: 0.0) * it.finalWeight } else null
            return IndexResult(score, coverage, updated, VERSION, 10, .25, .75)
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun JsonObject.map(key: String): JsonObject = get(key) as? JsonObject ?: emptyMap()
@Suppress("UNCHECKED_CAST")
internal fun JsonObject.rows(key: String): List<JsonObject> = (get(key) as? List<*>)?.mapNotNull { it as? JsonObject }.orEmpty()
internal fun JsonObject.num(key: String): Double? = (get(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }
internal fun JsonObject.str(key: String): String = get(key)?.toString().orEmpty()
