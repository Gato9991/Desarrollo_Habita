package cl.habita.mobile.data

import java.time.LocalDate

data class Query(val name: String = "Plaza de Armas · San Bernardo", val lat: String = "-33.5922368", val lon: String = "-70.704706", val date: String = "") {
    fun validated(): Triple<Double, Double, LocalDate> {
        val latitude = lat.trim().replace(',', '.').toDoubleOrNull()
        val longitude = lon.trim().replace(',', '.').toDoubleOrNull()
        require(latitude != null && latitude.isFinite() && latitude in -90.0..90.0) { "Ingresa una latitud entre -90 y 90." }
        require(longitude != null && longitude.isFinite() && longitude in -180.0..180.0) { "Ingresa una longitud entre -180 y 180." }
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date)) { "La fecha debe tener formato AAAA-MM-DD." }
        val day = runCatching { LocalDate.parse(date) }.getOrElse { throw IllegalArgumentException("La fecha ingresada no existe.") }
        return Triple(latitude, longitude, day)
    }
}

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.obj(key: String): Map<String, Any?> = get(key) as? Map<String, Any?> ?: emptyMap()
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.objects(key: String): List<Map<String, Any?>> = (get(key) as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()
fun Map<String, Any?>.number(key: String): Double? = (get(key) as? Number)?.toDouble()
fun Map<String, Any?>.text(key: String): String = get(key)?.toString().orEmpty()
