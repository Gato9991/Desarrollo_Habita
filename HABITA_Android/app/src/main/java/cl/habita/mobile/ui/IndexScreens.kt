package cl.habita.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.habita.core.*
import cl.habita.mobile.*
import cl.habita.mobile.HabitaViewModel.Companion.format
import cl.habita.mobile.data.*

@Composable fun IndexScreen(state: AppState, vm: HabitaViewModel) = PageBody {
    Eyebrow("02 / COMPRENDE EL ÍNDICE")
    Text(state.title, style = MaterialTheme.typography.headlineMedium)
    Text(state.subtitle, color = Muted)
    if (state.evaluation != Evaluation.DEMO && state.stale) Notice("Estos resultados corresponden a la consulta anterior. Vuelve a analizar los cambios de ubicación o fecha.", warning = true)
    SourceNotice(state)
    IndexCard(state)
    if (!state.hasResult) {
        Text("Primero consulta una ubicación para calcular sus indicadores.", color = Muted)
        PrimaryAction("Definir ubicación", { vm.navigate(Page.EVALUATE) })
    } else {
        Text("Variables del entorno", style = MaterialTheme.typography.titleLarge)
        Text(if (state.evaluation == Evaluation.TERRITORY) "Cuatro variables · 25% de peso base cada una · escala 0 a 10. Modelo de prueba pendiente de aprobación."
            else "Modelo anterior: cinco variables · 20% de peso base cada una · escala 0 a 100.", color = Muted, style = MaterialTheme.typography.bodySmall)
        state.index.variables.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { variable -> VariableCard(variable, Modifier.weight(1f), result = state.index) }
            }
        }
        PrimaryAction("Ver detalle del entorno", { vm.navigate(Page.DETAIL) })
        Methodology(state)
        if (state.evaluation == Evaluation.TERRITORY) EnvironmentSources(state, vm)
    }
}

