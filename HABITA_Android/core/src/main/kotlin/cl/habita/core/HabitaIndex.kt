package cl.habita.core

import kotlin.math.abs

typealias JsonObject = Map<String, Any?>
fun json(vararg fields: Pair<String, Any?>): JsonObject = linkedMapOf(*fields)

data class IndexInput(
    val mp25: Double? = null, val ozono: Double? = null,
    val temperatura: Double? = null, val humedad: Double? = null,
    val distanciaParadero: Double? = null, val paraderos500m: Double? = null,
    val distanciaSalud: Double? = null, val establecimientos2km: Double? = null,
    val remociones: Double? = null, val fallas: Double? = null,
    val volcanicas: Double? = null, val inundaciones: Double? = null,
)

data class Component(val name: String, val value: Double?, val unit: String, val weight: Double, val score: Double?)
data class Variable(
    val id: String, val name: String, val scope: String, val components: List<Component>,
    val score: Double?, val coverage: Double, val effectiveWeight: Double = 0.0,
    val finalWeight: Double = 0.0,
)
data class IndexResult(val score: Double?, val coverage: Double, val variables: List<Variable>,
    val version: String = HabitaIndex.VERSION, val displayMaximum: Int = 100,
    val baseWeight: Double = .2, val minimumCoverage: Double = 0.0) {
    fun display(value: Double?): Double? = value?.times(displayMaximum / 100.0)
    val status: String get() = when {
        coverage == 0.0 -> "Sin datos"
        coverage + 1e-12 < minimumCoverage -> "Cobertura insuficiente"
        abs(coverage - 1.0) < 1e-9 -> "Datos completos"
        else -> "Índice provisional"
    }
    fun toJson(): JsonObject = json(
        "version_formula" to version, "indice_habita" to display(score), "escala_maxima" to displayMaximum,
        "cobertura_porcentaje" to coverage * 100, "estado" to status,
        "advertencia" to HabitaIndex.NOTICE,
        "variables" to variables.map { v -> json(
            "id" to v.id, "nombre" to v.name, "alcance" to v.scope,
            "puntaje" to display(v.score), "cobertura" to v.coverage,
            "peso_base" to baseWeight, "peso_efectivo" to v.effectiveWeight,
            "participacion_final" to v.finalWeight,
            "componentes" to v.components.map { c -> json(
                "nombre" to c.name, "valor" to c.value, "unidad" to c.unit,
                "peso_interno" to c.weight, "puntaje" to display(c.score),
            ) },
        ) },
    )
}

/** Port de formula_indice_habita, sin inferir ruido ni seguridad desde otras variables. */
object HabitaIndex {
    const val VERSION = "0.1.0-provisional"
    const val NOTICE = "Fórmulas, límites y ponderaciones provisionales, pendientes de validación. El resultado no certifica habitabilidad ni seguridad."
    val names = listOf("Calidad del aire", "Confort térmico", "Accesibilidad a paraderos", "Accesibilidad a salud", "Amenazas naturales")

