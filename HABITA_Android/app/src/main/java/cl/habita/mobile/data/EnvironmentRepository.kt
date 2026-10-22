package cl.habita.mobile.data

import android.content.Context
import android.util.AtomicFile
import cl.habita.core.*
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant

class EnvironmentRepository(private val context: Context) {
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()
    private val type = object : TypeToken<JsonObject>() {}.type
    private val stored = File(context.filesDir, "last-environment.json")
    private val analyzer by lazy {
        val bytes = context.assets.open("reference/catalog.json").use { it.readBytes() }
        val expected = context.assets.open("reference/catalog.sha256").bufferedReader().use { it.readText().trim() }
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(actual == expected) { "El catálogo del entorno está dañado. Reinstala la aplicación." }
        TerritoryAnalyzer(gson.fromJson<JsonObject>(bytes.toString(Charsets.UTF_8), type))
    }
    val publicSources = PublicSources(::readJson)

    fun analyze(query: Query): JsonObject {
        val (lat, lon) = query.coordinates()
        return analyzer.analyze(lat, lon, query.name) + ("generated_at" to Instant.now().toString())
    }
    fun save(report: JsonObject) {
        val file = AtomicFile(stored); val out = file.startWrite()
        try { out.write(gson.toJson(report).toByteArray(Charsets.UTF_8)); file.finishWrite(out) }
        catch (error: Throwable) { file.failWrite(out); throw error }
    }
    fun last(): JsonObject? = runCatching {
        val file = AtomicFile(stored)
        file.openRead().use { input ->
            require(input.available() <= 5_000_000)
            val value = gson.fromJson<JsonObject>(input.bufferedReader(), type)
            value.takeIf { it.text("model") == TerritoryAnalyzer.VERSION }
        }
    }.getOrNull()

    private fun readJson(url: String): JsonObject {
        require(URL(url).protocol == "https")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000; connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "HABITA-Android/0.2 academic prototype")
        try {
            check(connection.responseCode == 200) { "El proveedor respondió HTTP ${connection.responseCode}. Intenta nuevamente más tarde." }
            val buffer = java.io.ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val chunk = ByteArray(16384)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Consulta cancelada")
                    val n = input.read(chunk); if (n < 0) break
                    require(buffer.size() + n <= 4_000_000) { "Respuesta del proveedor demasiado grande." }
                    buffer.write(chunk, 0, n)
                }
            }
            val data = gson.fromJson<JsonObject>(buffer.toString("UTF-8"), type)
            require(data["error"] == null || data["error"] == false) { "El proveedor rechazó la consulta." }
            return data
        } finally { connection.disconnect() }
    }
}
