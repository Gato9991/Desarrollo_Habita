package cl.habita.core

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TerritoryAnalyzerTest {
    private val gson = Gson()
    private val type = object : TypeToken<JsonObject>() {}.type
    private fun catalog(): JsonObject = gson.fromJson(File(System.getProperty("habita.catalogPath")).readText(), type)

    @Test fun matchesAllEightPythonReferenceLocationsAndEveryComponent() {
        val analyzer = TerritoryAnalyzer(catalog())
        val references: List<JsonObject> = gson.fromJson(javaClass.getResource("/territory_reference.json")!!.readText(), object : TypeToken<List<JsonObject>>() {}.type)
        assertEquals(8, references.size)
        references.forEach { reference ->
            val actual = analyzer.analyze(reference.num("lat")!!, reference.num("lon")!!, reference.str("name"))
            val index = TerritoryAnalyzer.index(actual)
            val expectedIndex = reference.map("index")
            assertEquals(reference.str("name"), expectedIndex.num("puntaje_100")!!, index.score!!, 1e-8)
            assertEquals(expectedIndex.num("cobertura")!!, index.coverage, 1e-10)
            assertEquals(expectedIndex.num("puntaje_10")!!, index.display(index.score)!!, 1e-9)
            reference.map("parameters").forEach { (key, _) ->
                val expected = reference.map("parameters").map(key); val got = actual.map("parameters").map(key)
                if (expected.num("puntaje_100") == null) assertNull(got["score"])
                else assertEquals(expected.num("puntaje_100")!!, got.num("score")!!, 1e-8)
                assertEquals(expected.num("cobertura")!!, got.num("coverage")!!, 1e-10)
                if (key in setOf("aire", "bienestar")) {
                    assertEquals(expected.map("estacion").str("codigo"), got.map("station").str("codigo"))
                    assertEquals(expected.map("estacion").num("distancia_m")!!, got.num("distance_m")!!, 1e-7)
                } else {
                    assertEquals(expected.map("cercano").str("codigo"), got.map("nearest").str("codigo"))
                    assertEquals(expected.num("cantidad"), got.num("count"))
                    assertEquals(expected.num("distancia_m")!!, got.num("distance_m")!!, 1e-7)
                }
                expected.map("componentes").forEach { (component, score) ->
                    if (score == null) assertNull(got.map("components")[component])
                    else assertEquals((score as Number).toDouble(), got.map("components").num(component)!!, 1e-8)
                }
            }
        }
    }

    @Test fun outsideStationCoverageDoesNotInventAirOrThermalAndSuppressesGlobal() {
        val report = TerritoryAnalyzer(catalog()).analyze(0.0, 0.0, "Fuera de cobertura")
        val index = TerritoryAnalyzer.index(report)
        assertNull(index.score)
        assertEquals(.5, index.coverage, 1e-12)
        assertEquals("Cobertura insuficiente", index.status)
        assertNull(report.map("parameters").map("aire")["station"])
        assertEquals(0.0, report.map("parameters").map("salud").num("count")!!, 0.0)
        assertEquals(1.0, report.map("parameters").map("salud").num("coverage")!!, 0.0)
    }

    @Test fun minimumCoverageAndMissingComponentWeightsArePreserved() {
        val data = catalog().toMutableMap()
        data["air"] = emptyList<JsonObject>()
        val report = TerritoryAnalyzer(data).analyze(-33.4422475, -70.653898, "La Moneda")
        val index = TerritoryAnalyzer.index(report)
        assertEquals(.75, index.coverage, 1e-12)
        assertNotNull(index.score)
        assertEquals(10, index.displayMaximum)
        assertEquals(1.0, index.variables.sumOf { it.finalWeight }, 1e-12)
        data["thermal"] = emptyList<JsonObject>()
        assertNull(TerritoryAnalyzer.index(TerritoryAnalyzer(data).analyze(-33.44, -70.65, "Sin series")).score)
    }

    @Test fun invalidCoordinatesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { TerritoryAnalyzer(catalog()).analyze(Double.NaN, -70.0, "Error") }
        assertThrows(IllegalArgumentException::class.java) { TerritoryAnalyzer(catalog()).analyze(-91.0, -70.0, "Error") }
    }
}
