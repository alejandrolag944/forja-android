package app.forja.monitor.access

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

enum class AccessMode { NONE, SHIZUKU, ROOT }

/** Owns permissioned sensor access. Only fixed, read-only diagnostics are executed. */
class TelemetryAccess(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    private val shizukuService = AtomicReference<IMonitorService?>(null)
    @Volatile var mode: AccessMode = AccessMode.NONE
        private set

    private val serviceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(context, PrivilegedMonitorService::class.java))
            .processNameSuffix("forja-monitor")
            .tag("forja-monitor-v1")
            .version(1)
            .daemon(false)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shizukuService.set(binder?.let(IMonitorService.Stub::asInterface))
            mode = AccessMode.SHIZUKU
            val identity = if (runCatching { Shizuku.getUid() }.getOrNull() == 0) "root" else "ADB shell"
            onStatus("Shizuku activo · $identity")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shizukuService.set(null)
            if (mode == AccessMode.SHIZUKU) mode = AccessMode.NONE
            onStatus("Shizuku se desconectó")
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) bindShizuku()
            else onStatus("Permiso de Shizuku rechazado")
        }
    }

    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    fun connectShizuku() {
        try {
            if (Shizuku.isPreV11()) {
                onStatus("Actualiza Shizuku para usar esta conexión")
                return
            }
            if (!Shizuku.pingBinder()) {
                val intent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                if (intent != null) context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                onStatus("Abre Shizuku y pulsa Iniciar")
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindShizuku()
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                onStatus("Autoriza Forja desde la pantalla de Shizuku")
                Shizuku.requestPermission(REQUEST_CODE)
            } else {
                Shizuku.requestPermission(REQUEST_CODE)
            }
        } catch (_: Exception) {
            onStatus("No se pudo conectar con Shizuku")
        }
    }

    private fun bindShizuku() {
        try {
            Shizuku.bindUserService(serviceArgs, serviceConnection)
        } catch (_: Exception) {
            onStatus("No se pudo iniciar el servicio de Forja en Shizuku")
        }
    }

    fun connectRoot(): Boolean {
        if (mode == AccessMode.SHIZUKU) disconnect()
        val output = runRoot("id")
        val connected = output.contains("uid=0")
        if (connected) {
            mode = AccessMode.ROOT
            shizukuService.set(null)
            onStatus("Root activo · solo lectura")
        } else {
            mode = AccessMode.NONE
            onStatus("No se concedió acceso root")
        }
        return connected
    }

    fun readThermalZones(): String = when (mode) {
        AccessMode.SHIZUKU -> safeShizuku { it.readThermalZones() }
        AccessMode.ROOT -> runRoot(THERMAL_COMMAND)
        AccessMode.NONE -> ""
    }

    fun listSurfaceLayers(): String = when (mode) {
        AccessMode.SHIZUKU -> safeShizuku { it.listSurfaceLayers() }
        AccessMode.ROOT -> runRoot("dumpsys SurfaceFlinger --list")
        AccessMode.NONE -> ""
    }

    fun readSurfaceLatency(layerName: String): String = when (mode) {
        AccessMode.SHIZUKU -> safeShizuku { it.readSurfaceLatency(layerName) }
        AccessMode.ROOT -> {
            if (layerName.isBlank() || layerName.length > 240 || layerName.any { !(it.isLetterOrDigit() || it in "._:/@ +()-[]") }) ""
            else runRoot("dumpsys SurfaceFlinger --latency \"$layerName\"")
        }
        AccessMode.NONE -> ""
    }

    fun disconnect() {
        try {
            if (mode == AccessMode.SHIZUKU) Shizuku.unbindUserService(serviceArgs, serviceConnection, true)
        } catch (_: Exception) {
            // Shizuku may already be stopped.
        }
        shizukuService.set(null)
        mode = AccessMode.NONE
        onStatus("Conexión detenida")
    }

    private inline fun safeShizuku(call: (IMonitorService) -> String): String = try {
        shizukuService.get()?.let(call).orEmpty()
    } catch (_: RemoteException) {
        ""
    } catch (_: Exception) {
        ""
    }

    private fun runRoot(command: String): String {
        var process: Process? = null
        return try {
            process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(4, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ""
            }
            BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().take(250_000) }
        } catch (_: Exception) {
            ""
        } finally {
            process?.destroy()
        }
    }

    companion object {
        private const val REQUEST_CODE = 7421
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val THERMAL_COMMAND =
            "for z in /sys/class/thermal/thermal_zone*; do " +
                "[ -r \"\$z/type\" ] && [ -r \"\$z/temp\" ] || continue; " +
                "printf '%s|%s\\n' \"\$(cat \"\$z/type\" 2>/dev/null)\" \"\$(cat \"\$z/temp\" 2>/dev/null)\"; " +
                "done"
    }
}
