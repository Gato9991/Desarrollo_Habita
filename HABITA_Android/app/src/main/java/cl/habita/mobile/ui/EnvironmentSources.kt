package cl.habita.mobile.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import cl.habita.mobile.*
import cl.habita.mobile.HabitaViewModel.Companion.format
import cl.habita.mobile.data.*

@Composable fun EnvironmentSources(state: AppState, vm: HabitaViewModel) {
    val report = state.environment ?: return
    val open = LocalUriHandler.current
    Text("Fuentes de este punto", style = MaterialTheme.typography.titleLarge)
    Notice("Aire: 14/10/2025–13/10/2026. Confort: 01/07/2026–13/10/2026. Son referencias históricas del entorno. La fecha de servicio GTFS no cambia estos períodos.")
    listOf("aire" to "Aire · SINCA", "bienestar" to "Confort exterior · DMC").forEach { (key, label) ->
        val p = report.obj("parameters").obj(key); val station = p.obj("station")
        CardBlock {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(p.text("status"), color = Muted)
            if (station.isNotEmpty()) {
                Text(station.text("nombre"), fontWeight = FontWeight.SemiBold)
                Text("${format(p.number("distance_m"))} m en línea recta · estación más cercana con datos admisibles", color = Muted, style = MaterialTheme.typography.bodySmall)
                station.obj("resumenes").forEach { (component, _) ->
                    val r = station.obj("resumenes").obj(component)
                    val label = when (component) { "mp25" -> "MP2,5"; "o3" -> "Ozono O₃"; "temperatura" -> "Temperatura"; "humedad" -> "Humedad"; else -> component }
                    Text("$label: ${r.text("estado")}", color = Muted, style = MaterialTheme.typography.bodySmall)
                    if (key == "aire") Text("${r.number("dias_admisibles")?.toInt() ?: 0} / 365 días admisibles · mínimo 256", color = Muted, style = MaterialTheme.typography.bodySmall)
                    else Text("Meses utilizados: ${(r["meses_usados"] as? List<*>)?.joinToString().orEmpty().ifBlank { "Ninguno" }}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { open.openUri(station.text("fuente")) }) { Text("Ver estación oficial") }
            }
            Text(if (key == "aire") "Los puntajes diarios se promedian. O₃ de SINCA está en ppb y se normaliza a µg/m³ a 25 °C y 1 atm. La media mostrada es descriptiva."
                else "Se puntúan observaciones antes de agregar por hora, día y mes. No es una medición dentro de la vivienda.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    val health = report.obj("parameters").obj("salud")
    CardBlock {
        Text("Salud · MINSAL / DEIS", style = MaterialTheme.typography.titleMedium)
        Text("Catálogo nacional · junio de 2026", color = Muted)
        Text("${health.number("count")?.toInt() ?: "Sin dato"} establecimientos admisibles en 2 km", fontWeight = FontWeight.SemiBold)
        val nearest = health.obj("nearest")
        if (nearest.isNotEmpty()) {
            Text(nearest.text("nombre")); Text("${nearest.text("tipo")} · ${nearest.text("prestador")}", color = Muted)
            Text("${format(health.number("distance_m"))} m en línea recta", color = Blue)
        }
        health.objects("nearby").take(5).forEach { Text("${it.text("nombre")} · ${format(it.number("distance_m"))} m", color = Muted, style = MaterialTheme.typography.bodySmall) }
        Text("11 tipos clínicos; prestador público o privado, operación habitual y código único. La cercanía no confirma atención, cupos u horarios.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    CardBlock {
        Text("Transporte · catálogo físico de buses", style = MaterialTheme.typography.titleMedium)
        val p = report.obj("parameters").obj("paraderos")
        Text("${p.obj("nearest").text("nombre")} · ${format(p.number("distance_m"))} m")
        Text("${p.number("count")?.toInt() ?: "Sin dato"} paraderos abordables en 500 m", color = Muted)
        Text("Este índice usa buses y no filtra por fecha. La sección Movilidad analiza aparte el calendario, los recorridos y los horarios programados del feed seleccionado.", color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { vm.mode(Evaluation.GTFS); vm.navigate(Page.EVALUATE) }) { Text("Consultar horarios de movilidad") }
    }
    Text("Consultas en línea", style = MaterialTheme.typography.titleLarge)
    Text("Al pulsar se envían las coordenadas de este punto a Open-Meteo, IDE Chile y SENAPRED. Los datos recibidos se muestran con su fecha y no cambian el índice histórico.", color = Muted, style = MaterialTheme.typography.bodySmall)
    PrimaryAction("Actualizar fuentes en línea", vm::refreshPublicSources, enabled = state.busy == null && !state.stale)
    report.objects("online_sources").forEach { source ->
        CardBlock {
            Text(source.text("label"), style = MaterialTheme.typography.titleMedium)
            Text("Consultado: ${source.text("checked_at")}", color = Muted, style = MaterialTheme.typography.bodySmall)
            if (source.text("status") == "ok") {
                val d = source.obj("data")
                Text(d.text("provider"), color = Blue)
                Text(d.text("date"), color = Muted, style = MaterialTheme.typography.bodySmall)
                onlineLines(source).forEach { Text(it, fontWeight = FontWeight.SemiBold) }
                Text(d.text("note"), color = Muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { open.openUri(d.text("source_url")) }) { Text("Abrir fuente de la consulta") }
            } else Notice("Fuente no disponible: ${source.text("error")}. El resto de los resultados se conserva.", warning = true)
        }
    }
    Text("Open-Meteo y Copernicus CAMS: datos con atribución. Acceso gratuito para uso no comercial; revisar condiciones antes de publicar comercialmente.", color = Muted, style = MaterialTheme.typography.bodySmall)
}
