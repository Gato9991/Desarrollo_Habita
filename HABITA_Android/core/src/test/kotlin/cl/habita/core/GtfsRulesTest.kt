package cl.habita.core

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class GtfsRulesTest {
    @Suppress("UNCHECKED_CAST") private fun JsonObject.child(key: String) = get(key) as JsonObject
    private val day = LocalDate.of(2026, 9, 23)
    private fun feed(frequency: Boolean = true, remove: Boolean = false, pickupA: String = "0", badHeadway: Boolean = false): File {
        val tables = linkedMapOf(
            "agency.txt" to "agency_id,agency_name,agency_url,agency_timezone\nA,Prueba,https://example.invalid,America/Santiago\n",
            "feed_info.txt" to "feed_start_date,feed_end_date,feed_version\n20260101,20261231,TEST\n",
            "routes.txt" to "route_id,route_short_name,route_long_name\nR,R,Recorrido prueba\n",
            "trips.txt" to "route_id,service_id,trip_id,direction_id,trip_headsign\nR,L,T,0,Destino\n",
            "stops.txt" to "stop_id,stop_name,stop_lat,stop_lon,location_type\nA,\"Parada, origen\",-33.0,-70.0,0\nB,Parada B,-33.001,-70.0,0\n",
            "stop_times.txt" to "trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type\nT,06:59:00,06:59:00,A,1,$pickupA\nT,07:01:00,07:01:00,B,2,0\n",
            "calendar_dates.txt" to "service_id,date,exception_type\nL,20260923,1\n" + if (remove) "L,20260923,2\n" else "",
        )
        if (frequency) tables["frequencies.txt"] = "trip_id,start_time,end_time,headway_secs,exact_times\nT,06:59:00,07:02:00,${if (badHeadway) 0 else 60},0\n"
        return File.createTempFile("habita-rules-", ".zip").apply {
            deleteOnExit()
            ZipOutputStream(outputStream()).use { zip -> tables.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry("nested/$name")); zip.write(content.toByteArray()); zip.closeEntry()
            } }
        }
    }
    @Test fun offsetsExclusiveEndsAndUniqueInstances() {
        val file = feed()
        try {
            val result = GtfsAnalyzer(file).analyze(-33.001, -70.0, day)
            val stop = result.child("nearest_active_stop")
            assertEquals("B", stop["stop_id"])
            assertEquals("07:01:00", stop.child("service_window")["first_departure"])
            assertEquals("07:03:00", stop.child("service_window")["last_departure"])
            val peak = result.child("area_analysis").child("radius_500m").child("morning_peak")
            assertEquals(5, peak["stop_departure_events"])
            assertEquals(3, peak["unique_trip_instances"])
        } finally { file.delete() }
    }
    @Test fun frequencyOptionalCsvQuotesAndPickupRestriction() {
        val file = feed(frequency = false, pickupA = "1")
        try {
            val result = GtfsAnalyzer(file).analyze(-33.0, -70.0, day)
            assertEquals("Parada, origen", result.child("nearest_physical_stop")["stop_name"])
            assertEquals("B", result.child("nearest_active_stop")["stop_id"])
            assertEquals(false, result.child("nearest_active_stop")["same_as_nearest_physical_stop"])
        } finally { file.delete() }
    }
    @Test fun calendarExceptionsCanRemoveAllServices() {
        val file = feed(remove = true)
        try {
            val result = GtfsAnalyzer(file).analyze(-33.0, -70.0, day)
            assertEquals(0, result.child("nearest_active_stop")["route_count"])
            assertEquals(0, result.child("area_analysis").child("radius_500m")["active_routes_count"])
        } finally { file.delete() }
    }
    @Test fun invalidFrequenciesAndMissingTablesAreRejected() {
        val file = feed(badHeadway = true)
        try { assertThrows(IllegalArgumentException::class.java) { GtfsAnalyzer(file) } } finally { file.delete() }
        val empty = File.createTempFile("habita-empty-", ".zip")
        try {
            ZipOutputStream(empty.outputStream()).use { it.putNextEntry(ZipEntry("nothing.txt")); it.write("x".toByteArray()); it.closeEntry() }
            assertThrows(IllegalArgumentException::class.java) { GtfsAnalyzer(empty) }
        } finally { empty.delete() }
    }
}
