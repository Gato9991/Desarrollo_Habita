package cl.habita.mobile

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Recorre la app real: navegación Figma, datos incompletos, GTFS del dispositivo y PDF nativo. */
@RunWith(AndroidJUnit4::class)
class HabitaJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun ready() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("Cancelar").fetchSemanticsNodes().isEmpty() }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val destination = File(compose.activity.getExternalFilesDir(null), "qa/$name.png")
        destination.parentFile!!.mkdirs()
        destination.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun demoJourneyPreservesPointAcrossRotationAndExportsPdf() {
        ready()
        capture("01-inicio")
        compose.onNodeWithText("Evaluar una propiedad").performScrollTo().performClick()
        capture("02-evaluar")
        compose.onNodeWithTag("point-PA-01").performScrollTo().performClick()
        compose.onNodeWithText("Ver resultado del punto").performScrollTo().performClick()
        compose.onNodeWithTag("index-score").assertTextEquals("69,7")
        capture("03-indice")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("index-score").assertTextEquals("69,7")
        compose.onNodeWithText("Ver detalle del entorno").performScrollTo().performClick()
        capture("04-detalle")
        compose.onNodeWithText("Generar reporte resumido").performScrollTo().performClick()
        capture("05-reporte")
        compose.onNodeWithText("Guardar reporte en PDF").assertExists()
        compose.runOnIdle {
            val vm = ViewModelProvider(compose.activity)[HabitaViewModel::class.java]
            assertTrue(vm.jsonReport().contains("Datos de prueba simulados"))
            val pdf = ReportPdf.create(vm.summary())
            assertTrue(String(pdf.take(5).toByteArray()).startsWith("%PDF-"))
            assertTrue(pdf.size > 1000)
        }
    }

    @Test fun gtfsRunsOnDeviceAndExposesRoutesCoverageAndPartialIndex() {
        ready()
        compose.onNodeWithTag("nav-evaluate").performClick()
        compose.onNodeWithText("Dirección / GTFS").performClick()
        compose.onNodeWithTag("service-date").performScrollTo().performTextReplacement("2026-09-23")
        compose.onNodeWithText("Analizar ubicación").performScrollTo().performClick()
        compose.waitUntil(120_000) { compose.onAllNodesWithText("Tus conexiones más cercanas").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("94,2 m").onFirst().assertExists()
        capture("06-movilidad")
        compose.onNodeWithText("Recorridos", useUnmergedTree = false).performScrollTo().performClick()
        compose.onNodeWithText("Filtrar recorrido o destino").assertExists()
        compose.onNodeWithText("Cobertura").performClick()
        compose.onNodeWithText("COBERTURA / RADIO 500 m").assertExists()
        compose.onNodeWithText("Ficha técnica").performClick()
        compose.onNodeWithText("Ficha de la consulta").assertExists()
        compose.onNodeWithTag("nav-index").performClick()
        compose.onNodeWithText("20%").assertExists()
        compose.runOnIdle {
            val vm = ViewModelProvider(compose.activity)[HabitaViewModel::class.java]
            assertEquals(Evaluation.GTFS, vm.state.value.evaluation)
            assertEquals(.2, vm.state.value.index.coverage, 1e-9)
            assertNull(vm.state.value.index.variables.first().score)
        }
    }

    @Test fun invalidCoordinatesReturnAReadableError() {
        ready()
        compose.onNodeWithTag("nav-evaluate").performClick()
        compose.onNodeWithText("Dirección / GTFS").performClick()
        compose.onNodeWithTag("query-lat").performScrollTo().performTextReplacement("NaN")
        compose.onNodeWithText("Analizar ubicación").performScrollTo().performClick()
        compose.onNodeWithText("Ingresa una latitud entre -90 y 90.").assertExists()
    }
}
