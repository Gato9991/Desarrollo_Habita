package cl.habita.core

import java.net.URLEncoder
import java.text.Normalizer

/** Consultas complementarias. Nunca sustituyen silenciosamente una serie histórica. */
class PublicSources(private val readJson: (String) -> JsonObject) {
    fun weather(lat: Double, lon: Double): JsonObject {
        val url = WEATHER + query("latitude" to lat, "longitude" to lon, "current" to "temperature_2m,relative_humidity_2m", "timezone" to "America/Santiago")
        val response = readJson(url); val current = response.map("current")
        require(current.str("time").isNotBlank()) { "Falta la fecha de los datos meteorológicos." }
        val t = current.num("temperature_2m")?.takeIf { it in -100.0..70.0 }
        val h = current.num("relative_humidity_2m")?.takeIf { it in 0.0..100.0 }
        require(t != null || h != null) { "No hay temperatura ni humedad disponibles." }
        require(response.map("current_units").str("temperature_2m") == "°C" && response.map("current_units").str("relative_humidity_2m") == "%") { "Unidades meteorológicas no compatibles." }
        return json("source_url" to url, "date" to current["time"], "provider" to "Open-Meteo · modelo meteorológico",
            "temperature_c" to t, "humidity_percent" to h, "grid_lat" to response["latitude"], "grid_lon" to response["longitude"],
            "note" to "Condiciones exteriores modeladas. No son una medición interior ni el resumen térmico histórico del índice.")
    }

    fun air(lat: Double, lon: Double): JsonObject {
        val url = AIR + query("latitude" to lat, "longitude" to lon, "current" to "pm2_5,ozone", "domains" to "cams_global", "timezone" to "America/Santiago")
        val response = readJson(url); val current = response.map("current")
        require(current.str("time").isNotBlank()) { "Falta la fecha del dato de aire." }
        fun concentration(key: String): Double? = current.num(key)?.takeIf { it >= 0 }
        val pm = concentration("pm2_5"); val o3 = concentration("ozone")
        require(pm != null || o3 != null) { "No hay concentraciones disponibles." }
        val units = response.map("current_units")
        require(listOf("pm2_5", "ozone").all { units.str(it).replace('µ', 'μ') == "μg/m³" }) { "Unidades de aire no compatibles." }
        return json("source_url" to url, "date" to current["time"], "provider" to "Open-Meteo · Copernicus CAMS global",
            "pm25_ug_m3" to pm, "ozone_ug_m3" to o3, "grid_lat" to response["latitude"], "grid_lon" to response["longitude"],
            "note" to "Estimación de una celda de aproximadamente 45 km. No es una estación SINCA ni un promedio anual. O₃ ya viene en µg/m³.")
    }

