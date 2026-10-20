package cl.habita.mobile

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.habita.mobile.data.HabitaRepository
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Importación transaccional y recuperación de la fuente en almacenamiento Android real. */
@RunWith(AndroidJUnit4::class)
class SourceImportTest {
    private fun isolated(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val key = "import-test-${UUID.randomUUID()}"
        val directory = File(base.cacheDir, key).apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$key-$name", mode)
        }
    }
    private fun zip(context: Context, valid: Boolean): File {
        val files = mapOf(
            "feed_info.txt" to "feed_start_date,feed_end_date,feed_version\n20260101,20261231,TEST-MOBILE\n",
            "agency.txt" to "agency_id,agency_timezone\nA,America/Santiago\n",
            "routes.txt" to "route_id,route_short_name\nR,R\n",
            "trips.txt" to "trip_id,route_id,service_id\nT,R,L\n",
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon\nS,Parada,-33,-70\n",
            "calendar_dates.txt" to "service_id,date,exception_type\nL,20260923,1\n",
            "stop_times.txt" to if (valid) "trip_id,departure_time,stop_id,stop_sequence\nT,07:00:00,S,1\n" else "not_a_gtfs_header\nbroken\n",
        )
        return File(context.filesDir, "candidate.zip").apply {
            ZipOutputStream(outputStream()).use { out -> files.forEach { (name, text) ->
                out.putNextEntry(ZipEntry(name)); out.write(text.toByteArray()); out.closeEntry()
            } }
        }
    }
    @Test fun invalidSourceLeavesPreviousFeedSelected() {
        val context = isolated()
        val repo = HabitaRepository(context)
        val original = repo.load()
        val invalid = zip(context, false)
        assertThrows(IllegalArgumentException::class.java) { repo.importZip(Uri.fromFile(invalid)) {} }
        assertEquals(original.version, HabitaRepository(context).load().version)
        assertEquals("Feed incluido · Red Metropolitana", repo.sourceLabel)
    }
    @Test fun importedSourcePersistsAndCanBeRestored() {
        val context = isolated()
        val repo = HabitaRepository(context)
        val original = repo.load()
        assertEquals("TEST-MOBILE", repo.importZip(Uri.fromFile(zip(context, true))) {}.version)
        val reopened = HabitaRepository(context)
        assertEquals("TEST-MOBILE", reopened.load().version)
        assertEquals(original.version, reopened.restoreBundled().version)
        assertEquals(original.version, HabitaRepository(context).load().version)
    }
}
