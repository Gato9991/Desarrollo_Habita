package cl.habita.core

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate

class GtfsReferenceTest {
    private val analyzer by lazy { GtfsAnalyzer(File(System.getProperty("habita.gtfsPath"))) }
    @Test fun sanBernardoMatchesPython121() = reference("san_bernardo.json", -33.5922368, -70.704706)
    @Test fun estadioNacionalMatchesPython121() = reference("estadio_nacional.json", -33.4644994, -70.6112756)
    private fun reference(name: String, lat: Double, lon: Double) {
        val reference = javaClass.getResourceAsStream("/$name")!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
        val result = JsonParser.parseString(GsonBuilder().serializeNulls().create().toJson(analyzer.analyze(lat, lon, LocalDate.of(2026, 9, 23)))).asJsonObject
        listOf("nearest_physical_stop", "nearest_active_stop", "area_analysis").forEach {
            assertEquals("$name / $it", reference[it], result[it])
        }
    }
    @Test fun datesCoordinatesAndNoNearbyStopsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { analyzer.activeServices(LocalDate.of(2030, 1, 1)) }
        assertThrows(IllegalArgumentException::class.java) { analyzer.analyze(Double.NaN, 0.0, LocalDate.of(2026, 9, 23)) }
        assertThrows(IllegalArgumentException::class.java) { analyzer.analyze(0.0, 0.0, LocalDate.of(2026, 9, 23)) }
    }
    @Test fun timesPastMidnightAndExclusiveRanges() {
        assertEquals(91920, GtfsAnalyzer.timeToSeconds("25:32:00"))
        assertEquals("25:32:00", GtfsAnalyzer.secondsToTime(91920))
        assertThrows(IllegalArgumentException::class.java) { GtfsAnalyzer.timeToSeconds("25:60:00") }
    }
}
