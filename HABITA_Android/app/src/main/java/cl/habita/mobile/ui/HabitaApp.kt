package cl.habita.mobile.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cl.habita.mobile.*
import cl.habita.mobile.data.*

@Composable fun HabitaApp(vm: HabitaViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importZip) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::importFolder) }
    val jsonPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::saveExport) }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let(vm::saveExport) }
    val openZip = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
    val openFolder = { folderPicker.launch(null) }
    val exportName = state.title.replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(70)
    BackHandler(state.page != Page.HOME) { vm.back() }
    Scaffold(containerColor = Background, topBar = {
        Column(Modifier.fillMaxWidth().background(Navy).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.page != Page.HOME) IconButton(onClick = vm::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", tint = Color.White) }
                else Box(Modifier.size(44.dp).background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("H", color = Blue, fontWeight = FontWeight.Bold, fontSize = 25.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("HABITA", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                    Text("Conoce antes de habitar", color = Color(0xFFB8DBE3), fontSize = 10.sp)
                }
                Text("PROTOTIPO", color = Color(0xFF9AE2E5), style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.background(Color(0xFF163F58), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 8.dp))
            }
        }
    }, bottomBar = {
        NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
            val entries = listOf(Page.HOME to "Inicio", Page.EVALUATE to "Evaluar", Page.INDEX to "Índice", Page.MOBILITY to "Movilidad", Page.REPORT to "Reporte")
            entries.forEach { (page, label) ->
                NavigationBarItem(modifier = Modifier.testTag("nav-${page.name.lowercase()}"), selected = state.page == page || state.page == Page.DETAIL && page == Page.REPORT,
                    onClick = { vm.navigate(page) }, label = { Text(label, fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = Pale, selectedIconColor = Blue, selectedTextColor = Blue),
                    icon = { Icon(when (page) { Page.HOME -> Icons.Rounded.Home; Page.EVALUATE -> Icons.Rounded.Search;
                        Page.INDEX -> Icons.Rounded.Insights; Page.MOBILITY -> Icons.Rounded.DirectionsBus; else -> Icons.Rounded.Description }, label) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (state.busy != null) Surface(color = Pale) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Blue, strokeWidth = 2.dp)
                    Text(state.busy!!, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Blue)
                    TextButton(onClick = vm::cancel) { Text("Cancelar") }
                }
            }
            state.error?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Notice(it, error = true, onDismiss = vm::clearNotice) } }
            state.message?.let { Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { Notice(it, onDismiss = vm::clearNotice) } }
            Box(Modifier.weight(1f)) {
                when (state.page) {
                    Page.HOME -> HomeScreen(state, vm)
                    Page.EVALUATE -> EvaluateScreen(state, vm, openZip, openFolder)
                    Page.INDEX -> IndexScreen(state, vm)
                    Page.DETAIL -> DetailScreen(state, vm)
                    Page.MOBILITY -> MobilityScreen(state, vm, openZip, openFolder) {
                        state.report?.obj("location")?.let { location ->
                            val lat = location.number("lat"); val lon = location.number("lon")
                            if (lat != null && lon != null) {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(location.text("name"))})"))
                                runCatching { context.startActivity(intent) }.onFailure {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lon")))
                                }
                            }
                        }
                    }
                    Page.REPORT -> ReportScreen(state, vm,
                        onJson = { vm.prepareExport(false); jsonPicker.launch("HABITA_$exportName.json") },
                        onPdf = { vm.prepareExport(true); pdfPicker.launch("HABITA_$exportName.pdf") },
                        onShare = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Informe HABITA · ${state.title}"); putExtra(Intent.EXTRA_TEXT, vm.summary())
                        }, "Compartir informe HABITA")) })
                }
            }
        }
    }
}

@Composable private fun HomeScreen(state: AppState, vm: HabitaViewModel) = PageBody {
    Eyebrow("TU ENTORNO, CON MÁS CONTEXTO")
    Text("Decide dónde vivir\ncon más información", style = MaterialTheme.typography.headlineLarge)
    Text("Evalúa una propiedad y su entorno antes de comprar o arrendar.", color = Muted, style = MaterialTheme.typography.bodyLarge)
    PropertyIllustration()
    PrimaryAction("Evaluar una propiedad", { vm.navigate(Page.EVALUATE) })
    Surface(color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Line), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.CheckCircleOutline, null, tint = Blue, modifier = Modifier.size(20.dp))
            Text("Resultado claro y comparable", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
    CardBlock {
        Eyebrow("CINCO PERSPECTIVAS")
        Text("Un lugar. Datos a la vista.", style = MaterialTheme.typography.titleLarge)
        Text("Aire, confort térmico, transporte, salud y amenazas naturales. Cada resultado muestra sus componentes y la información disponible.", color = Muted)
        TextButton(onClick = { vm.mode(Evaluation.DEMO); vm.navigate(Page.INDEX) }) { Text("Explorar los seis puntos de prueba →") }
    }
    OutlinedButton(onClick = { vm.navigate(Page.MOBILITY) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Rounded.DirectionsBus, null); Spacer(Modifier.width(10.dp)); Text("Explorar mi conectividad")
    }
    Notice("El índice sigue siendo provisional. Los seis puntos de demostración usan datos simulados; las consultas de transporte usan el feed GTFS incluido.")
}
