package cl.habita.mobile.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.habita.core.DemoPoints
import cl.habita.mobile.*
import cl.habita.mobile.HabitaViewModel.Companion.format
import java.time.LocalDate
import java.time.ZoneId

@Composable fun EvaluateScreen(state: AppState, vm: HabitaViewModel, importZip: () -> Unit, importFolder: () -> Unit) = PageBody {
    Eyebrow("01 / ELIGE TU UBICACIÓN")
    Text("¿Qué dirección\nquieres conocer?", style = MaterialTheme.typography.headlineLarge)
    Text("Evalúa una dirección con las fuentes del equipo, consulta movilidad o explora la demostración.", color = Muted)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilterChip(selected = state.evaluation == Evaluation.DEMO, onClick = { vm.mode(Evaluation.DEMO) }, label = { Text("Demostración") })
        FilterChip(selected = state.evaluation == Evaluation.TERRITORY, onClick = { vm.mode(Evaluation.TERRITORY) }, label = { Text("Índice del entorno") })
        FilterChip(selected = state.evaluation == Evaluation.GTFS, onClick = { vm.mode(Evaluation.GTFS) }, label = { Text("Dirección / GTFS") })
    }
    if (state.evaluation == Evaluation.DEMO) {
        Notice("Seis identificadores simulados. No representan direcciones reales.", warning = true)
        DemoPoints.all.forEach { point ->
            Surface(onClick = { vm.selectPoint(point.id) }, shape = RoundedCornerShape(16.dp),
                color = if (state.pointId == point.id) Pale else androidx.compose.ui.graphics.Color.White,
                border = BorderStroke(if (state.pointId == point.id) 2.dp else 1.dp, if (state.pointId == point.id) Blue else Line),
                modifier = Modifier.fillMaxWidth().testTag("point-${point.id}")) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RadioButton(selected = state.pointId == point.id, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(point.id, fontWeight = FontWeight.Bold)
                        Text(point.commune, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(format(point.result.score), color = Blue, style = MaterialTheme.typography.titleLarge)
                        Text("${(point.result.coverage * 100).toInt()}% de datos", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        PrimaryAction("Ver resultado del punto", { vm.navigate(Page.INDEX) })
    } else {
        OutlinedTextField(state.address, vm::editAddress, label = { Text("Calle, número y comuna") },
            placeholder = { Text("Ej. Plaza de Armas, San Bernardo") }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), enabled = state.busy == null)
        OutlinedButton(onClick = vm::searchAddress, enabled = state.busy == null, modifier = Modifier.fillMaxWidth()) { Text("Buscar dirección") }
        Text("La búsqueda usa el servicio geocodificador de Android y puede requerir conexión. Confirma la coincidencia antes de analizar.", color = Muted, style = MaterialTheme.typography.bodySmall)
        state.places.forEach { place ->
            Surface(onClick = { vm.selectPlace(place) }, shape = RoundedCornerShape(14.dp), color = Pale) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(place.name, fontWeight = FontWeight.SemiBold)
                    Text("${place.lat}, ${place.lon} · Usar esta ubicación", color = Blue, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.preset(false) }, label = { Text("San Bernardo") }, enabled = state.busy == null)
            AssistChip(onClick = { vm.preset(true) }, label = { Text("Estadio Nacional") }, enabled = state.busy == null)
        }
        CardBlock {
            Text("O ingresa coordenadas", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(state.query.name, { vm.edit(state.query.copy(name = it.take(160))) }, label = { Text("Nombre del lugar") }, singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = state.busy == null)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(state.query.lat, { vm.edit(state.query.copy(lat = it)) }, label = { Text("Latitud") }, singleLine = true, modifier = Modifier.weight(1f).testTag("query-lat"), enabled = state.busy == null)
                OutlinedTextField(state.query.lon, { vm.edit(state.query.copy(lon = it)) }, label = { Text("Longitud") }, singleLine = true, modifier = Modifier.weight(1f), enabled = state.busy == null)
            }
            if (state.evaluation == Evaluation.GTFS) DateField(state, vm)
            PrimaryAction("Analizar ubicación", if (state.evaluation == Evaluation.TERRITORY) vm::analyzeEnvironment else vm::analyzeMobility, enabled = state.busy == null && (state.evaluation == Evaluation.TERRITORY || state.metadata != null))
        }
        if (state.evaluation == Evaluation.TERRITORY) Notice("Evalúa aire, confort exterior, paraderos y salud con las fuentes históricas del equipo. Puedes consultar datos en línea después de analizar. No requiere internet para el índice incluido.")
        else {
            Notice("Este modo analiza solo GTFS. Para aire, confort y salud selecciona Índice del entorno.")
            FeedControls(state, vm, importZip, importFolder)
        }
    }
}

@Composable private fun DateField(state: AppState, vm: HabitaViewModel) {
    val context = LocalContext.current
    OutlinedTextField(state.query.date, { vm.edit(state.query.copy(date = it)) },
        label = { Text("Fecha de servicio · AAAA-MM-DD") }, singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("service-date"), enabled = state.busy == null,
        trailingIcon = { IconButton(onClick = {
            val selected = runCatching { LocalDate.parse(state.query.date) }.getOrDefault(state.metadata?.suggestedDate() ?: LocalDate.now())
            DatePickerDialog(context, { _, year, month, day -> vm.edit(state.query.copy(date = LocalDate.of(year, month + 1, day).toString())) },
                selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                state.metadata?.let {
                    datePicker.minDate = it.start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    datePicker.maxDate = it.end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }.show()
        }) { Icon(Icons.Rounded.CalendarMonth, "Elegir fecha") } })
    Text("Vigencia: ${state.metadata?.start ?: "…"} — ${state.metadata?.end ?: "…"}", color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable fun FeedControls(state: AppState, vm: HabitaViewModel, importZip: () -> Unit, importFolder: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    CardBlock {
        Eyebrow("FUENTE DE LA CONSULTA")
        Text(state.metadata?.version ?: "Sin fuente disponible", fontWeight = FontWeight.Bold)
        Text(state.sourceLabel, color = Muted, style = MaterialTheme.typography.bodySmall)
        state.metadata?.let { Text("${it.start} — ${it.end}", color = Muted, style = MaterialTheme.typography.bodySmall) }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Cerrar opciones de fuente −" else "Cambiar fuente GTFS +") }
        if (expanded) {
            Text("Elige un ZIP o una carpeta con tablas GTFS. Los datos se procesan en tu dispositivo. Límite: 300 MB.", color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = importZip, enabled = state.busy == null, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FolderZip, null); Spacer(Modifier.width(8.dp)); Text("Importar ZIP GTFS") }
            OutlinedButton(onClick = importFolder, enabled = state.busy == null, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp)); Text("Importar carpeta GTFS") }
            TextButton(onClick = vm::restoreFeed, enabled = state.busy == null) { Text("Restaurar feed incluido") }
        }
    }
}
