package cl.habita.mobile

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.habita.mobile.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Prueba manual con Internet; las regresiones del núcleo usan respuestas controladas. */
@RunWith(AndroidJUnit4::class)
class OnlineSourcesDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun publicServicesRespondOnAndroidAndKeepHistoricalIndex() {
        val vm = ViewModelProvider(compose.activity)[HabitaViewModel::class.java]
        compose.waitUntil(60_000) { vm.state.value.busy == null }
        compose.runOnIdle { vm.edit(Query()); vm.analyzeEnvironment() }
        compose.waitUntil(60_000) { vm.state.value.busy == null && vm.state.value.environment != null }
        val historicalScore = vm.state.value.index.score
        compose.runOnIdle { vm.refreshPublicSources() }
        compose.waitUntil(90_000) { vm.state.value.busy == null }
        val results = vm.state.value.environment!!.objects("online_sources")
        val file = File(compose.activity.getExternalFilesDir(null), "qa/online-sources.json")
        file.parentFile!!.mkdirs(); file.writeText(vm.jsonReport())
        assertEquals(4, results.size)
        results.forEach { assertEquals(it.text("label") + ": " + it.text("error"), "ok", it.text("status")) }
        assertEquals(historicalScore, vm.state.value.index.score)
        assertTrue(results.all { it.obj("data").text("source_url").startsWith("https://") })
        compose.onNodeWithText("Clima exterior").performScrollTo()
        compose.onRoot().performTouchInput { swipeUp() }
        val image = File(compose.activity.getExternalFilesDir(null), "qa/09-fuentes-en-linea.png")
        image.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
