package cl.habita.mobile.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.habita.core.JsonObject
import cl.habita.mobile.*
import cl.habita.mobile.HabitaViewModel.Companion.format
import cl.habita.mobile.data.*
import kotlin.math.cos
import kotlin.math.min

@Composable fun MobilityScreen(state: AppState, vm: HabitaViewModel, importZip: () -> Unit, importFolder: () -> Unit, openMap: () -> Unit) = PageBody {
    Eyebrow("MOVILIDAD / TU CONECTIVIDAD")
    Text("Tu entorno,\nmejor conectado.", style = MaterialTheme.typography.headlineLarge)
    Text("Paraderos, recorridos y horarios programados cerca de una ubicación.", color = Muted)
    val report = state.report
    if (report == null) {
        CardBlock {
            Icon(Icons.Rounded.DirectionsBus, null, modifier = Modifier.size(44.dp), tint = Blue)
            Text("Todo comienza con un lugar.", style = MaterialTheme.typography.titleLarge)
            Text("Consulta San Bernardo, Estadio Nacional o tus propias coordenadas. El análisis se ejecuta en este dispositivo.", color = Muted)
            PrimaryAction("Definir ubicación", { vm.mode(Evaluation.GTFS); vm.navigate(Page.EVALUATE) })
        }
        FeedControls(state, vm, importZip, importFolder)
    } else {
        Text(report.obj("location").text("name").ifBlank { "Ubicación consultada" }, style = MaterialTheme.typography.titleLarge)
        Text("${report.obj("metadata").text("analysis_date")} · Feed ${report.obj("metadata").text("feed_version")}", color = Muted, style = MaterialTheme.typography.bodySmall)
        if (state.stale) Notice("Editaste la ubicación o la fecha. Estos resultados corresponden a la consulta anterior; vuelve a analizar para actualizarlos.", warning = true)
        val physical = report.obj("nearest_physical_stop")
        val active = report.obj("nearest_active_stop")
        val area800 = report.obj("area_analysis").obj("radius_800m")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Paradero físico más cercano", "${format(physical.number("geodesic_distance_m"))} m", "Distancia en línea recta", Modifier.weight(1f))
            Metric("Caminata teórica", "${format(physical.number("theoretical_walk_time_min"))} min", "Hacia ese paradero · 1,4 m/s", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Recorridos activos", area800.number("active_routes_count")?.toInt()?.toString() ?: "—", "Dentro de 800 m", Modifier.weight(1f))
            Metric("Puntos de parada", area800.number("stop_points")?.toInt()?.toString() ?: "—", "Dentro de 800 m", Modifier.weight(1f))
        }
        var tab by rememberSaveable { mutableIntStateOf(0) }
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = Background, contentColor = Blue) {
            listOf("Resumen", "Recorridos", "Cobertura", "Ficha técnica").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label, fontSize = 12.sp) })
            }
        }
        when (tab) {
            0 -> {
                CardBlock {
                    Text("Tus conexiones más cercanas", style = MaterialTheme.typography.titleMedium)
                    Text("Proximidad física y disponibilidad de servicio son cosas distintas.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    StopRow("PARADERO FÍSICO MÁS CERCANO", physical)
                    HorizontalDivider(color = Line)
                    if (active.text("stop_id").isNotEmpty()) {
                        StopRow("PARADERO ACTIVO MÁS CERCANO", active)
                        Text(if (active["same_as_nearest_physical_stop"] == true) "El paradero más cercano tiene servicio activo" else "El paradero activo es distinto del más cercano", color = Blue, style = MaterialTheme.typography.bodySmall)
                    } else Text(active.text("note"), color = Muted)
                }
                val service = active.obj("service_window")
                if (service.isNotEmpty()) CardBlock {
                    Text("Ventana de servicio", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("PRIMERA SALIDA", color = Muted, style = MaterialTheme.typography.labelSmall); Text(service.text("first_departure"), style = MaterialTheme.typography.titleLarge) }
                        Column { Text("ÚLTIMA SALIDA", color = Muted, style = MaterialTheme.typography.labelSmall); Text(service.text("last_departure"), style = MaterialTheme.typography.titleLarge) }
                    }
                    service["note"]?.let { Text(it.toString(), color = Muted, style = MaterialTheme.typography.bodySmall) }
                    listOf("morning_peak" to "07:00–09:00", "afternoon_peak" to "17:00–19:00").forEach { (key, label) ->
                        DataLine("Salidas programadas · $label", active.obj("peak_departures").obj(key).number("total_departures")?.toInt()?.toString() ?: "0")
                    }
                    Text("Conteos en el paradero activo más cercano. Pueden incluir estimaciones del feed.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                StopSchematic(report)
                OutlinedButton(onClick = openMap, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Map, null); Spacer(Modifier.width(8.dp)); Text("Ver ubicación en mapas") }
                PrimaryAction("Ver índice parcial del entorno", { vm.mode(Evaluation.GTFS); vm.navigate(Page.INDEX) })
            }
            1 -> Routes(active)
            2 -> listOf("radius_500m" to "500 m", "radius_800m" to "800 m").forEach { (key, title) -> Coverage(report.obj("area_analysis").obj(key), title) }
            3 -> {
                CardBlock {
                    Text("Ficha de la consulta", style = MaterialTheme.typography.titleMedium)
                    val metadata = report.obj("metadata")
                    listOf("feed_version" to "Versión", "analysis_date" to "Fecha de servicio", "feed_start_date" to "Inicio de vigencia",
                        "feed_end_date" to "Fin de vigencia", "timezone" to "Zona horaria", "active_service_ids" to "Servicios activos").forEach { (key, label) ->
                        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
                        SelectionContainer { Text(metadata.text(key).ifBlank { "No declarada" }) }
                    }
                    Text("Motor Kotlin · Reglas GTFS v1.2.1", color = Blue, style = MaterialTheme.typography.bodySmall)
                }
                CardBlock {
                    Text("Cómo leer estos datos", style = MaterialTheme.typography.titleMedium)
                    (report["limitations"] as? List<*>)?.forEach { Text("• $it", color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
                var raw by rememberSaveable { mutableStateOf(false) }
                OutlinedButton(onClick = { raw = !raw }, modifier = Modifier.fillMaxWidth()) { Text(if (raw) "Ocultar JSON completo" else "Ver JSON completo") }
                if (raw) SelectionContainer {
                    Text(vm.jsonReport(), fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Pale, softWrap = false,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).background(Navy, RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState()).padding(16.dp))
                }
            }
        }
        OutlinedButton(onClick = { vm.mode(Evaluation.GTFS); vm.navigate(Page.EVALUATE) }, modifier = Modifier.fillMaxWidth()) { Text("Consultar otra ubicación o fecha") }
        TextButton(onClick = { vm.mode(Evaluation.GTFS); vm.navigate(Page.REPORT) }) { Text("Guardar o compartir informe →") }
        FeedControls(state, vm, importZip, importFolder)
    }
    Notice("Datos programados, sin llegadas en tiempo real. Distancias en línea recta y caminata teórica. Revisa las limitaciones en la ficha técnica.")
}

@Composable private fun StopRow(label: String, stop: JsonObject) {
    Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
    Text(stop.text("stop_name"), fontWeight = FontWeight.SemiBold)
    Text("${format(stop.number("geodesic_distance_m"))} m", color = Blue, style = MaterialTheme.typography.titleLarge)
}

@Composable private fun DataLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(10.dp)); Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun Routes(active: JsonObject) {
    var filter by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(filter, { filter = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Filtrar recorrido o destino") }, singleLine = true)
    val destinations = active.objects("destinations").filter { item -> item.values.any { it.toString().contains(filter, ignoreCase = true) } }
    if (destinations.isEmpty()) Notice("No hay recorridos que coincidan con la consulta y el filtro.")
    destinations.forEach { route ->
        CardBlock {
            Text(route.text("route_short_name").ifBlank { route.text("route_id") }, color = Blue, style = MaterialTheme.typography.headlineMedium)
            Text(route.text("trip_headsign").ifBlank { route.text("route_long_name") }, style = MaterialTheme.typography.titleMedium)
            Text("Sentido ${route.text("direction_id").ifBlank { "no declarado" }} · Servicio ${route.text("service_id")}", color = Muted, style = MaterialTheme.typography.bodySmall)
            val headway = active.objects("headway_by_route_direction").firstOrNull { it["route_id"] == route["route_id"] && it["direction_id"] == route["direction_id"] }?.obj("headway").orEmpty()
            listOf("morning_peak" to "Punta mañana · 07:00–09:00", "afternoon_peak" to "Punta tarde · 17:00–19:00").forEach { (key, title) ->
                HorizontalDivider(color = Line)
                Text(title, color = Blue, style = MaterialTheme.typography.labelSmall)
                val peak = headway.obj(key)
                if (peak.isEmpty()) Text("Sin servicio en esta ventana", color = Muted, style = MaterialTheme.typography.bodySmall)
                else {
                    DataLine("Salidas generadas", peak.number("scheduled_departures")?.toInt()?.toString() ?: "0")
                    peak.number("weighted_block_headway_min")?.let { DataLine("Intervalo de bloques ponderado", "${format(it)} min") }
                    peak.number("effective_interval_from_generated_departures_min")?.let { DataLine("Intervalo efectivo generado", "${format(it)} min") }
                    if (peak["concurrent_patterns_detected"] == true) Notice("Hay patrones simultáneos. El intervalo ponderado no representa su frecuencia combinada.", warning = true)
                }
            }
        }
    }
}

@Composable private fun Coverage(area: JsonObject, title: String) {
    CardBlock {
        Eyebrow("COBERTURA / RADIO $title")
        Text("${area.number("stop_points")?.toInt() ?: 0} puntos de parada", style = MaterialTheme.typography.titleLarge)
        Text("${area.number("active_routes_count")?.toInt() ?: 0} recorridos activos", color = Blue)
        listOf("morning_peak" to "Punta mañana · 07:00–09:00", "afternoon_peak" to "Punta tarde · 17:00–19:00").forEach { (key, label) ->
            val peak = area.obj(key)
            HorizontalDivider(color = Line)
            Text(label, fontWeight = FontWeight.SemiBold)
            DataLine("Eventos de salida por paradero", peak.number("stop_departure_events")?.toInt()?.toString() ?: "0")
            DataLine("Instancias únicas de viaje", peak.number("unique_trip_instances")?.toInt()?.toString() ?: "0")
            var expanded by rememberSaveable(title, key) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar desglose −" else "Ver desglose por recorrido +") }
            if (expanded) peak.obj("by_route").keys.sorted().forEach { route ->
                val values = peak.obj("by_route").obj(route)
                DataLine(route, "${values.number("stop_departure_events")?.toInt()} eventos / ${values.number("unique_trip_instances")?.toInt()} viajes")
            }
        }
        Text("Un viaje puede pasar por varias paradas del radio. Eventos y viajes únicos no equivalen a cantidad de buses físicos.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun StopSchematic(report: JsonObject) {
    val location = report.obj("location")
    val lat = location.number("lat") ?: return
    val lon = location.number("lon") ?: return
    CardBlock {
        Text("Proximidad a tu ubicación", style = MaterialTheme.typography.titleMedium)
        Canvas(Modifier.fillMaxWidth().height(220.dp).testTag("stop-schematic").semantics { contentDescription = "Esquema de puntos de parada dentro de 800 metros, con radios de 500 y 800 metros." }) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = min(size.width, size.height) / 2 - 8.dp.toPx()
            drawCircle(Pale, radius, center)
            drawCircle(Line, radius, center, style = Stroke(1.dp.toPx()))
            drawCircle(Color(0xFF87C9D3), radius * 500 / 800, center, style = Stroke(1.dp.toPx()))
            report.objects("map_stops").forEach { stop ->
                val x = Math.toRadians((stop.number("lon") ?: lon) - lon) * 6371000 * cos(Math.toRadians(lat))
                val y = -Math.toRadians((stop.number("lat") ?: lat) - lat) * 6371000
                drawCircle(if (stop["active"] == true) Blue else Muted, 3.dp.toPx(), center + Offset((x / 800 * radius).toFloat(), (y / 800 * radius).toFloat()))
            }
            drawCircle(Color.White, 9.dp.toPx(), center)
            drawCircle(Teal, 6.dp.toPx(), center)
        }
        Text("Turquesa: tu ubicación · Azul: parada con viajes activos · Gris: sin viajes activos en la fecha consultada.", color = Muted, style = MaterialTheme.typography.bodySmall)
        Text("Radios de 500 y 800 m. Esquema de proximidad, sin calles ni rutas peatonales.", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
