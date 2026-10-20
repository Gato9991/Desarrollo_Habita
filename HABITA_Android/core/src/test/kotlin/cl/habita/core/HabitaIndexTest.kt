package cl.habita.core

import org.junit.Assert.*
import org.junit.Test

class HabitaIndexTest {
    @Test fun sixWebPointsAndMissingComponents() {
        // Resultados de formula_indice_habita.py, sin redondeo de presentación.
        val scores = listOf(77.96000000000001, 53.1, 69.67753623188406, 47.105485232067515, 83.14539007092199, 68.2156862745098)
        val coverage = listOf(1.0, 1.0, .92, .79, .94, .68)
        DemoPoints.all.forEachIndexed { i, point ->
            assertEquals(point.id, scores[i], point.result.score!!, 1e-9)
            assertEquals(point.id, coverage[i], point.result.coverage, 1e-9)
        }
    }
    @Test fun absentValuesAreNotZeroAndWeightsAreRenormalized() {
        assertNull(HabitaIndex.calculate(IndexInput()).score)
        val zero = HabitaIndex.calculate(IndexInput(paraderos500m = 0.0))
        assertEquals(0.0, zero.score!!, 0.0)
        assertEquals(.08, zero.coverage, 1e-9)
        val partial = DemoPoints.all[2].result
        assertEquals(.12 / .92, partial.variables[1].finalWeight, 1e-9)
        assertEquals(1.0, partial.variables.sumOf { it.finalWeight }, 1e-9)
    }
    @Test fun invalidMeasurementsAreRejected() {
        listOf(IndexInput(mp25 = -1.0), IndexInput(temperatura = Double.NaN),
            IndexInput(humedad = 101.0), IndexInput(paraderos500m = .5),
            IndexInput(fallas = -1.0), IndexInput(distanciaSalud = Double.POSITIVE_INFINITY)).forEach {
            assertThrows(IllegalArgumentException::class.java) { HabitaIndex.calculate(it) }
        }
        assertEquals(0.0, HabitaIndex.calculate(IndexInput(mp25 = 200.0)).score!!, 0.0)
    }
}
