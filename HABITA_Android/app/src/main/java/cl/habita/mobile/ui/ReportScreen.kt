package cl.habita.mobile.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cl.habita.mobile.*
import cl.habita.mobile.HabitaViewModel.Companion.format

@Composable fun ReportScreen(state: AppState, vm: HabitaViewModel, onJson: () -> Unit, onPdf: () -> Unit, onShare: () -> Unit) = PageBody {
    Eyebrow("04 / TU REPORTE")
    Text("La información,\npara llevar.", style = MaterialTheme.typography.headlineLarge)
    Text("Guarda el resultado y sus fuentes para revisarlo con más contexto.", color = Muted)
    SourceNotice(state)
    if (state.evaluation != Evaluation.DEMO && state.stale) Notice("El informe conserva el último análisis. Los cambios del formulario todavía no se han calculado.", warning = true)
    CardBlock {
        Text(state.title, style = MaterialTheme.typography.titleLarge)
        Text(state.subtitle, color = Muted)
        HorizontalDivider(color = Line)
        state.index.variables.forEach { variable ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(variable.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(12.dp))
                Text("${format(state.index.display(variable.score))} / ${state.index.displayMaximum}", color = Blue, fontWeight = FontWeight.SemiBold)
            }
        }
        Text("Disponibilidad de datos: ${format(state.index.coverage * 100)}%", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
    val canExport = state.busy == null && state.hasResult
    PrimaryAction("Guardar reporte en PDF", onPdf, enabled = canExport, icon = Icons.Rounded.PictureAsPdf)
    OutlinedButton(onClick = onJson, enabled = canExport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Icon(Icons.Rounded.DataObject, null); Spacer(Modifier.width(10.dp)); Text("Exportar informe JSON")
    }
    OutlinedButton(onClick = onShare, enabled = canExport, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(10.dp)); Text("Compartir resumen")
    }
    if (!canExport && state.busy == null) Notice("Analiza una ubicación para generar su reporte.")
    Text("El PDF incluye el resumen y las limitaciones. El JSON conserva el desglose, las fuentes del entorno, las consultas en línea o el informe GTFS según el modo elegido.", color = Muted, style = MaterialTheme.typography.bodySmall)
    Text("Prototipo conceptual · Metodología provisional", color = Muted, style = MaterialTheme.typography.bodySmall)
}
