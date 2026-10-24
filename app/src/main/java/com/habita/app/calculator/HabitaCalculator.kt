package com.habita.app.calculator

import com.habita.app.model.HabitaInput
import com.habita.app.model.HabitaResult
import com.habita.app.model.SubScoresSummary
import com.habita.app.model.VariableComponentResult
import com.habita.app.model.VariableResult
import kotlin.math.abs

object HabitaCalculator {

    const val VERSION_FORMULA = "0.1.0-provisional"
    const val ADVERTENCIA =
        "Fórmulas, límites y ponderaciones provisionales, sujetos a cambios y " +
        "validación. El resultado no certifica habitabilidad ni seguridad."

    private val PESOS_VARIABLES = mapOf(
        "calidad_aire" to 0.20,
        "confort_termico" to 0.20,
        "paraderos" to 0.20,
        "salud" to 0.20,
        "amenazas_naturales" to 0.20
    )

    fun limitar(valor: Double): Double {
        return valor.coerceIn(0.0, 100.0)
    }

    fun puntajeMp25(valor: Double?): Double? =
        valor?.let { limitar(100.0 * (1.0 - it / 100.0)) }

    fun puntajeOzono(valor: Double?): Double? =
        valor?.let { limitar(100.0 * (1.0 - it / 200.0)) }

    fun puntajeTemperatura(valor: Double?): Double? =
        valor?.let { limitar(100.0 * (1.0 - abs(it - 22.0) / 18.0)) }

    fun puntajeHumedad(valor: Double?): Double? =
        valor?.let { limitar(100.0 * (1.0 - abs(it - 50.0) / 50.0)) }

    fun puntajeDistanciaParadero(valor: Double?): Double? =
        valor?.let { limitar(100.0 * (1.0 - it / 1000.0)) }

    fun puntajeCantidadParaderos(valor: Int?): Double? =
        valor?.let { limitar(it * 10.0) }

    fun puntajeDistanciaSalud(valor: Double?): Double? =
        valor?.let { limitar(100.0 * (1.0 - it / 5000.0)) }

    fun puntajeCantidadSalud(valor: Int?): Double? =
        valor?.let { limitar(it * 20.0) }

    fun puntajeAmenaza(valor: Double?): Double? =
        valor?.let { limitar(100.0 - it) }

    private fun crearComponente(
        nombre: String,
        valor: Double?,
        unidad: String,
        peso: Double,
        puntaje: Double?
    ): VariableComponentResult = VariableComponentResult(
        nombre = nombre,
        valor = valor,
        unidad = unidad,
        pesoInterno = peso,
        puntaje = puntaje
    )

    private fun combinar(
        nombre: String,
        tituloDisplay: String,
        componentes: List<VariableComponentResult>
    ): VariableResult {
        val cobertura = componentes.filter { it.puntaje != null }.sumOf { it.pesoInterno }
        val puntaje = if (cobertura > 0.0) {
            componentes.filter { it.puntaje != null }
                .sumOf { it.pesoInterno * it.puntaje!! } / cobertura
        } else null

        val estado = when {
            cobertura == 0.0 -> "sin_datos"
            abs(cobertura - 1.0) < 1e-5 -> "completo"
            else -> "incompleto"
        }

        return VariableResult(
            nombre = nombre,
            tituloDisplay = tituloDisplay,
            puntaje = puntaje,
            cobertura = cobertura,
            estado = estado,
            pesoBase = PESOS_VARIABLES[nombre] ?: 0.20,
            componentes = componentes
        )
    }

    fun calcularCalidadAire(mp25: Double?, ozono: Double?): VariableResult {
        return combinar(
            "calidad_aire",
            "Calidad del Aire",
            listOf(
                crearComponente("mp25", mp25, "µg/m³", 0.70, puntajeMp25(mp25)),
                crearComponente("ozono", ozono, "µg/m³", 0.30, puntajeOzono(ozono))
            )
        )
    }

    fun calcularConfortTermico(temperatura: Double?, humedad: Double?): VariableResult {
        return combinar(
            "confort_termico",
            "Confort Térmico",
            listOf(
                crearComponente("temperatura", temperatura, "°C", 0.60, puntajeTemperatura(temperatura)),
                crearComponente("humedad", humedad, "%", 0.40, puntajeHumedad(humedad))
            )
        )
    }

    fun calcularParaderos(distanciaM: Double?, cantidad500m: Int?): VariableResult {
        return combinar(
            "paraderos",
            "Accesibilidad a Paraderos",
            listOf(
                crearComponente("distancia_m", distanciaM, "m", 0.60, puntajeDistanciaParadero(distanciaM)),
                crearComponente("cantidad_500m", cantidad500m?.toDouble(), "paraderos", 0.40, puntajeCantidadParaderos(cantidad500m))
            )
        )
    }

    fun calcularSalud(distanciaM: Double?, cantidad2km: Int?): VariableResult {
        return combinar(
            "salud",
            "Accesibilidad a Salud",
            listOf(
                crearComponente("distancia_m", distanciaM, "m", 0.70, puntajeDistanciaSalud(distanciaM)),
                crearComponente("cantidad_2km", cantidad2km?.toDouble(), "establecimientos", 0.30, puntajeCantidadSalud(cantidad2km))
            )
        )
    }

