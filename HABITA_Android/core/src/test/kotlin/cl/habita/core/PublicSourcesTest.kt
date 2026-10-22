package cl.habita.core

import org.junit.Assert.*
import org.junit.Test

class PublicSourcesTest {
    @Test fun modeledAirUsesConcentrationsAndKeepsZeroWithoutConvertingOzoneAgain() {
        var url = ""
        val api = PublicSources { address -> url = address; json("current" to json("time" to "2026-10-21T12:00", "pm2_5" to 0.0, "ozone" to 20.0),
            "current_units" to json("pm2_5" to "μg/m³", "ozone" to "μg/m³")) }
        val result = api.air(-33.5, -70.7)
        assertEquals(0.0, result.num("pm25_ug_m3")!!, 0.0)
        assertEquals(20.0, result.num("ozone_ug_m3")!!, 0.0)
        assertTrue(url.contains("cams_global")); assertFalse(result.containsKey("score"))
    }

    @Test fun absentOrIncompatibleWeatherIsRejected() {
        val empty = PublicSources { json("current" to json("time" to "2026-10-21T12:00")) }
        assertThrows(IllegalArgumentException::class.java) { empty.weather(-33.5, -70.7) }
        val wrongUnits = PublicSources { json("current" to json("time" to "2026-10-21T12:00", "temperature_2m" to 30.0),
            "current_units" to json("temperature_2m" to "°F", "relative_humidity_2m" to "%")) }
        assertThrows(IllegalArgumentException::class.java) { wrongUnits.weather(-33.5, -70.7) }
    }

    @Test fun healthRejectsTruncatedCatalogAndDeduplicatesClinicalCenters() {
        val truncated = PublicSources { json("type" to "FeatureCollection", "features" to emptyList<JsonObject>(), "numberMatched" to 2) }
        assertThrows(IllegalArgumentException::class.java) { truncated.health(-33.5, -70.7) }
        val center = json("properties" to json("estado" to "Vigente en Operación Habitual", "prestador" to "Público", "tipo" to "Hospital",
            "latitud" to -33.5, "longitud" to -70.7, "cod_vig" to 100, "nombre" to "Centro"))
        val closed = json("properties" to (center.map("properties") + ("estado" to "Cerrado")))
        val api = PublicSources { json("type" to "FeatureCollection", "features" to listOf(center, center, closed), "numberMatched" to 3) }
        val result = api.health(-33.5, -70.7)
        assertEquals(1, result["count_2km"])
        assertEquals("Centro", result.map("nearest").str("nombre"))
    }

    @Test fun emptyVolcanicIntersectionIsNotASafetyScore() {
        val result = PublicSources { json("features" to emptyList<JsonObject>()) }.volcanicHazard(-33.5, -70.7)
        assertTrue(result.rows("matches").isEmpty())
        assertFalse(result.containsKey("score"))
        assertTrue(result.str("note").contains("no significa ausencia de riesgo"))
        assertThrows(IllegalArgumentException::class.java) { PublicSources { json("error" to json("code" to 503)) }.volcanicHazard(-33.5, -70.7) }
    }
}
