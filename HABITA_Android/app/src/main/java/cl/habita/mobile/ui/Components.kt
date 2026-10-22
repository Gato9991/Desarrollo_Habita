package cl.habita.mobile.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.habita.core.*
import cl.habita.mobile.AppState
import cl.habita.mobile.HabitaViewModel.Companion.format
import kotlin.math.roundToInt

@Composable fun PageBody(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable fun Eyebrow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(20.dp, 3.dp).background(Teal, RoundedCornerShape(2.dp)))
        Text(text, color = Blue, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp)
    }
}

@Composable fun CardBlock(modifier: Modifier = Modifier, dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        color = if (dark) Navy else Color.White, contentColor = if (dark) Color.White else Ink,
        border = if (dark) null else BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable fun PrimaryAction(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowForward) {
    Button(onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Navy),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 15.dp)) {
        Text(text, Modifier.weight(1f)); Spacer(Modifier.width(10.dp)); Icon(icon, null, tint = if (enabled) Teal else Muted)
    }
}

@Composable fun Notice(text: String, warning: Boolean = false, error: Boolean = false, onDismiss: (() -> Unit)? = null) {
    Surface(color = if (warning || error) Color(0xFFFFF4E4) else Pale, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.Info, null,
                Modifier.size(18.dp), tint = if (warning || error) Amber else Blue)
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = if (warning || error) Amber else Blue)
            if (onDismiss != null) IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Close, "Descartar aviso", tint = Muted) }
        }
    }
}

@Composable fun IndexCard(state: AppState) {
    val result = state.index
    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Navy, Color(0xFF0B4B6C))), RoundedCornerShape(22.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.size(112.dp).background(Color.White, CircleShape), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (result.score == null) "—" else format(result.display(result.score)), color = Blue, fontSize = 35.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("index-score"))
                Text("de ${result.displayMaximum}", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("ÍNDICE HABITA", color = Color(0xFF9EDFE6), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                Text(result.status, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("Lectura provisional del modelo", color = Color(0xFFD4E8EE), style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Disponibilidad de datos ponderada", color = Color(0xFFD4E8EE), style = MaterialTheme.typography.bodySmall)
            Text("${if (result.displayMaximum == 10) format(result.coverage * 100) else (result.coverage * 100).roundToInt().toString()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(progress = { result.coverage.toFloat() }, Modifier.fillMaxWidth().height(6.dp), color = Teal, trackColor = Color(0xFF355B72))
    }
}

@Composable fun VariableCard(variable: Variable, modifier: Modifier = Modifier, startExpanded: Boolean = false, result: IndexResult? = null) {
    var expanded by rememberSaveable(variable.id, startExpanded) { mutableStateOf(startExpanded) }
    Surface(onClick = { expanded = !expanded }, modifier = modifier.testTag("variable-${variable.id}")
        .semantics { stateDescription = if (expanded) "Datos expandidos" else "Datos contraídos" },
        shape = RoundedCornerShape(18.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(when (variable.id) { "calidad_aire" -> Icons.Rounded.Air; "confort_termico" -> Icons.Rounded.Thermostat;
                "paraderos" -> Icons.Rounded.DirectionsBus; "salud" -> Icons.Rounded.LocalHospital; else -> Icons.Rounded.Terrain },
                null, Modifier.size(25.dp), tint = Blue)
            Text(variable.name, style = MaterialTheme.typography.titleMedium)
            Text(variable.scope, color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(format(if (result != null) result.display(variable.score) else variable.score), color = Navy, fontSize = if (variable.score == null) 19.sp else 30.sp, fontWeight = FontWeight.Bold)
            LinearProgressIndicator(progress = { (variable.score?.div(100) ?: 0.0).toFloat() }, modifier = Modifier.fillMaxWidth().height(5.dp), color = Teal, trackColor = Pale)
            Text("Cobertura ${(variable.coverage * 100).roundToInt()}%", color = Muted, style = MaterialTheme.typography.bodySmall)
            Text(if (expanded) "Ocultar datos −" else "Ver datos +", color = Blue, style = MaterialTheme.typography.labelSmall)
            if (expanded) {
                HorizontalDivider(color = Line)
                variable.components.forEach { c ->
                    Text(c.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text(c.value?.let { "${format(it)} ${c.unit}" } ?: "Sin dato", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("Peso interno ${(c.weight * 100).roundToInt()}% · Puntaje ${format(if (result != null) result.display(c.score) else c.score)}", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text("Peso base ${((result?.baseWeight ?: .2) * 100).roundToInt()}% · Peso efectivo ${format(variable.effectiveWeight * 100)}% · Participación ${format(variable.finalWeight * 100)}%",
                    color = Blue, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable fun Metric(label: String, value: String, note: String, modifier: Modifier = Modifier) {
    CardBlock(modifier) {
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
        Text(value, color = Navy, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(note, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable fun PropertyIllustration() {
    Box(Modifier.fillMaxWidth().height(176.dp).background(Pale, RoundedCornerShape(22.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val sx = size.width / 327f; val sy = size.height / 176f
            drawRoundRect(Color(0xFFD4EDF4), size = Size(size.width, 82f * sy), cornerRadius = CornerRadius(22 * sx))
            drawRoundRect(Color.White, topLeft = Offset(108 * sx, 42 * sy), size = Size(112 * sx, 104 * sy), cornerRadius = CornerRadius(16 * sx))
            for (row in 0..1) for (column in 0..2) drawRoundRect(Color(0xFFB4D8E5), topLeft = Offset((126 + column * 29) * sx, (60 + row * 30) * sy), size = Size(18 * sx, 18 * sy), cornerRadius = CornerRadius(5 * sx))
        }
        Text("Índice ${format(DemoPoints.all.first().result.score)} · Demo", color = Color.White, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).background(Blue, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 9.dp))
    }
}
