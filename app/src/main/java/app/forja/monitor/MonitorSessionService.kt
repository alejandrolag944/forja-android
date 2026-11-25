package app.forja.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.TextView

/** Keeps an explicit user-started session alive and can show a local game overlay. */
class MonitorSessionService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: TextView? = null
    private lateinit var windowManager: WindowManager

    private val updateTask = object : Runnable {
        override fun run() {
            val message = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getString(KEY_OVERLAY_TEXT, "FORJA   -- FPS   CPU --   GPU --")
                .orEmpty()
            overlay?.text = message
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_SESSION_ACTIVE, false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> removeOverlay()
        }
        startSessionForeground()
        if (intent?.action == ACTION_START && getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean(KEY_OVERLAY_ENABLED, false)) {
            showOverlay()
        }
        handler.removeCallbacks(updateTask)
        handler.post(updateTask)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(updateTask)
        removeOverlay()
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_SESSION_ACTIVE, false).apply()
        super.onDestroy()
    }

    private fun startSessionForeground() {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MonitorSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }.setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Forja está midiendo")
            .setContentText("Métricas locales · toca Detener para cerrar la sesión")
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Detener", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Medición Forja", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Sesión local de métricas del dispositivo"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this) || overlay != null) return
        val density = resources.displayMetrics.density
        val text = TextView(this).apply {
            text = "FORJA   -- FPS   CPU --   GPU --"
            setTextColor(android.graphics.Color.rgb(250, 238, 225))
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                setColor(android.graphics.Color.argb(242, 35, 27, 22))
                cornerRadius = 24 * density
                setStroke((1.5f * density).toInt(), android.graphics.Color.rgb(229, 160, 106))
            }
            elevation = 8 * density
        }
        val params = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
            LayoutParams.TYPE_APPLICATION_OVERLAY,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (14 * density).toInt()
            y = (112 * density).toInt()
        }
        runCatching {
            windowManager.addView(text, params)
            overlay = text
        }
    }

    private fun removeOverlay() {
        overlay?.let { view -> runCatching { windowManager.removeView(view) } }
        overlay = null
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean(KEY_OVERLAY_ENABLED, false).apply()
    }

    companion object {
        const val ACTION_START = "app.forja.monitor.action.START"
        const val ACTION_STOP = "app.forja.monitor.action.STOP"
        const val ACTION_SHOW = "app.forja.monitor.action.SHOW_OVERLAY"
        const val ACTION_HIDE = "app.forja.monitor.action.HIDE_OVERLAY"
        const val PREFERENCES = "forja-monitor"
        const val KEY_OVERLAY_TEXT = "overlay-text"
        const val KEY_OVERLAY_ENABLED = "overlay-enabled"
        const val KEY_SESSION_ACTIVE = "session-active"
        private const val CHANNEL_ID = "forja-monitor-session"
        private const val NOTIFICATION_ID = 904
    }
}
