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
enum class Evaluation { DEMO, GTFS, TERRITORY }
data class Place(val name: String, val lat: Double, val lon: Double)
data class AppState(
    val page: Page = Page.HOME, val evaluation: Evaluation = Evaluation.DEMO,
    val pointId: String = "SB-01", val query: Query = Query(), val address: String = "",
    val places: List<Place> = emptyList(), val metadata: FeedMetadata? = null,
    val sourceLabel: String = "Preparando fuente…", val report: JsonObject? = null,
    val reportQuery: Query? = null, val busy: String? = null, val error: String? = null,
    val message: String? = null,
    val environment: JsonObject? = null, val environmentQuery: Query? = null,
) {
    val point: DemoPoint get() = DemoPoints.all.firstOrNull { it.id == pointId } ?: DemoPoints.all.first()
    val index: IndexResult get() = if (evaluation == Evaluation.TERRITORY) TerritoryAnalyzer.index(environment) else if (evaluation == Evaluation.DEMO) point.result else {
        val inputs = report?.obj("habita_inputs").orEmpty()
        HabitaIndex.calculate(IndexInput(distanciaParadero = inputs.number("distancia_paradero_m"), paraderos500m = inputs.number("paraderos_500m")))
    }
    val title: String get() = when (evaluation) {
        Evaluation.DEMO -> "Punto ${point.id}"
        Evaluation.TERRITORY -> environment?.obj("location")?.text("name").orEmpty().ifBlank { "Ubicación por consultar" }
        else -> report?.obj("location")?.text("name").orEmpty().ifBlank { "Ubicación por consultar" }
    }
    val subtitle: String get() = when (evaluation) {
        Evaluation.DEMO -> "${point.commune} · Ubicación simulada"
        Evaluation.TERRITORY -> "Fuentes del equipo · modelo ${TerritoryAnalyzer.VERSION}"
        else -> report?.obj("metadata")?.text("analysis_date").orEmpty().ifBlank { "Consulta de movilidad GTFS" }
    }
    val stale: Boolean get() = if (evaluation == Evaluation.TERRITORY) environment != null &&
        (query.name != environmentQuery?.name || query.lat != environmentQuery?.lat || query.lon != environmentQuery?.lon)
        else report != null && query != reportQuery
    val hasResult: Boolean get() = when (evaluation) { Evaluation.DEMO -> true; Evaluation.GTFS -> report != null; Evaluation.TERRITORY -> environment != null }
}

class HabitaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HabitaRepository(application)
    private val environmentRepository = EnvironmentRepository(application)
    private val mutable = MutableStateFlow(AppState(pointId = repository.lastPoint()))
    val state = mutable.asStateFlow()
    private var work: Job? = null
    private var pendingExport: ByteArray? = null

    init { task("Preparando el feed incluido…") {
        val metadata = withContext(Dispatchers.IO) { repository.load() }
        val previous = withContext(Dispatchers.IO) { repository.lastReport() }
        val environment = withContext(Dispatchers.IO) { environmentRepository.last() }
        val environmentQuery = environment?.obj("location")?.let { Query(it.text("name"), it.text("lat"), it.text("lon")) }
        val previousQuery = previous?.let { Query(it.obj("location").text("name"),
            it.obj("location").text("lat"), it.obj("location").text("lon"), it.obj("metadata").text("analysis_date")) }
        mutable.update { it.copy(metadata = metadata, sourceLabel = repository.sourceLabel,
            query = (environmentQuery ?: previousQuery ?: it.query).copy(date = metadata.suggestedDate().toString()),
            report = previous, reportQuery = previousQuery, environment = environment, environmentQuery = environmentQuery) }
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

    fun analyzeEnvironment() = task("Evaluando aire, confort, salud y paraderos…") {
        val query = state.value.query
        query.coordinates()
        val report = withContext(Dispatchers.IO) { environmentRepository.analyze(query) }
        withContext(Dispatchers.IO) { environmentRepository.save(report) }
        mutable.update { it.copy(environment = report, environmentQuery = query, evaluation = Evaluation.TERRITORY, page = Page.INDEX) }
    }

    fun refreshPublicSources() = task("Consultando fuentes públicas…") {
        val snapshot = state.value
        val report = snapshot.environment ?: error("Primero analiza una ubicación.")
        require(!snapshot.stale) { "Vuelve a analizar la ubicación antes de actualizar sus fuentes." }
        val (lat, lon) = requireNotNull(snapshot.environmentQuery).coordinates()
        val sources = environmentRepository.publicSources
        val requests = listOf(
            Triple("weather", "Clima exterior", { sources.weather(lat, lon) }),
            Triple("air", "Aire modelado", { sources.air(lat, lon) }),
            Triple("health", "Establecimientos de salud", { sources.health(lat, lon) }),
            Triple("volcano", "Peligros volcánicos", { sources.volcanicHazard(lat, lon) }),
        )
        val results = coroutineScope { requests.map { (id, label, request) -> async(Dispatchers.IO) {
            ensureActive()
            val checked = java.time.Instant.now().toString()
            try {
                val data = request(); ensureActive()
                json("id" to id, "label" to label, "status" to "ok", "checked_at" to checked, "data" to data)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { json("id" to id, "label" to label, "status" to "error", "checked_at" to checked,
                "error" to (error.message ?: "No se pudo conectar con esta fuente.")) }
        } }.awaitAll() }
        val updated = report + ("online_sources" to results)
        withContext(Dispatchers.IO) { environmentRepository.save(updated) }
        mutable.update { it.copy(environment = updated, message = "Fuentes consultadas: ${results.count { r -> r.text("status") == "ok" }} de ${results.size}. El índice histórico se conserva.") }
    }

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
        "app" to "HABITA Android 0.2.0", "ubicacion" to snapshot.title,
        "fuente" to when (snapshot.evaluation) { Evaluation.DEMO -> "Datos de prueba simulados; no representan direcciones reales"; Evaluation.TERRITORY -> "SINCA, DMC, MINSAL y GTFS · fuentes históricas del equipo"; else -> "GTFS estático · ${snapshot.sourceLabel}" },
        "indice" to snapshot.index.toJson(),
        "formulario_modificado_desde_analisis" to snapshot.stale,
        "movilidad" to if (snapshot.evaluation == Evaluation.GTFS) snapshot.report else null,
        "entorno" to if (snapshot.evaluation == Evaluation.TERRITORY) snapshot.environment else null,
        "nota" to "El índice del entorno usa fuentes históricas. Las consultas en línea se identifican por separado y no reemplazan esas series. La demo usa datos simulados. Amenazas sin puntaje e IoT pendiente.",
    )
    fun jsonReport(): String = repository.jsonText(reportObject())
    fun summary(snapshot: AppState = state.value): String = buildString {
        appendLine("HABITA · Conoce antes de habitar")
        appendLine(snapshot.title); appendLine(snapshot.subtitle)
        appendLine(when (snapshot.evaluation) { Evaluation.DEMO -> "DATOS SIMULADOS DE DEMOSTRACIÓN"; Evaluation.TERRITORY -> "FUENTES HISTÓRICAS DEL EQUIPO"; else -> "DATOS GTFS PROGRAMADOS" })
        val index = snapshot.index
        appendLine("Índice: ${format(index.display(index.score))} / ${index.displayMaximum} · Cobertura: ${format(index.coverage * 100)}%")
        index.variables.forEach { appendLine("${it.name}: ${format(index.display(it.score))} / ${index.displayMaximum} · disponibilidad ${format(it.coverage * 100)}%") }
        if (snapshot.evaluation == Evaluation.TERRITORY) snapshot.environment?.let { environment ->
            appendLine("Modelo: ${index.version}. Propuesta pendiente de aprobación. Cuatro indicadores con 25% de peso. Global disponible con cobertura >= 75%.")
            appendLine("Aire: 14/10/2025 a 13/10/2026. Térmico: 01/07/2026 a 13/10/2026.")
            appendLine("No son mediciones de la fecha GTFS ni del interior de la vivienda.")
            environment.obj("parameters").forEach { (key, _) ->
                val p = environment.obj("parameters").obj(key); val station = p.obj("station"); val nearest = p.obj("nearest")
                appendLine("$key: ${p.text("status")}")
                if (station.isNotEmpty()) {
                    appendLine("Estación ${station.text("nombre")} · ${format(p.number("distance_m"))} m · ${station.text("fuente")}")
                    station.obj("resumenes").forEach { (component, _) ->
                        val summary = station.obj("resumenes").obj(component)
                        appendLine("  $component: ${summary.text("estado")} · días ${summary.text("dias_admisibles")} · meses ${summary["meses_usados"]}")
                    }
                }
                if (nearest.isNotEmpty()) appendLine("${nearest.text("nombre")} · ${format(p.number("distance_m"))} m · cantidad: ${p.number("count")?.toInt()}")
            }
            appendLine("Catálogo de salud MINSAL: junio 2026. Paraderos: bus abordable del feed incluido, sin filtro por calendario de servicio.")
            environment.objects("online_sources").forEach { source ->
                appendLine("${source.text("label")}: ${source.text("status")} · consultado ${source.text("checked_at")}")
                if (source.text("status") == "ok") {
                    onlineLines(source).forEach { appendLine(it) }
                    appendLine(source.obj("data").text("source_url")); appendLine(source.obj("data").text("note"))
                } else appendLine(source.text("error"))
            }
        }
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
        appendLine("Amenazas: la capa volcánica no cubre todos los riesgos ni modifica el índice. IoT: pendiente.")
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
