package cl.habita.mobile

import android.app.Application
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cl.habita.core.*
import cl.habita.mobile.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class Page { HOME, EVALUATE, INDEX, MOBILITY, REPORT, DETAIL }
enum class Evaluation { DEMO, GTFS }
data class Place(val name: String, val lat: Double, val lon: Double)
data class AppState(
    val page: Page = Page.HOME, val evaluation: Evaluation = Evaluation.DEMO,
    val pointId: String = "SB-01", val query: Query = Query(), val address: String = "",
    val places: List<Place> = emptyList(), val metadata: FeedMetadata? = null,
    val sourceLabel: String = "Preparando fuente…", val report: JsonObject? = null,
    val reportQuery: Query? = null, val busy: String? = null, val error: String? = null,
    val message: String? = null,
) {
    val point: DemoPoint get() = DemoPoints.all.firstOrNull { it.id == pointId } ?: DemoPoints.all.first()
    val index: IndexResult get() = if (evaluation == Evaluation.DEMO) point.result else {
        val inputs = report?.obj("habita_inputs").orEmpty()
        HabitaIndex.calculate(IndexInput(distanciaParadero = inputs.number("distancia_paradero_m"), paraderos500m = inputs.number("paraderos_500m")))
    }
    val title: String get() = if (evaluation == Evaluation.DEMO) "Punto ${point.id}" else report?.obj("location")?.text("name").orEmpty().ifBlank { "Ubicación por consultar" }
    val subtitle: String get() = if (evaluation == Evaluation.DEMO) "${point.commune} · Ubicación simulada" else report?.obj("metadata")?.text("analysis_date").orEmpty().ifBlank { "Consulta de movilidad GTFS" }
    val stale: Boolean get() = report != null && query != reportQuery
}

class HabitaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HabitaRepository(application)
    private val mutable = MutableStateFlow(AppState(pointId = repository.lastPoint()))
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var pendingExport: ByteArray? = null

    init { task("Preparando el feed incluido…") {
        val metadata = withContext(Dispatchers.IO) { repository.load() }
        val previous = withContext(Dispatchers.IO) { repository.lastReport() }
        val previousQuery = previous?.let { Query(it.obj("location").text("name"),
            it.obj("location").text("lat"), it.obj("location").text("lon"), it.obj("metadata").text("analysis_date")) }
        mutable.update { it.copy(metadata = metadata, sourceLabel = repository.sourceLabel,
            query = previousQuery ?: it.query.copy(date = metadata.suggestedDate().toString()),
            report = previous, reportQuery = previousQuery) }
    } }

    fun navigate(page: Page) { mutable.update { it.copy(page = page,
        evaluation = if (page == Page.MOBILITY) Evaluation.GTFS else it.evaluation,
        error = null, message = null) } }
    fun back() { navigate(when (state.value.page) { Page.DETAIL -> Page.INDEX; Page.INDEX, Page.MOBILITY -> Page.EVALUATE; else -> Page.HOME }) }
    fun selectPoint(id: String) { repository.rememberPoint(id); mutable.update { it.copy(pointId = id, evaluation = Evaluation.DEMO, error = null) } }
    fun mode(mode: Evaluation) { mutable.update { it.copy(evaluation = mode, error = null) } }
    fun edit(query: Query) { mutable.update { it.copy(query = query, error = null, message = null) } }
    fun editAddress(text: String) { mutable.update { it.copy(address = text, places = emptyList(), error = null) } }
    fun selectPlace(place: Place) { mutable.update { it.copy(query = it.query.copy(name = place.name, lat = place.lat.toString(), lon = place.lon.toString()), places = emptyList()) } }
    fun preset(national: Boolean) { mutable.update { it.copy(query = it.query.copy(
        name = if (national) "Estadio Nacional" else "Plaza de Armas · San Bernardo",
        lat = if (national) "-33.4644994" else "-33.5922368", lon = if (national) "-70.6112756" else "-70.704706")) } }
    fun clearNotice() { mutable.update { it.copy(error = null, message = null) } }
    fun cancel() { work?.cancel() }

    fun analyzeMobility() = task("Iniciando análisis…") {
        val query = state.value.query
        query.validated()
        val context = currentCoroutineContext()
        val result = withContext(Dispatchers.IO) { repository.analyze(query) { phase ->
            context.ensureActive(); mutable.update { it.copy(busy = phase) }
        } }
        withContext(Dispatchers.IO) { repository.saveReport(result) }
        mutable.update { it.copy(report = result, reportQuery = query, evaluation = Evaluation.GTFS, page = Page.MOBILITY) }
    }

    fun importZip(uri: Uri) = changeFeed { progress -> repository.importZip(uri, progress) }
    fun importFolder(uri: Uri) = changeFeed { progress -> repository.importFolder(uri, progress) }
    fun restoreFeed() = changeFeed { repository.restoreBundled() }
    private fun changeFeed(action: ((String) -> Unit) -> FeedMetadata) = task("Preparando nueva fuente…") {
        val context = currentCoroutineContext()
        withContext(Dispatchers.IO) {
            val metadata = action { phase -> context.ensureActive(); mutable.update { it.copy(busy = phase) } }
            // Sin suspensión entre confirmar la fuente y actualizar su estado visible.
            mutable.update { it.copy(metadata = metadata, sourceLabel = repository.sourceLabel, report = null,
                reportQuery = null, query = it.query.copy(date = metadata.suggestedDate().toString()),
                message = "Fuente GTFS actualizada. Vuelve a analizar la ubicación.") }
        }
    }

    fun searchAddress() = task("Buscando dirección…") {
        val text = state.value.address.trim()
        require(text.length >= 5) { "Escribe calle, número y comuna, o ingresa coordenadas." }
        require(Geocoder.isPresent()) { "Este dispositivo no tiene geocodificador. Puedes usar coordenadas o los lugares de ejemplo." }
        val geocoder = Geocoder(getApplication(), Locale.forLanguageTag("es-CL"))
        val addresses = withTimeout(20_000) {
            if (Build.VERSION.SDK_INT >= 33) suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(text, 5, -56.0, -76.0, -17.0, -66.0, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) { if (continuation.isActive) continuation.resume(addresses) }
                    override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("No se pudo resolver la dirección. Revisa la conexión o usa coordenadas.")) }
                })
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(text, 5, -56.0, -76.0, -17.0, -66.0).orEmpty()
            }
        }
        val places = addresses.filter { it.hasLatitude() && it.hasLongitude() }.map { Place(it.getAddressLine(0) ?: text, it.latitude, it.longitude) }
        require(places.isNotEmpty()) { "No se encontraron coincidencias. Añade la comuna o usa coordenadas." }
        mutable.update { it.copy(places = places) }
    }

    fun reportObject(snapshot: AppState = state.value): JsonObject = json(
        "app" to "HABITA Android 0.1.0", "ubicacion" to snapshot.title,
        "fuente" to if (snapshot.evaluation == Evaluation.DEMO) "Datos de prueba simulados; no representan direcciones reales" else "GTFS estático · ${snapshot.sourceLabel}",
        "indice" to snapshot.index.toJson(),
        "movilidad" to if (snapshot.evaluation == Evaluation.GTFS) snapshot.report else null,
        "nota" to "Aire, confort, salud, amenazas y medición IoT requieren fuentes validadas para ubicaciones reales. No se asignan datos simulados a direcciones reales.",
    )
    fun jsonReport(): String = repository.jsonText(reportObject())
    fun summary(snapshot: AppState = state.value): String = buildString {
        appendLine("HABITA · Conoce antes de habitar")
        appendLine(snapshot.title); appendLine(snapshot.subtitle)
        appendLine(if (snapshot.evaluation == Evaluation.DEMO) "DATOS SIMULADOS DE DEMOSTRACIÓN" else "DATOS GTFS PROGRAMADOS")
        appendLine("Índice: ${format(snapshot.index.score)} / 100 · Cobertura: ${format(snapshot.index.coverage * 100)}%")
        snapshot.index.variables.forEach { appendLine("${it.name}: ${format(it.score)} / 100 · disponibilidad ${format(it.coverage * 100)}%") }
        if (snapshot.evaluation == Evaluation.GTFS) snapshot.report?.let { report ->
            val physical = report.obj("nearest_physical_stop")
            appendLine("Fecha de servicio: ${report.obj("metadata").text("analysis_date")}")
            appendLine("Feed: ${report.obj("metadata").text("feed_version")}")
            appendLine("Paradero: ${physical.text("stop_name")} · ${format(physical.number("geodesic_distance_m"))} m en línea recta")
            appendLine("Caminata teórica: ${format(physical.number("theoretical_walk_time_min"))} min")
            listOf("radius_500m" to "500 m", "radius_800m" to "800 m").forEach { (key, title) ->
                val area = report.obj("area_analysis").obj(key)
                appendLine("Radio $title: ${area.number("stop_points")?.toInt()} puntos de parada y ${area.number("active_routes_count")?.toInt()} recorridos activos")
            }
        }
        appendLine(); appendLine(HabitaIndex.NOTICE)
        appendLine("GTFS: horarios programados, sin llegadas en tiempo real. Distancias en línea recta y caminata teórica a 1,4 m/s.")
        appendLine("Aire, confort, salud, amenazas e IoT: las fuentes de una ubicación real siguen pendientes de integración y validación.")
    }
    fun prepareExport(pdf: Boolean) { pendingExport = if (pdf) ReportPdf.create(summary()) else jsonReport().toByteArray(Charsets.UTF_8) }
    fun saveExport(uri: Uri) = task("Guardando informe…") {
        val bytes = pendingExport ?: error("El informe ya no está disponible. Vuelve a exportarlo.")
        withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: error("No se pudo guardar el archivo.") }
        pendingExport = null
        mutable.update { it.copy(message = "Informe guardado.") }
    }
    private fun task(label: String, action: suspend () -> Unit) {
        if (work?.isActive == true) return
        work = viewModelScope.launch {
            mutable.update { it.copy(busy = label, error = null, message = null) }
            try { action() }
            catch (timeout: TimeoutCancellationException) { mutable.update { it.copy(error = "La búsqueda demoró demasiado. Revisa la conexión o utiliza coordenadas.") } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { mutable.update { it.copy(error = error.message ?: "No se pudo completar la operación.") } }
            finally { mutable.update { it.copy(busy = null) } }
        }
    }
    companion object { fun format(value: Double?): String = value?.let { String.format(Locale.forLanguageTag("es-CL"), "%.1f", it) } ?: "Sin datos" }
}