    fun health(lat: Double, lon: Double): JsonObject {
        val dLat = Math.toDegrees(5000.0 / 6_371_000)
        val dLon = dLat / kotlin.math.cos(Math.toRadians(lat)).coerceAtLeast(.01)
        val bbox = "${(lon-dLon).coerceAtLeast(-180.0)},${(lat-dLat).coerceAtLeast(-90.0)},${(lon+dLon).coerceAtMost(180.0)},${(lat+dLat).coerceAtMost(90.0)},urn:ogc:def:crs:OGC:1.3:CRS84"
        val url = HEALTH + query("service" to "WFS", "version" to "2.0.0", "request" to "GetFeature",
            "typeNames" to "EstablecimientosdesaluddeChile2026:establecimientos_de_salud_junio_2026", "outputFormat" to "application/json",
            "srsName" to "EPSG:4326", "bbox" to bbox, "count" to 2000)
        val response = readJson(url)
        require(response.str("type") == "FeatureCollection") { "La fuente de salud no entregó GeoJSON válido." }
        val features = response.rows("features")
        val matched = response.num("numberMatched") ?: response.num("totalFeatures")
        require(matched != null && matched <= features.size) { "El servicio entregó un catálogo truncado; no se calcula un conteo incompleto." }
        val entries = features.mapNotNull { f ->
            val p = f.map("properties")
            if (normalize(p.str("estado")) != "vigente en operacion habitual" || normalize(p.str("prestador")) !in setOf("publico", "privado") || normalize(p.str("tipo")) !in HEALTH_TYPES) return@mapNotNull null
            val a = p.num("latitud"); val b = p.num("longitud"); val id = p.num("cod_vig")?.toLong()?.toString()
            if (a == null || b == null || a !in -90.0..90.0 || b !in -180.0..180.0 || id == null) return@mapNotNull null
            json("codigo" to id, "nombre" to p["nombre"], "tipo" to p["tipo"], "prestador" to p["prestador"],
                "latitud" to a, "longitud" to b, "distance_m" to TerritoryAnalyzer.distance(lat, lon, a, b))
        }.groupBy { it.str("codigo") }.values.mapNotNull { rows -> rows.first().takeIf { rows.distinct().size == 1 } }
            .filter { it.num("distance_m")!! <= 5000 }
            .sortedWith(compareBy<JsonObject> { it.num("distance_m") }.thenBy { it.str("codigo") })
        return json("source_url" to url, "date" to "Edición junio de 2026", "provider" to "IDE Chile · MINSAL/DEIS · WFS",
            "count_2km" to entries.count { it.num("distance_m")!! <= 2000 }, "nearest" to entries.firstOrNull(), "centers" to entries.take(100),
            "note" to "Consulta del catálogo oficial dentro de 5 km; conteo en 2 km. No confirma cupos, horarios ni disponibilidad de atención. El índice conserva el catálogo nacional incluido.")
    }

    fun volcanicHazard(lat: Double, lon: Double): JsonObject {
        val url = VOLCANO + query("f" to "json", "where" to "1=1", "geometry" to "$lon,$lat", "geometryType" to "esriGeometryPoint",
            "inSR" to 4326, "spatialRel" to "esriSpatialRelIntersects", "outFields" to "volcan,peligro", "returnGeometry" to false)
        val response = readJson(url)
        require(response["error"] == null && response["features"] is List<*>) { "La capa de amenazas no está disponible." }
        require(response["exceededTransferLimit"] != true) { "La consulta de amenazas está truncada." }
        return json("source_url" to url, "date" to "Cartografía consultada; sin fecha de actualización declarada", "provider" to "SENAPRED · capa de peligros volcánicos",
            "matches" to response.rows("features").map { it.map("attributes") },
            "note" to "Solo intersección con esta capa volcánica. Sin coincidencias no significa ausencia de riesgo. Inundaciones, fallas y remociones requieren capas y reglas validadas; no se asigna un puntaje.")
    }

    companion object {
        const val WEATHER = "https://api.open-meteo.com/v1/forecast"
        const val AIR = "https://air-quality-api.open-meteo.com/v1/air-quality"
        const val HEALTH = "https://geoportal.cl/geoserver/EstablecimientosdesaluddeChile2026/wfs"
        const val VOLCANO = "https://visor-grd.senapred.gob.cl/arcgis/rest/services/SIIE_AMENAZAS/amenazas/MapServer/2/query"
        private fun query(vararg values: Pair<String, Any>) = "?" + values.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v.toString(), "UTF-8")}" }
        private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase(java.util.Locale.ROOT).trim().replace(Regex("\\s+"), " ")
        private val HEALTH_TYPES = listOf("Hospital", "Clínica", "Centro de Salud Privado", "Centro de Salud Familiar (CESFAM)",
            "Centro Comunitario de Salud Familiar (CECOSF)", "Posta de Salud Rural (PSR)", "Consultorio General Urbano (CGU)", "Consultorio General Rural (CGR)",
            "Servicio de Atención Primaria de Urgencia (SAPU)", "Servicio de Atención Primaria de Urgencia de Alta Resolutividad (SAR)", "Servicio de Urgencia Rural (SUR)").map(::normalize).toSet()
    }
}
