package com.habita.app.data

import com.habita.app.calculator.HabitaCalculator
import com.habita.app.model.AspectoRevisarItem
import com.habita.app.model.FortalezaItem
import com.habita.app.model.HabitaInput
import com.habita.app.model.HabitaResult
import com.habita.app.model.PropertyLocation
import kotlin.math.abs

object DemoLocationsRepository {

    val providenciaDemo = PropertyLocation(
        id = "providencia_1234",
        direccion = "Av. Providencia 1234",
        comuna = "Providencia",
        ciudad = "Santiago",
        inputData = HabitaInput(
            mp25 = 28.0,
            ozono = 45.0,
            temperatura = 23.0,
            humedad = 48.0,
            distanciaParaderoM = 120.0,
            paraderos500m = 9,
            distanciaSaludM = 650.0,
            establecimientos2km = 4,
            remociones = 15.0,
            fallas = 20.0,
            volcanicas = 0.0,
            inundaciones = 25.0
        ),
        fortalezas = listOf(
            FortalezaItem("Metro cercano", "4 min caminando"),
            FortalezaItem("Servicios esenciales", "12 a menos de 1 km"),
            FortalezaItem("Conectividad", "Muy buena"),
            FortalezaItem("Centros de salud", "4 a menos de 2 km")
        ),
        aspectosARevisar = listOf(
            AspectoRevisarItem(
                "Ruido nocturno",
                "Nivel estimado sobre el promedio del sector debido a flujo vehicular"
            )
        ),
        medicionViviendaDisponible = true
    )

    val sanBernardoDemo = PropertyLocation(
        id = "san_bernardo_plaza",
        direccion = "Plaza de Armas",
        comuna = "San Bernardo",
        ciudad = "Santiago",
        inputData = HabitaInput(
            mp25 = 45.0,
            ozono = 62.0,
            temperatura = 25.0,
            humedad = 42.0,
            distanciaParaderoM = 220.0,
            paraderos500m = 6,
            distanciaSaludM = 1100.0,
            establecimientos2km = 2,
            remociones = 25.0,
            fallas = 30.0,
            volcanicas = 0.0,
            inundaciones = 35.0
        ),
        fortalezas = listOf(
            FortalezaItem("Tren Nos / Metrotren", "6 min caminando"),
            FortalezaItem("Servicios cívicos", "Plaza central y comercio a pasos"),
            FortalezaItem("Acceso a salud pública", "Hospital Parroquial a 1.1 km")
        ),
        aspectosARevisar = listOf(
            AspectoRevisarItem(
                "Calidad del aire en invierno",
                "Nivel de material particulado MP2.5 moderadamente elevado"
            )
        ),
        medicionViviendaDisponible = false
    )

    val estadiNacionalDemo = PropertyLocation(
        id = "estadio_nacional",
        direccion = "Av. Grecia 2001",
        comuna = "Ñuñoa",
        ciudad = "Santiago",
        inputData = HabitaInput(
            mp25 = 22.0,
            ozono = 38.0,
            temperatura = 22.0,
            humedad = 50.0,
            distanciaParaderoM = 90.0,
            paraderos500m = 11,
            distanciaSaludM = 500.0,
            establecimientos2km = 5,
            remociones = 10.0,
            fallas = 15.0,
            volcanicas = 0.0,
            inundaciones = 15.0
        ),
        fortalezas = listOf(
            FortalezaItem("Metro Estadio Nacional", "2 min caminando (Línea 6 y Línea 3)"),
            FortalezaItem("Áreas verdes y deportivas", "Parque Estadio Nacional a pasos"),
            FortalezaItem("Conectividad ciclovías", "Red consolidada en Av. Grecia")
        ),
        aspectosARevisar = listOf(
            AspectoRevisarItem(
                "Congestión puntual",
                "Aumento de flujo de público en días de eventos masivos"
            )
        ),
        medicionViviendaDisponible = false
    )

    val maipuDemo = PropertyLocation(
        id = "maipu_pajaritos",
        direccion = "Av. Pajaritos 2500",
        comuna = "Maipú",
        ciudad = "Santiago",
        inputData = HabitaInput(
            mp25 = 38.0,
            ozono = 55.0,
            temperatura = 24.0,
            humedad = 46.0,
            distanciaParaderoM = 180.0,
            paraderos500m = 7,
            distanciaSaludM = 950.0,
            establecimientos2km = 3,
            remociones = 20.0,
            fallas = 25.0,
            volcanicas = 0.0,
            inundaciones = 30.0
        ),
        fortalezas = listOf(
            FortalezaItem("Corredor de transporte", "Paradero de buses a 180 m"),
            FortalezaItem("Servicios locales", "Supermercados y colegios a < 800 m")
        ),
        aspectosARevisar = listOf(
            AspectoRevisarItem(
                "Tráfico en hora punta",
                "Nivel de ruido vehicular moderado por corredor Pajaritos"
            )
        ),
        medicionViviendaDisponible = false
    )

    val allDemos = listOf(providenciaDemo, sanBernardoDemo, estadiNacionalDemo, maipuDemo)

    fun searchLocation(query: String): PropertyLocation {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return providenciaDemo

        // Check exact or partial match in demo locations
        val match = allDemos.find {
            it.direccion.contains(trimmed, ignoreCase = true) ||
            it.comuna.contains(trimmed, ignoreCase = true)
        }
        if (match != null) return match

        // Generate dynamic location based on query
        val hash = abs(trimmed.hashCode())
        val mp25Val = 20.0 + (hash % 30)
        val ozonoVal = 35.0 + (hash % 40)
        val distParadero = 100.0 + (hash % 400)
        val countParaderos = 4 + (hash % 8)
        val distSalud = 400.0 + (hash % 1500)

        val dynamicInput = HabitaInput(
            mp25 = mp25Val,
            ozono = ozonoVal,
            temperatura = 22.5,
            humedad = 50.0,
            distanciaParaderoM = distParadero,
            paraderos500m = countParaderos,
            distanciaSaludM = distSalud,
            establecimientos2km = 3,
            remociones = 15.0,
            fallas = 20.0,
            volcanicas = 0.0,
            inundaciones = 25.0
        )

        val parts = trimmed.split(",")
        val street = parts.getOrNull(0)?.trim() ?: trimmed
        val comuna = parts.getOrNull(1)?.trim() ?: "Santiago"

        return PropertyLocation(
            id = "dyn_${hash}",
            direccion = street,
            comuna = comuna,
            ciudad = "Santiago",
            inputData = dynamicInput,
            fortalezas = listOf(
                FortalezaItem("Transporte público", "Paraderos a ${distParadero.toInt()} m"),
                FortalezaItem("Servicios de salud", "Centro de salud a ${distSalud.toInt()} m")
            ),
            aspectosARevisar = listOf(
                AspectoRevisarItem(
                    "Calidad del aire local",
                    "Afectado por condiciones ambientales de la comuna"
                )
            ),
            medicionViviendaDisponible = false
        )
    }

    fun calculateResultForLocation(location: PropertyLocation): HabitaResult {
        return HabitaCalculator.calcularIndiceHabita(location.inputData)
    }
}
