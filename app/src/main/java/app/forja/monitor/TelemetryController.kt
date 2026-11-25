package app.forja.monitor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import app.forja.monitor.access.AccessMode
import app.forja.monitor.access.TelemetryAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

data class AppChoice(val label: String, val packageName: String)
data class SensorReading(val celsius: Double, val source: String)

data class DashboardState(
    val connected: Boolean = false,
    val overlayVisible: Boolean = false,
    val accessMode: AccessMode = AccessMode.NONE,
    val status: String = "Activa Shizuku o root para leer sensores",
    val monitoring: Boolean = false,
    val intervalMs: Long = 1_000,
    val apps: List<AppChoice> = emptyList(),
    val selectedApp: AppChoice? = null,
    val layers: List<String> = emptyList(),
    val selectedLayer: String? = null,
    val fps: Double? = null,
    val cpu: SensorReading? = null,
    val gpu: SensorReading? = null,
    val samples: List<Double> = emptyList(),
    val lastUpdated: String = "Esperando conexión"
)

/** Small process-level controller for permissions, polling and presentation state. */
class TelemetryController(private val context: Context) {
    val state: MutableState<DashboardState> = mutableStateOf(DashboardState())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val access = TelemetryAccess(context) { message ->
        val nextMode = when {
            message.startsWith("Shizuku activo") -> AccessMode.SHIZUKU
            message.startsWith("Root activo") -> AccessMode.ROOT
            message == "Conexión detenida" || message.contains("se desconectó") || message.startsWith("No se concedió") -> AccessMode.NONE
            else -> state.value.accessMode
        }
        state.value = state.value.copy(connected = nextMode != AccessMode.NONE, accessMode = nextMode, status = message)
    }
    private var pollingJob: Job? = null
    private var lastLayerScanAt = 0L
    private var cachedLayers: List<String> = emptyList()
    @Volatile private var selectedLayer: String? = null

    init {
        state.value = state.value.copy(apps = findLaunchableApps())
    }

    fun connectShizuku() = access.connectShizuku()

    fun connectRoot() {
        scope.launch {
            access.connectRoot()
            val mode = access.mode
            state.value = state.value.copy(
                connected = mode != AccessMode.NONE,
                accessMode = mode
            )
            if (mode != AccessMode.NONE) sampleNow()
        }
    }

    fun disconnect() {
        stopMonitoring()
        access.disconnect()
        cachedLayers = emptyList()
        lastLayerScanAt = 0L
        selectedLayer = null
        state.value = state.value.copy(
            connected = false,
            accessMode = AccessMode.NONE,
            fps = null,
            cpu = null,
            gpu = null,
            layers = emptyList(),
            selectedLayer = null
        )
    }

    fun setInterval(intervalMs: Long) {
        state.value = state.value.copy(intervalMs = intervalMs)
    }

    fun chooseApp(app: AppChoice) {
        selectedLayer = null
        cachedLayers = emptyList()
        lastLayerScanAt = 0L
        state.value = state.value.copy(
            selectedApp = app,
            selectedLayer = null,
            layers = emptyList(),
            fps = null,
            samples = emptyList()
        )
        if (state.value.monitoring) scope.launch { sampleNow() }
    }

    fun chooseLayer(layerName: String) {
        selectedLayer = layerName
        state.value = state.value.copy(selectedLayer = layerName, fps = null, samples = emptyList())
    }