    fun calcularAmenazasNaturales(
        remociones: Double?,
        fallas: Double?,
        volcanicas: Double?,
        inundaciones: Double?
    ): VariableResult {
        return combinar(
            "amenazas_naturales",
            "Amenazas Naturales",
            listOf(
                crearComponente("remociones", remociones, "/100", 0.25, puntajeAmenaza(remociones)),
                crearComponente("fallas", fallas, "/100", 0.25, puntajeAmenaza(fallas)),
                crearComponente("volcanicas", volcanicas, "/100", 0.25, puntajeAmenaza(volcanicas)),
                crearComponente("inundaciones", inundaciones, "/100", 0.25, puntajeAmenaza(inundaciones))
            )
        )
    }

    fun calcularIndiceHabita(input: HabitaInput): HabitaResult {
        val varsMap = mutableMapOf<String, VariableResult>()

        varsMap["calidad_aire"] = calcularCalidadAire(input.mp25, input.ozono)
        varsMap["confort_termico"] = calcularConfortTermico(input.temperatura, input.humedad)
        varsMap["paraderos"] = calcularParaderos(input.distanciaParaderoM, input.paraderos500m)
        varsMap["salud"] = calcularSalud(input.distanciaSaludM, input.establecimientos2km)
        varsMap["amenazas_naturales"] = calcularAmenazasNaturales(
            input.remociones, input.fallas, input.volcanicas, input.inundaciones
        )

        val coberturaGlobal = varsMap.values.sumOf { v ->
            (PESOS_VARIABLES[v.nombre] ?: 0.20) * v.cobertura
        }

        val varsProcesadas = mutableMapOf<String, VariableResult>()
        var sumaAportes = 0.0

        for ((nombre, v) in varsMap) {
            val pesoBase = PESOS_VARIABLES[nombre] ?: 0.20
            val pesoEfectivo = pesoBase * v.cobertura
            val participacionFinal = if (coberturaGlobal > 0.0) pesoEfectivo / coberturaGlobal else 0.0
            val aporte = if (v.puntaje != null) participacionFinal * v.puntaje else 0.0

            sumaAportes += aporte

            varsProcesadas[nombre] = v.copy(
                pesoBase = pesoBase,
                pesoEfectivo = pesoEfectivo,
                participacionFinal = participacionFinal,
                aporteAlIndice = aporte
            )
        }

        val indice = if (coberturaGlobal > 0.0) sumaAportes else null

        val estadoGlobal = when {
            coberturaGlobal == 0.0 -> "sin_datos"
            abs(coberturaGlobal - 1.0) < 1e-5 -> "completo"
            else -> "provisional"
        }

        // Clasificación de habitabilidad
        val scoreInt = indice?.toInt() ?: 0
        val (clasificacion, descripcionClasificacion) = when {
            scoreInt >= 80 -> "Muy buena habitabilidad" to "Entorno muy favorable para vivir"
            scoreInt >= 65 -> "Buena habitabilidad" to "Entorno adecuado con buenas condiciones"
            scoreInt >= 50 -> "Habitabilidad moderada" to "Entorno aceptable con algunos aspectos a mejorar"
            else -> "Habitabilidad baja" to "Entorno con limitaciones importantes de servicios o condiciones"
        }

        // Desglose simplificado para tarjetas de UI (Ruido, Ambiente, Seguridad, Movilidad, Salud)
        val aireScore = varsProcesadas["calidad_aire"]?.puntaje?.toInt() ?: 75
        val confortScore = varsProcesadas["confort_termico"]?.puntaje?.toInt() ?: 80
        val amenazasScore = varsProcesadas["amenazas_naturales"]?.puntaje?.toInt() ?: 78
        val movilidadScore = varsProcesadas["paraderos"]?.puntaje?.toInt() ?: 90
        val saludScore = varsProcesadas["salud"]?.puntaje?.toInt() ?: 85

        // Estimación de Ruido (basada en proximidad y aire)
        val ruidoScore = ((aireScore * 0.4) + (movilidadScore * 0.6)).toInt().coerceIn(40, 95)
        val ambienteScore = ((aireScore * 0.5) + (confortScore * 0.5)).toInt().coerceIn(40, 98)

        val subScores = SubScoresSummary(
            ruido = ruidoScore,
            ambiente = ambienteScore,
            seguridad = amenazasScore,
            movilidad = movilidadScore,
            salud = saludScore
        )

        return HabitaResult(
            versionFormula = VERSION_FORMULA,
            indiceHabita = indice,
            cobertura = coberturaGlobal,
            coberturaPorcentaje = coberturaGlobal * 100.0,
            estado = estadoGlobal,
            advertencia = ADVERTENCIA,
            variables = varsProcesadas,
            subScoresSummary = subScores,
            clasificacion = clasificacion,
            descripcionClasificacion = descripcionClasificacion
        )
    }
}