    fun calculate(input: IndexInput): IndexResult {
        val all = listOf(input.mp25, input.ozono, input.temperatura, input.humedad,
            input.distanciaParadero, input.paraderos500m, input.distanciaSalud,
            input.establecimientos2km, input.remociones, input.fallas, input.volcanicas, input.inundaciones)
        require(all.filterNotNull().all { it.isFinite() }) { "Las mediciones deben ser números finitos." }
        listOf(input.mp25, input.ozono, input.distanciaParadero, input.distanciaSalud,
            input.paraderos500m, input.establecimientos2km).filterNotNull().forEach {
            require(it >= 0) { "Las concentraciones, distancias y cantidades deben ser positivas o cero." }
        }
        listOf(input.humedad, input.remociones, input.fallas, input.volcanicas, input.inundaciones)
            .filterNotNull().forEach { require(it in 0.0..100.0) { "Humedad y exposiciones deben estar entre 0 y 100." } }
        listOf(input.paraderos500m, input.establecimientos2km).filterNotNull().forEach {
            require(it % 1.0 == 0.0) { "Las cantidades deben ser enteras." }
        }
        fun component(name: String, value: Double?, unit: String, weight: Double, fn: (Double) -> Double) =
            Component(name, value, unit, weight, value?.let { fn(it).coerceIn(0.0, 100.0) })
        fun group(id: String, name: String, scope: String, vararg items: Component): Variable {
            val coverage = items.filter { it.score != null }.sumOf { it.weight }
            val score = if (coverage > 0) items.sumOf { (it.score ?: 0.0) * it.weight } / coverage else null
            return Variable(id, name, scope, items.toList(), score, coverage)
        }
        val groups = listOf(
            group("calidad_aire", names[0], "Dato comunal",
                component("MP2,5", input.mp25, "µg/m³", .7) { 100 * (1 - it / 100) },
                component("Ozono O₃", input.ozono, "µg/m³", .3) { 100 * (1 - it / 200) }),
            group("confort_termico", names[1], "Dato comunal",
                component("Temperatura", input.temperatura, "°C", .6) { 100 * (1 - abs(it - 22) / 18) },
                component("Humedad relativa", input.humedad, "%", .4) { 100 * (1 - abs(it - 50) / 50) }),
            group("paraderos", names[2], "Dato del punto",
                component("Paradero más cercano", input.distanciaParadero, "m", .6) { 100 * (1 - it / 1000) },
                component("Paraderos en 500 m", input.paraderos500m, "paraderos", .4) { it * 10 }),
            group("salud", names[3], "Dato del punto",
                component("Establecimiento más cercano", input.distanciaSalud, "m", .7) { 100 * (1 - it / 5000) },
                component("Establecimientos en 2 km", input.establecimientos2km, "centros", .3) { it * 20 }),
            group("amenazas_naturales", names[4], "Dato del punto",
                component("Remociones en masa", input.remociones, "/100 exposición", .25) { 100 - it },
                component("Amenazas asociadas a fallas", input.fallas, "/100 exposición", .25) { 100 - it },
                component("Amenazas volcánicas", input.volcanicas, "/100 exposición", .25) { 100 - it },
                component("Inundaciones", input.inundaciones, "/100 exposición", .25) { 100 - it }),
        )
        val coverage = groups.sumOf { .2 * it.coverage }
        val updated = groups.map { it.copy(effectiveWeight = .2 * it.coverage,
            finalWeight = if (coverage > 0) .2 * it.coverage / coverage else 0.0) }
        return IndexResult(if (coverage > 0) updated.sumOf { (it.score ?: 0.0) * it.finalWeight } else null, coverage, updated)
    }
}

data class DemoPoint(val id: String, val commune: String, val input: IndexInput) {
    val result: IndexResult get() = HabitaIndex.calculate(input)
}

/** Exactamente los seis registros de window.HABITA_DATA de la demo web. Sin coordenadas ficticias. */
object DemoPoints {
    val all = listOf(
        DemoPoint("SB-01", "San Bernardo", IndexInput(35.0,60.0,25.0,45.0,150.0,8.0,800.0,3.0,20.0,30.0,0.0,40.0)),
        DemoPoint("SB-02", "San Bernardo", IndexInput(35.0,60.0,25.0,45.0,700.0,2.0,3500.0,1.0,60.0,30.0,0.0,70.0)),
        DemoPoint("PA-01", "Puente Alto", IndexInput(28.0,80.0,26.0,null,250.0,6.0,1200.0,2.0,40.0,45.0,5.0,25.0)),
        DemoPoint("PA-02", "Puente Alto", IndexInput(28.0,80.0,26.0,null,900.0,null,4000.0,1.0,70.0,45.0,5.0,null)),
        DemoPoint("PE-01", "Peñalolén", IndexInput(22.0,null,24.0,55.0,100.0,9.0,600.0,4.0,55.0,40.0,0.0,20.0)),
        DemoPoint("PE-02", "Peñalolén", IndexInput(22.0,null,24.0,55.0,500.0,4.0,2000.0,null,null,null,null,null)),
    )
}