    fun startMonitoring() {
        if (access.mode == AccessMode.NONE) {
            state.value = state.value.copy(status = "Conecta Shizuku o root antes de iniciar")
            return
        }
        if (state.value.selectedApp == null) {
            state.value = state.value.copy(status = "Elige un juego o una app para medir FPS")
            return
        }
        pollingJob?.cancel()
        state.value = state.value.copy(monitoring = true, status = "Medición en vivo", overlayVisible = false)
        context.getSharedPreferences(MonitorSessionService.PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(MonitorSessionService.KEY_OVERLAY_ENABLED, false)
            .putBoolean(MonitorSessionService.KEY_SESSION_ACTIVE, true)
            .apply()
        context.startForegroundService(Intent(context, MonitorSessionService::class.java).setAction(MonitorSessionService.ACTION_START))
        pollingJob = scope.launch {
            while (isActive) {
                val sessionActive = context.getSharedPreferences(MonitorSessionService.PREFERENCES, Context.MODE_PRIVATE)
                    .getBoolean(MonitorSessionService.KEY_SESSION_ACTIVE, true)
                if (!sessionActive) {
                    stopMonitoring()
                    break
                }
                sampleNow()
                delay(state.value.intervalMs)
            }
        }
    }

    fun stopMonitoring() {
        pollingJob?.cancel()
        pollingJob = null
        state.value = state.value.copy(monitoring = false, overlayVisible = false)
        context.getSharedPreferences(MonitorSessionService.PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(MonitorSessionService.KEY_SESSION_ACTIVE, false).apply()
        context.stopService(Intent(context, MonitorSessionService::class.java))
    }

    fun toggleOverlay() {
        if (!state.value.monitoring) {
            state.value = state.value.copy(status = "Inicia la medición antes de mostrar la ventana flotante")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            state.value = state.value.copy(status = "Permite la ventana flotante y vuelve a Forja")
            return
        }
        val show = !state.value.overlayVisible
        val action = if (show) MonitorSessionService.ACTION_SHOW else MonitorSessionService.ACTION_HIDE
        context.getSharedPreferences(MonitorSessionService.PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(MonitorSessionService.KEY_OVERLAY_ENABLED, show).apply()
        context.startService(Intent(context, MonitorSessionService::class.java).setAction(action))
        state.value = state.value.copy(overlayVisible = show)
    }

    suspend fun sampleNow() = withContext(Dispatchers.IO) {
        val current = state.value
        if (access.mode == AccessMode.NONE) return@withContext

        val app = current.selectedApp
        var foundLayers = cachedLayers
        if (app != null && (foundLayers.isEmpty() || System.currentTimeMillis() - lastLayerScanAt > LAYER_RESCAN_MS)) {
            foundLayers = access.listSurfaceLayers()
                .lineSequence()
                .map { it.trim() }
                .filter { it.contains(app.packageName, ignoreCase = true) }
                .distinct()
                .take(MAX_MATCHING_LAYERS)
                .toList()
            cachedLayers = foundLayers
            lastLayerScanAt = System.currentTimeMillis()
            if (selectedLayer !in foundLayers) selectedLayer = foundLayers.firstOrNull()
        }

        val latency = selectedLayer?.let(access::readSurfaceLatency).orEmpty()
        val fps = parseFps(latency)
        val (cpu, gpu) = parseThermals(access.readThermalZones())
        val latest = state.value
        val nextSamples = if (fps != null) (latest.samples + fps).takeLast(MAX_GRAPH_SAMPLES) else latest.samples
        state.value = latest.copy(
            connected = access.mode != AccessMode.NONE,
            accessMode = access.mode,
            status = when {
                app == null -> "Conectado · elige una app para medir FPS"
                foundLayers.isEmpty() -> "Conectado · abre el juego para que aparezca su capa gráfica"
                fps == null -> "Capa detectada · esperando fotogramas reales"
                else -> accessStatus(access.mode)
            },
            layers = foundLayers,
            selectedLayer = selectedLayer,
            fps = fps,
            cpu = cpu,
            gpu = gpu,
            samples = nextSamples,
            lastUpdated = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
        )
        context.getSharedPreferences(MonitorSessionService.PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(MonitorSessionService.KEY_OVERLAY_TEXT, overlayText(state.value))
            .apply()
    }

    fun close() {
        stopMonitoring()
        scope.cancel()
        access.disconnect()
    }

    private fun accessStatus(mode: AccessMode): String = when (mode) {
        AccessMode.ROOT -> "Root activo · lecturas de solo lectura"
        AccessMode.SHIZUKU -> "Shizuku activo · lecturas de solo lectura"
        AccessMode.NONE -> "Sin conexión"
    }

    private fun overlayText(snapshot: DashboardState): String {
        val fps = snapshot.fps?.let { String.format(Locale.US, "%.0f", it) } ?: "--"
        val cpu = snapshot.cpu?.let { String.format(Locale.US, "%.0f°", it.celsius) } ?: "--"
        val gpu = snapshot.gpu?.let { String.format(Locale.US, "%.0f°", it.celsius) } ?: "--"
        return "FORJA   $fps FPS   CPU $cpu   GPU $gpu"
    }

    @Suppress("DEPRECATION")
    private fun findLaunchableApps(): List<AppChoice> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val results: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            context.packageManager.queryIntentActivities(intent, 0)
        }
        return results.asSequence()
            .mapNotNull { result ->
                val info = result.activityInfo ?: return@mapNotNull null
                if (info.packageName == context.packageName) return@mapNotNull null
                val label = result.loadLabel(context.packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) null else AppChoice(label, info.packageName)
            }
            .distinctBy(AppChoice::packageName)
            .sortedBy(AppChoice::label)
            .toList()
    }

    private fun parseThermals(raw: String): Pair<SensorReading?, SensorReading?> {
        val values = raw.lineSequence().mapNotNull { line ->
            val parts = line.split('|', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val label = parts[0].trim().ifBlank { return@mapNotNull null }
            val number = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            val temperature = when {
                abs(number) >= 1_000_000 -> number / 1_000_000.0
                abs(number) >= 1_000 -> number / 1_000.0
                else -> number
            }
            if (temperature !in -20.0..150.0) return@mapNotNull null
            SensorReading(temperature, label)
        }.toList()

        val cpu = values.filter { it.source.contains("cpu", true) || it.source.contains("cluster", true) || it.source.contains("soc", true) }
            .maxByOrNull(SensorReading::celsius)
        val gpu = values.filter { it.source.contains("gpu", true) || it.source.contains("graphics", true) }
            .maxByOrNull(SensorReading::celsius)
        return cpu to gpu
    }

    private fun parseFps(raw: String): Double? {
        val timestamps = raw.lineSequence()
            .drop(1) // SurfaceFlinger prints the display's frame period on the first line.
            .mapNotNull { row ->
                val columns = row.trim().split(Regex("\\s+"))
                columns.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0L }
            }
            .distinct()
            .sorted()
            .toList()
            .takeLast(45)
        if (timestamps.size < 2) return null
        val span = timestamps.last() - timestamps.first()
        if (span <= 0L) return null
        val value = (timestamps.size - 1) * 1_000_000_000.0 / span.toDouble()
        return value.takeIf { it in 1.0..500.0 }
    }

    companion object {
        private const val MAX_MATCHING_LAYERS = 30
        private const val MAX_GRAPH_SAMPLES = 48
        private const val LAYER_RESCAN_MS = 5_000L
    }
}

/** Keeps the same monitoring session when Android recreates the Activity. */
object ForjaControllerStore {
    @Volatile private var instance: TelemetryController? = null

    fun get(context: Context): TelemetryController = instance ?: synchronized(this) {
        instance ?: TelemetryController(context.applicationContext).also { instance = it }
    }
}
