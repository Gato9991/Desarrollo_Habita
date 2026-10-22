package cl.habita.mobile

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.habita.core.TerritoryAnalyzer
import cl.habita.mobile.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class EnvironmentJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun vm() = ViewModelProvider(compose.activity)[HabitaViewModel::class.java]
    private fun ready() { compose.waitUntil(60_000) { vm().state.value.busy == null } }
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File(compose.activity.getExternalFilesDir(null), "qa/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun realEnvironmentHasFourIndicatorsPersistsAndExportsWithoutNetwork() {
        ready()
        compose.runOnIdle { vm().edit(Query("Palacio de La Moneda", "-33.4422475", "-70.653898")) }
        compose.onNodeWithText("Evaluar una propiedad").performScrollTo().performClick()
        compose.onNodeWithText("Analizar ubicación").performScrollTo().performClick()
        compose.waitUntil(60_000) { vm().state.value.page == Page.INDEX && vm().state.value.busy == null }
        compose.onNodeWithText("de 10").assertExists()
        capture("07-entorno-indice")
        compose.runOnIdle {
            val state = vm().state.value
            assertEquals(Evaluation.TERRITORY, state.evaluation)
            assertEquals(TerritoryAnalyzer.VERSION, state.index.version)
            assertEquals(4, state.index.variables.size)
            assertTrue(state.index.coverage >= .75)
            assertTrue(state.index.variables.all { it.score != null })
            val stored = EnvironmentRepository(compose.activity).last()
            assertEquals("Palacio de La Moneda", stored!!.obj("location").text("name"))
            assertTrue(vm().jsonReport().contains("SINCA"))
            assertTrue(vm().summary().contains("MINSAL"))
            assertTrue(ReportPdf.create(vm().summary()).size > 1000)
        }
        compose.onNodeWithText("Salud · MINSAL / DEIS").performScrollTo().assertExists()
        compose.onRoot().performTouchInput { swipeUp() }
        capture("08-entorno-salud")
        compose.activityRule.scenario.recreate()
        compose.runOnIdle { assertNotNull(vm().state.value.environment) }
        compose.onNodeWithText("Actualizar fuentes en línea").performScrollTo().assertIsEnabled()
        compose.runOnIdle {
            val state = vm().state.value
            vm().edit(state.query.copy(lat = "-33.0"))
            assertTrue(vm().state.value.stale)
        }
        compose.onNodeWithText("Actualizar fuentes en línea").assertIsNotEnabled()
    }
}
