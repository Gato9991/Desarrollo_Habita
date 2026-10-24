package com.habita.app.model

data class HabitaInput(
    val mp25: Double? = null,
    val ozono: Double? = null,
    val temperatura: Double? = null,
    val humedad: Double? = null,
    val distanciaParaderoM: Double? = null,
    val paraderos500m: Int? = null,
    val distanciaSaludM: Double? = null,
    val establecimientos2km: Int? = null,
    val remociones: Double? = null,
    val fallas: Double? = null,
    val volcanicas: Double? = null,
    val inundaciones: Double? = null
)

data class VariableComponentResult(
    val nombre: String,
    val valor: Double?,
    val unidad: String,
    val pesoInterno: Double,
    val puntaje: Double?
)

data class VariableResult(
    val nombre: String,
    val tituloDisplay: String,
    val puntaje: Double?,
    val cobertura: Double,
    val estado: String,
    val pesoBase: Double = 0.20,
    val pesoEfectivo: Double = 0.0,
    val participacionFinal: Double = 0.0,
    val aporteAlIndice: Double = 0.0,
    val componentes: List<VariableComponentResult>
)

data class SubScoresSummary(
    val ruido: Int,
    val ambiente: Int,
    val seguridad: Int,
    val movilidad: Int,
    val salud: Int
)

data class HabitaResult(
    val versionFormula: String = "0.1.0-provisional",
    val indiceHabita: Double?,
    val cobertura: Double,
    val coberturaPorcentaje: Double,
    val estado: String,
    val advertencia: String,
    val variables: Map<String, VariableResult>,
    val subScoresSummary: SubScoresSummary,
    val clasificacion: String,
    val descripcionClasificacion: String
)

data class FortalezaItem(
    val titulo: String,
    val detalle: String
)

data class AspectoRevisarItem(
    val titulo: String,
    val detalle: String
)

data class PropertyLocation(
    val id: String,
    val direccion: String,
    val comuna: String,
    val ciudad: String,
    val inputData: HabitaInput,
    val fortalezas: List<FortalezaItem>,
    val aspectosARevisar: List<AspectoRevisarItem>,
    val medicionViviendaDisponible: Boolean = false
)
