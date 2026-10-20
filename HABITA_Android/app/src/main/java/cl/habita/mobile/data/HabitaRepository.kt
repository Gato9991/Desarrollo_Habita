package cl.habita.mobile.data

import android.content.Context
import android.annotation.SuppressLint
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import cl.habita.core.*
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@SuppressLint("ApplySharedPref") // commit se ejecuta en IO y debe confirmar el puntero antes de borrar el feed anterior.
class HabitaRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("habita", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()
    private var analyzer: GtfsAnalyzer? = null
    var sourceLabel: String = "Feed incluido · Red Metropolitana"
        private set
    private val storedReport = File(context.filesDir, "last-mobility.json")

    fun load(): FeedMetadata {
        val imported = preferences.getString("feed", null)?.let { File(context.filesDir, it) }
        if (imported?.isFile == true) {
            analyzer = GtfsAnalyzer(imported)
            sourceLabel = preferences.getString("feedLabel", "Feed importado")!!
        } else {
            analyzer = GtfsAnalyzer(bundled())
            sourceLabel = "Feed incluido · Red Metropolitana"
        }
        return analyzer!!.metadata
    }

    private fun bundled(): File {
        val destination = File(context.filesDir, "bundled-feed.zip")
        if (!destination.isFile || destination.length() != 12420603L || sha256(destination) != FEED_SHA256) {
            val staging = File(context.filesDir, "bundled-feed.tmp")
            try {
                context.assets.open("gtfs/feed.zip").use { input -> staging.outputStream().use { input.copyTo(it) } }
                require(sha256(staging) == FEED_SHA256) { "El feed incluido está dañado. Reinstala la aplicación." }
                if (destination.exists()) check(destination.delete())
                check(staging.renameTo(destination)) { "No se pudo preparar el feed." }
            } finally { staging.delete() }
        }
        return destination
    }

    fun analyze(query: Query, progress: (String) -> Unit): JsonObject {
        val (lat, lon, date) = query.validated()
        return requireNotNull(analyzer) { "El feed todavía se está preparando." }.analyze(lat, lon, date, query.name, progress)
    }

    fun importZip(uri: Uri, progress: (String) -> Unit): FeedMetadata = importSource("ZIP importado", progress) { out ->
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { destination -> copyLimited(input, destination, progress) }
        } ?: error("No se pudo abrir el archivo seleccionado.")
    }.also { sourceLabel = displayName(uri); preferences.edit().putString("feedLabel", sourceLabel).apply() }

    fun importFolder(uri: Uri, progress: (String) -> Unit): FeedMetadata = importSource("Carpeta GTFS importada", progress) { out ->
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        var files = 0
        var total = 0L
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    if (!name.endsWith(".txt") || name.contains('/') || name.contains('\\')) continue
                    require(++files <= 100) { "La carpeta contiene demasiadas tablas." }
                    progress("Importando $name…")
                    val child = DocumentsContract.buildDocumentUriUsingTree(uri, cursor.getString(0))
                    zip.putNextEntry(ZipEntry(name))
                    context.contentResolver.openInputStream(child)?.use { input ->
                        total += copyLimited(input, zip, progress, MAX_IMPORT - total)
                    } ?: error("No se pudo leer $name.")
                    zip.closeEntry()
                }
            } ?: error("No se pudo acceder a la carpeta.")
        }
        require(files > 0) { "Selecciona la carpeta que contiene las tablas .txt del feed GTFS." }
    }

    private fun importSource(label: String, progress: (String) -> Unit, copy: (File) -> Unit): FeedMetadata {
        val staged = File(context.filesDir, "import-${UUID.randomUUID()}.zip")
        try {
            copy(staged)
            progress("Validando la nueva fuente GTFS…")
            val replacement = GtfsAnalyzer(staged)
            val old = preferences.getString("feed", null)
            check(preferences.edit().putString("feed", staged.name).putString("feedLabel", label).commit()) { "No se pudo guardar la selección de fuente." }
            analyzer = replacement
            sourceLabel = label
            if (old != null && old != staged.name) File(context.filesDir, old).delete()
            storedReport.delete()
            return replacement.metadata
        } catch (error: Throwable) { staged.delete(); throw error }
    }

    fun restoreBundled(): FeedMetadata {
        val replacement = GtfsAnalyzer(bundled())
        val old = preferences.getString("feed", null)
        check(preferences.edit().remove("feed").remove("feedLabel").commit()) { "No se pudo restaurar la fuente." }
        analyzer = replacement
        sourceLabel = "Feed incluido · Red Metropolitana"
        old?.let { File(context.filesDir, it).delete() }
        storedReport.delete()
        return replacement.metadata
    }

    fun saveReport(report: JsonObject) {
        val atomic = android.util.AtomicFile(storedReport)
        val output = atomic.startWrite()
        try { output.write(jsonText(report).toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (e: Throwable) { atomic.failWrite(output); throw e }
    }
    fun lastReport(): JsonObject? = runCatching {
        if (!storedReport.exists() || storedReport.length() > 5_000_000) null
        else gson.fromJson<JsonObject>(storedReport.readText(), object : TypeToken<JsonObject>() {}.type)
    }.getOrNull()
    fun jsonText(value: Any): String = gson.toJson(value)
    fun rememberPoint(id: String) { preferences.edit().putString("point", id).apply() }
    fun lastPoint(): String = preferences.getString("point", "SB-01")!!

    private fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()?.take(120) ?: "ZIP GTFS importado"

    private fun copyLimited(input: InputStream, output: java.io.OutputStream, progress: (String) -> Unit, limit: Long = MAX_IMPORT): Long {
        val buffer = ByteArray(65536)
        var count = 0L
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            count += size
            require(count <= limit) { "La importación supera 300 MB." }
            output.write(buffer, 0, size)
            if (count % (1024 * 1024) < 65536) progress("Importando fuente · ${count / (1024 * 1024)} MB…")
        }
        return count
    }
    companion object {
        const val MAX_IMPORT = 300L * 1024 * 1024
        private const val FEED_SHA256 = "8c31a7f68676f9a1cf2b3d86a40c8bea7adbd1f7478ac57d57eb826f6de21e77"
        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { stream ->
                val buffer = ByteArray(65536)
                while (true) { val n = stream.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
