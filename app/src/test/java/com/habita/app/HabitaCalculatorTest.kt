package com.habita.app

import com.habita.app.calculator.HabitaCalculator
import com.habita.app.model.HabitaInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.roundToInt

class HabitaCalculatorTest {

    private val baseInput = HabitaInput(
        mp25 = 35.0,
        ozono = 60.0,
        temperatura = 25.0,
        humedad = 45.0,
        distanciaParaderoM = 150.0,
        paraderos500m = 8,
        distanciaSaludM = 800.0,
        establecimientos2km = 3,
        remociones = 20.0,
        fallas = 30.0,
        volcanicas = 0.0,
        inundaciones = 40.0
    )

    @Test
    fun testCasoBaseCompleto() {
        val result = HabitaCalculator.calcularIndiceHabita(baseInput)

        assertNotNull(result.indiceHabita)
        val scoreRounded = (result.indiceHabita!! * 10.0).roundToInt() / 10.0
        assertEquals(78.0, scoreRounded, 0.1)
        assertEquals(100.0, result.coberturaPorcentaje, 0.01)
        assertEquals("completo", result.estado)
    }

    @Test
    fun testSinDatos() {
        val emptyInput = HabitaInput()
        val result = HabitaCalculator.calcularIndiceHabita(emptyInput)

        assertNull(result.indiceHabita)
        assertEquals(0.0, result.cobertura, 0.001)
        assertEquals("sin_datos", result.estado)
    }

    @Test
    fun testCeroNoEsAusencia() {
        val zeroParaderosInput = HabitaInput(paraderos500m = 0)
        val result = HabitaCalculator.calcularIndiceHabita(zeroParaderosInput)

        assertNotNull(result.indiceHabita)
        assertEquals(0.0, result.indiceHabita!!, 0.001)
        assertEquals(0.08, result.cobertura, 0.001)
        assertEquals("provisional", result.estado)
    }

    @Test
    fun testLimites() {
        assertEquals(0.0, HabitaCalculator.puntajeMp25(1000.0)!!, 0.001)
        assertEquals(0.0, HabitaCalculator.puntajeOzono(1000.0)!!, 0.001)
        assertEquals(0.0, HabitaCalculator.puntajeTemperatura(50.0)!!, 0.001)
        assertEquals(100.0, HabitaCalculator.puntajeTemperatura(22.0)!!, 0.001)
        assertEquals(100.0, HabitaCalculator.puntajeHumedad(50.0)!!, 0.001)
        assertEquals(0.0, HabitaCalculator.puntajeDistanciaParadero(2000.0)!!, 0.001)
        assertEquals(100.0, HabitaCalculator.puntajeCantidadParaderos(100)!!, 0.001)
        assertEquals(100.0, HabitaCalculator.puntajeAmenaza(0.0)!!, 0.001)
        assertEquals(0.0, HabitaCalculator.puntajeAmenaza(100.0)!!, 0.001)
    }
}
