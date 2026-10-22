package cl.habita.mobile.data

import cl.habita.core.JsonObject
import java.util.Locale

fun onlineLines(source: JsonObject): List<String> {
    val d = source.obj("data")
    fun value(key: String) = d.number(key)?.let { String.format(Locale.forLanguageTag("es-CL"), "%.1f", it) } ?: "Sin dato"
    return when (source.text("id")) {
        "weather" -> listOf("Temperatura exterior: ${value("temperature_c")} °C", "Humedad relativa: ${value("humidity_percent")}%")
        "air" -> listOf("MP2,5: ${value("pm25_ug_m3")} µg/m³", "Ozono O₃: ${value("ozone_ug_m3")} µg/m³")
        "health" -> listOf("Establecimientos admisibles en 2 km: ${d.number("count_2km")?.toInt() ?: "Sin dato"}",
            d.obj("nearest").takeIf { it.isNotEmpty() }?.let { "Más cercano dentro de 5 km: ${it.text("nombre")} · ${String.format(Locale.forLanguageTag("es-CL"), "%.1f", it.number("distance_m"))} m" } ?: "Sin establecimientos admisibles en 5 km")
        "volcano" -> d.objects("matches").map { "${it.text("volcan")} · ${it.text("peligro")}" }.ifEmpty { listOf("Sin intersecciones con la capa consultada") }
        else -> emptyList()
    }
}
