package app.forja.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.forja.monitor.access.AccessMode
import java.util.Locale

private val Ink = Color(0xFF171310)
private val Panel = Color(0xFF211A16)
private val Raised = Color(0xFF2A211B)
private val Ember = Color(0xFFE5A06A)
private val Cream = Color(0xFFF7E8D8)
private val Muted = Color(0xFFB9A99D)
private val Sage = Color(0xFFA9C982)
private val Coral = Color(0xFFE48A77)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(23, 19, 16)
        window.navigationBarColor = android.graphics.Color.rgb(23, 19, 16)
        setContent {
            ForjaTheme {
                val controller = remember { ForjaControllerStore.get(applicationContext) }
                val state by controller.state
                DashboardScreen(state = state, controller = controller)
            }
        }
    }
}

@Composable
private fun ForjaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ember,
            onPrimary = Ink,
            secondary = Sage,
            background = Ink,
            surface = Panel,
            onSurface = Cream,
            surfaceVariant = Raised,
            onSurfaceVariant = Muted,
            error = Coral
        ),
        content = content
    )
}

@Composable
private fun DashboardScreen(state: DashboardState, controller: TelemetryController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val contentInsets = WindowInsets.statusBars.asPaddingValues()
    val bottomInsets = WindowInsets.navigationBars.asPaddingValues()
    var showApps by remember { mutableStateOf(false) }
    var showLayers by remember { mutableStateOf(false) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        controller.startMonitoring()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(top = contentInsets.calculateTopPadding() + 12.dp, bottom = bottomInsets.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ember.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = Ember, modifier = Modifier.size(25.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("FORJA", color = Cream, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Text("PULSO DEL DISPOSITIVO", color = Muted, fontSize = 10.sp, letterSpacing = 1.3.sp)
            }
            Surface(color = if (state.monitoring) Sage.copy(alpha = 0.13f) else Raised, shape = CircleShape) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (state.monitoring) Sage else Muted))
                    Spacer(Modifier.width(7.dp))
                    Text(if (state.monitoring) "EN VIVO" else "PAUSA", color = if (state.monitoring) Sage else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Text("Tu juego, en tiempo real.", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp)

        FpsCard(state)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "CPU / SoC",
                value = state.cpu?.celsius?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                unit = if (state.cpu == null) "°C" else "°C",
                detail = state.cpu?.source ?: "Sensor no expuesto",
                icon = Icons.Filled.DeviceThermostat,
                accent = Coral
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "GPU",
                value = state.gpu?.celsius?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                unit = "°C",
                detail = state.gpu?.source ?: "Sensor no expuesto",
                icon = Icons.Filled.Memory,
                accent = Ember
            )
        }

        TrendCard(state)

        SectionHeading(icon = Icons.Filled.Apps, title = "APP A MEDIR")
        Column {
            Box {
                OutlinedButton(
                    onClick = { showApps = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream)
                ) {
                    Icon(Icons.Filled.Apps, contentDescription = null, tint = Ember)
                    Spacer(Modifier.width(9.dp))
                    Text(state.selectedApp?.label ?: "Selecciona un juego o app", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = showApps, onDismissRequest = { showApps = false }) {
                    state.apps.take(120).forEach { app ->
                        DropdownMenuItem(
                            text = { Column {
                                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName, fontSize = 10.sp, color = Muted)
                            } },
                            onClick = { controller.chooseApp(app); showApps = false }
                        )
                    }
                }
            }
            if (state.layers.size > 1) {
                Spacer(Modifier.height(8.dp))
                Box {
                    OutlinedButton(onClick = { showLayers = true }, shape = RoundedCornerShape(14.dp)) {
                        Text("Capa: ${state.selectedLayer ?: "elegir"}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = showLayers, onDismissRequest = { showLayers = false }) {
                        state.layers.forEach { layer ->
                            DropdownMenuItem(text = { Text(layer, maxLines = 2, overflow = TextOverflow.Ellipsis) }, onClick = {
                                controller.chooseLayer(layer)
                                showLayers = false
                            })
                        }
                    }
                }
            }
        }

        SectionHeading(icon = Icons.Filled.Tune, title = "FRECUENCIA DE LECTURA")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(500L to "0.5 s", 1_000L to "1 s", 2_000L to "2 s", 5_000L to "5 s").forEach { (ms, label) ->
                FilterChip(
                    selected = state.intervalMs == ms,
                    onClick = { controller.setInterval(ms) },
                    label = { Text(label, fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Button(
            onClick = {
                if (state.monitoring) {
                    controller.stopMonitoring()
                } else if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    controller.startMonitoring()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Ink)
        ) {
            Icon(if (state.monitoring) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.monitoring) "DETENER MEDICIÓN" else "EMPEZAR A MEDIR", fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
        }

        OverlaySection(state, controller)

        AccessSection(state, controller) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (launchIntent != null) context.startActivity(launchIntent)
            else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/")))
        }

        TutorialSection()

        Surface(color = Raised.copy(alpha = 0.7f), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = Ember, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Los sensores dependen del fabricante. Si Android o el kernel no expone CPU/GPU, Forja muestra “no disponible”; nunca inventa una temperatura. FPS usa tiempos de presentación de SurfaceFlinger.",
                    color = Muted, fontSize = 12.sp, lineHeight = 18.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(top = 3.dp)) {
            Text("Código abierto · Hecha con asistencia de IA", color = Cream.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text("Sin cuenta, sin nube y sin telemetría", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun OverlaySection(state: DashboardState, controller: TelemetryController) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = Ember, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(9.dp))
                Text("VENTANA FLOTANTE", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Text("Muestra FPS y temperaturas sobre el juego. Requiere permiso para aparecer encima de otras apps.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            OutlinedButton(
                onClick = controller::toggleOverlay,
                enabled = state.monitoring,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (state.overlayVisible) "Ocultar ventana flotante" else "Permitir / mostrar ventana flotante")
            }
        }
    }
}

@Composable
private fun TutorialSection() {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SectionHeading(icon = Icons.Filled.Info, title = "GUÍA PASO A PASO")
        TutorialCard(
            title = "Shizuku por depuración inalámbrica · Android 11+",
            steps = listOf(
                "Instala Shizuku desde su sitio oficial y vuelve a Forja.",
                "Activa Opciones de desarrollador y Depuración inalámbrica en Ajustes.",
                "En Shizuku, pulsa Iniciar mediante depuración inalámbrica. Si pide emparejar, abre Emparejar dispositivo con código en Ajustes y escribe el código en la notificación de Shizuku.",
                "Regresa a Shizuku y pulsa Iniciar. Luego vuelve a Forja, pulsa Conectar Shizuku y autoriza el acceso."
            ),
            expandedInitially = true
        )
        TutorialCard(
            title = "Shizuku por USB / ADB · Android 10 o anterior",
            steps = listOf(
                "Instala Shizuku y las Platform Tools de Android en un ordenador.",
                "Activa Opciones de desarrollador y Depuración USB; conecta y autoriza el teléfono.",
                "En Shizuku, abre Iniciar desde un ordenador y sigue el comando que aparece allí.",
                "Cuando Shizuku indique que está activo, vuelve a Forja, pulsa Conectar Shizuku y autoriza. Tras reiniciar, normalmente hay que iniciarlo otra vez."
            )
        )
        TutorialCard(
            title = "Root · Magisk o KernelSU",
            steps = listOf(
                "Abre Forja y pulsa Probar en Root.",
                "Acepta la solicitud en tu gestor de root.",
                "Forja confirma el acceso con una lectura de identidad y después ejecuta solo consultas de sensores y SurfaceFlinger.",
                "Si no aparece la solicitud, revisa el gestor de root y vuelve a pulsar Probar."
            )
        )
        TutorialCard(
            title = "Elegir juego, medir y poner el overlay",
            steps = listOf(
                "Con Shizuku o root conectado, abre el selector APP A MEDIR y elige un juego instalado.",
                "Ajusta la frecuencia de lectura y pulsa Empezar a medir.",
                "Para ver los valores mientras juegas, pulsa Permitir / mostrar ventana flotante y concede el permiso especial de Android.",
                "Cambia de app para abrir el juego. La notificación persistente de Forja permite detener la medición."
            )
        )
        Surface(color = Raised.copy(alpha = 0.65f), shape = RoundedCornerShape(16.dp)) {
            Text(
                "Si FPS o una temperatura sigue en --, ese modelo puede ocultar la capa o el sensor aunque el acceso esté conectado. Forja lo deja como no disponible; no lo estima.",
                color = Muted, fontSize = 11.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}

@Composable
private fun TutorialCard(title: String, steps: List<String>, expandedInitially: Boolean = false) {
    var expanded by remember(title) { mutableStateOf(expandedInitially) }
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        color = Panel,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Cream, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(if (expanded) "−" else "+", color = Ember, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
            if (expanded) {
                Spacer(Modifier.height(11.dp))
                steps.forEachIndexed { index, step ->
                    Row(modifier = Modifier.padding(bottom = 9.dp), verticalAlignment = Alignment.Top) {
                        Surface(color = Ember.copy(alpha = 0.16f), shape = CircleShape) {
                            Text((index + 1).toString(), color = Ember, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(step, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FpsCard(state: DashboardState) {
    Surface(color = Panel, shape = RoundedCornerShape(26.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Speed, contentDescription = null, tint = Ember, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("FPS DEL JUEGO", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                if (state.connected) Icon(Icons.Filled.CheckCircle, contentDescription = "Conectado", tint = Sage, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.fps?.let { String.format(Locale.US, "%.0f", it) } ?: "--",
                    color = Cream, fontSize = 54.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp
                )
                Text(" FPS", color = Ember, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(state.selectedApp?.label ?: "Elige una app para empezar", color = Cream.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
            Text(state.status, color = Muted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (state.connected) Sage.copy(alpha = 0.12f) else Ember.copy(alpha = 0.12f), shape = CircleShape) {
                    Text(
                        when (state.accessMode) {
                            AccessMode.ROOT -> " ROOT "
                            AccessMode.SHIZUKU -> " SHIZUKU "
                            AccessMode.NONE -> " SIN ACCESO "
                        },
                        color = if (state.connected) Sage else Ember, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text("Actualizado ${state.lastUpdated}", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    title: String,
    value: String,
    unit: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color
) {
    Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(title, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = Cream, fontSize = if (value == "--") 28.sp else 31.sp, fontWeight = FontWeight.Bold)
                Text(" $unit", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(detail, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TrendCard(state: DashboardState) {
    Surface(color = Panel, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("FPS RECIENTES", color = Cream, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                Spacer(Modifier.weight(1f))
                Text("cada ${state.intervalMs / 1_000.0}s", color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                val values = state.samples
                if (values.size >= 2) {
                    val min = (values.minOrNull() ?: 0.0).toFloat()
                    val max = (values.maxOrNull() ?: 1.0).toFloat().let { if (it == min) it + 1f else it }
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = size.width * index / (values.size - 1).toFloat()
                        val y = size.height - ((value.toFloat() - min) / (max - min)).coerceIn(0f, 1f) * (size.height - 8f) - 4f
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, color = Ember, style = Stroke(width = 3.dp.toPx()))
                    drawCircle(color = Ember, radius = 4.dp.toPx(), center = Offset(size.width, size.height - ((values.last().toFloat() - min) / (max - min)).coerceIn(0f, 1f) * (size.height - 8f) - 4f))
                } else {
                    drawLine(Muted.copy(alpha = 0.3f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 1.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun SectionHeading(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Ember, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, color = Muted, fontSize = 11.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AccessSection(state: DashboardState, controller: TelemetryController, openShizuku: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(icon = Icons.Filled.Settings, title = "MÉTODOS DE ACTIVACIÓN")
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = Ember, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Shizuku · depuración inalámbrica", color = Cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Android 11+: inicia Shizuku desde Opciones de desarrollador → Depuración inalámbrica y luego autoriza Forja.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                }
                Button(
                    onClick = { if (state.accessMode == AccessMode.SHIZUKU) controller.disconnect() else controller.connectShizuku() },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Raised, contentColor = Cream)
                ) {
                    Text(if (state.accessMode == AccessMode.SHIZUKU) "Desconectar Shizuku" else "Conectar Shizuku")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = Sage, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Root · Magisk / KernelSU", color = Cream, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Pide permiso root y solo ejecuta lecturas.", color = Muted, fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { if (state.accessMode == AccessMode.ROOT) controller.disconnect() else controller.connectRoot() },
                        shape = RoundedCornerShape(13.dp)
                    ) { Text(if (state.accessMode == AccessMode.ROOT) "Quitar" else "Probar") }
                }
                if (!state.connected) {
                    OutlinedButton(onClick = openShizuku, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Text("Abrir guía de Shizuku", color = Ember)
                    }
                }
            }
        }
    }
}
