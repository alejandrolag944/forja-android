package app.forja.monitor.access

import android.os.RemoteException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/** Runs a small, fixed set of read-only diagnostics as Shizuku's shell/root user. */
class PrivilegedMonitorService : IMonitorService.Stub() {
    override fun readThermalZones(): String = runReadOnly(
        "for z in /sys/class/thermal/thermal_zone*; do " +
            "[ -r \"\$z/type\" ] && [ -r \"\$z/temp\" ] || continue; " +
            "printf '%s|%s\\n' \"\$(cat \"\$z/type\" 2>/dev/null)\" \"\$(cat \"\$z/temp\" 2>/dev/null)\"; " +
            "done"
    )

    override fun listSurfaceLayers(): String = runReadOnly("dumpsys SurfaceFlinger --list")

    override fun readSurfaceLatency(layerName: String): String {
        if (layerName.isBlank() || layerName.length > 240 || layerName.any { !(it.isLetterOrDigit() || it in "._:/@ +()-[]") }) {
            return ""
        }
        return runReadOnly("dumpsys SurfaceFlinger --latency \"$layerName\"")
    }

    private fun runReadOnly(command: String): String {
        var process: Process? = null
        return try {
            process = ProcessBuilder("sh", "-c", command)
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
}