@Composable fun DetailScreen(state: AppState, vm: HabitaViewModel) = PageBody {
    Eyebrow("03 / DETALLE DEL ENTORNO")
    Text("Este punto, explicado.", style = MaterialTheme.typography.headlineLarge)
    Surface(color = Pale, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Rounded.LocationOn, null, tint = Blue, modifier = Modifier.size(32.dp))
            Column { Text(state.title, style = MaterialTheme.typography.titleMedium); Text(state.subtitle, color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
    SourceNotice(state)
    CardBlock {
        Text("Lo que dicen los datos", style = MaterialTheme.typography.titleMedium)
        Text(if (state.evaluation == Evaluation.DEMO) demoSummary(state.point) else if (state.evaluation == Evaluation.TERRITORY)
            "El índice del entorno combina aire, confort exterior, paraderos de bus y salud. Sus períodos históricos y las consultas en línea se pueden revisar en la pantalla Índice."
            else realSummary(state), color = Muted)
    }
    val ranked = state.index.variables.filter { it.score != null }.sortedByDescending { it.score }
    if (ranked.isNotEmpty()) {
        Text("Comparación dentro del modelo", style = MaterialTheme.typography.titleLarge)
        CardBlock {
            Text("Componente mejor puntuado", color = Blue, style = MaterialTheme.typography.labelSmall)
            Text("${ranked.first().name} · ${format(state.index.display(ranked.first().score))} / ${state.index.displayMaximum}", fontWeight = FontWeight.SemiBold)
            if (ranked.size > 1) {
                HorizontalDivider(color = Line)
                Text("Componente con menor puntaje", color = Muted, style = MaterialTheme.typography.labelSmall)
                Text("${ranked.last().name} · ${format(state.index.display(ranked.last().score))} / ${state.index.displayMaximum}", fontWeight = FontWeight.SemiBold)
            }
            Text("Esta comparación depende de las reglas provisionales y de los datos disponibles.", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    Text("Aspectos a revisar", style = MaterialTheme.typography.titleLarge)
    Notice(if (state.evaluation == Evaluation.DEMO) "Los valores de esta ubicación son simulados. Un puntaje alto no demuestra que un lugar sea seguro ni reemplaza una visita o revisión de sus fuentes."
        else if (state.evaluation == Evaluation.TERRITORY) "Las estaciones de referencia pueden estar lejos del punto. La cobertura indica componentes disponibles, no certeza. El global se muestra solo con al menos 75% de cobertura."
        else "Este modo parcial usa únicamente accesibilidad a paraderos (20% de cobertura) del modelo anterior.", warning = true)
    state.index.variables.forEach { VariableCard(it, Modifier.fillMaxWidth(), startExpanded = true, result = state.index) }
    CardBlock(dark = true) {
        Text("Medición dentro de la vivienda", style = MaterialTheme.typography.titleMedium)
        Text("PRÓXIMA ETAPA", color = Color(0xFF9AE2E5), style = MaterialTheme.typography.labelSmall)
        Text("Sensor temporal durante 48–72 horas para registrar ruido, temperatura, humedad y luminosidad. Esta versión aún no se conecta a un sensor.", color = Color(0xFFD4E8EE))
    }
    PrimaryAction("Generar reporte resumido", { vm.navigate(Page.REPORT) }, icon = Icons.Rounded.Description)
    Methodology(state)
}

@Composable fun SourceNotice(state: AppState) {
    if (state.evaluation == Evaluation.DEMO) Notice("Datos de prueba simulados. ${state.index.status}. No representan direcciones reales ni una evaluación validada.", warning = true)
    else if (state.evaluation == Evaluation.TERRITORY) Notice(if (state.environment == null) "Evalúa una ubicación con las fuentes históricas incluidas del equipo."
        else "SINCA, DMC, MINSAL y GTFS. Índice histórico de prueba; las consultas actuales de APIs se muestran por separado.")
    else Notice(if (state.report == null) "Consulta una ubicación real con el feed GTFS para obtener resultados."
        else "Fuente GTFS estática. Solo transporte tiene datos para este punto; aire, confort, salud y amenazas permanecen sin datos.")
}

@Composable fun Methodology(state: AppState) {
    if (state.evaluation == Evaluation.TERRITORY) {
        CardBlock {
            Text("Cómo calculamos este índice", fontWeight = FontWeight.SemiBold)
            Text("Modelo ${state.index.version} de Pruebas, pendiente de aprobación. Cuatro indicadores con 25% de base; escala 0–10; mínimo 75% de disponibilidad ponderada para mostrar el global.", color = Muted)
            Text("Aire: media anual de puntajes diarios, mínimo 256 de 365 días por contaminante. MP2,5 70% y O₃ 30%. Temperatura 60% y humedad 40%; se puntúan observaciones y se agregan por hora, día y mes (18 horas/día, 70% de días/mes, al menos dos meses). Estación admisible más cercana hasta 30 km, elegida por separado para aire y confort.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("Paraderos: distancia 60% y cantidad en 500 m 40%. Salud: distancia 70% y cantidad en 2 km 30%. Vecino más cercano en todo el catálogo admisible; distancias en línea recta. Los pesos se reducen cuando faltan componentes. No se rellenan ausencias con cero ni se mezclan los contaminantes de estaciones distintas.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("Las medias de las series son descriptivas: el índice usa sus puntajes agregados. Las APIs actuales no reemplazan las series históricas ni añaden cobertura al índice. Amenazas sin normalización validada quedan fuera del puntaje.", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(HabitaIndex.NOTICE, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedCard(onClick = { expanded = !expanded }, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (expanded) "Cómo calculamos el Índice HABITA −" else "Cómo calculamos el Índice HABITA +", fontWeight = FontWeight.SemiBold)
            if (expanded) {
                Text("Versión ${HabitaIndex.VERSION}", color = Blue)
                Text("Cada variable tiene 20% de peso base. Su peso efectivo se reduce si faltan componentes. El resultado se reajusta solo con los datos disponibles; cero y ausencia son situaciones distintas.", color = Muted)
                Text("Calidad del aire: MP2,5 (70%) y O₃ (30%). Confort: temperatura (60%) y humedad (40%). Paraderos: distancia (60%) y cantidad en 500 m (40%). Salud: distancia (70%) y cantidad en 2 km (30%). Amenazas: cuatro exposiciones con 25% cada una.", color = Muted)
                Text("Puntajes limitados a 0–100. MP2,5: 100 × (1 − valor / 100). O₃: 100 × (1 − valor / 200). Temperatura: 100 × (1 − |valor − 22| / 18). Humedad: 100 × (1 − |valor − 50| / 50). Distancia paradero: 100 × (1 − m / 1000). Cantidad paraderos: mínimo de 100 y cantidad × 10. Distancia salud: 100 × (1 − m / 5000). Cantidad salud: mínimo de 100 y cantidad × 20. Amenazas: 100 − exposición.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Cobertura actual: ${format(state.index.coverage * 100)}%. La cobertura indica disponibilidad ponderada, no certeza ni validez.", color = Blue)
                Text(HabitaIndex.NOTICE, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun demoSummary(point: DemoPoint): String = buildString {
    append("En ${point.id}, ${point.commune}, el paradero más cercano está a ${format(point.input.distanciaParadero)} m y el establecimiento de salud más cercano a ${format(point.input.distanciaSalud)} m. ")
    append(point.input.paraderos500m?.let { "Hay ${it.toInt()} paraderos en 500 m. " } ?: "No hay dato de cantidad de paraderos en 500 m. ")
    append(point.input.establecimientos2km?.let { "Hay ${it.toInt()} establecimientos de salud en 2 km. " } ?: "Falta el conteo de establecimientos de salud en 2 km. ")
    append("Aire y confort comparten los valores asignados a la comuna en esta demostración.")
}
private fun realSummary(state: AppState): String {
    val report = state.report ?: return "Todavía no hay una consulta GTFS para esta ubicación."
    val physical = report.obj("nearest_physical_stop")
    val active = report.obj("nearest_active_stop")
    return "El paradero físico más cercano es ${physical.text("stop_name")}, a ${format(physical.number("geodesic_distance_m"))} m en línea recta. " +
        if (active.text("stop_id").isNotEmpty()) "El paradero activo más cercano es ${active.text("stop_name")}. La consulta corresponde al ${report.obj("metadata").text("analysis_date")}. Revisa movilidad para ver recorridos, horarios y cobertura."
        else "No hay un paradero con viajes activos dentro de 1.000 m para la fecha consultada."
}
