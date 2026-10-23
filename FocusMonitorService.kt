package com.lockin.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalTime

class FocusMonitorService : Service() {
    private val prefs get() = (application as LockInApplication).settings
    private val dao get() = (application as LockInApplication).database.sessionDao()
    private var monitor: Thread? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var blockingView: android.view.View? = null
    private var blockedPackage: String? = null
    private var lastForeground = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        monitor = Thread(::monitorLoop, "LockIn-app-monitor").also { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        return START_STICKY
    }

    private fun monitorLoop() {
        while (!Thread.currentThread().isInterrupted) {
            try {
                val settings = runBlocking { prefs.settings.first() }
                val now = System.currentTimeMillis()
                val inBedtime = settings.bedtimeEnabled && isInBedtime(settings, LocalTime.now())
                val focus = settings.activeUntil > now

                if (!focus && settings.activeUntil > 0L && settings.activeUntil <= now) {
                    runBlocking {
                        dao.insert(
                            FocusSession(
                                startedAt = settings.activeStartedAt,
                                endedAt = settings.activeUntil,
                                plannedMinutes = settings.activePlannedMinutes,
                                actualMinutes = settings.activePlannedMinutes,
                                status = "completed"
                            )
                        )
                        prefs.clearFocus()
                    }
                    dismissOverlay()
                }

                if (!focus && !settings.bedtimeEnabled) {
                    dismissOverlay()
                    stopSelf()
                    return
                }

                if (!hasUsageAccess()) {
                    dismissOverlay()
                    updateNotification(if (focus) settings.activeUntil else 0L, inBedtime, "Usage Access is off · open LockIn to restore monitoring")
                    SystemClock.sleep(4_000L)
                    continue
                }

                val current = foregroundPackage()
                if (current.isNotBlank()) lastForeground = current
                val activePackages = when {
                    focus -> settings.selectedPackages
                    inBedtime -> settings.bedtimePackages
                    else -> emptySet()
                }
                val blocked = activePackages.contains(lastForeground) &&
                    lastForeground !in ProtectedPackages.forDevice(this)
                if (blocked && hasOverlayPermission()) showOverlay(lastForeground)
                else dismissOverlay()

                val notificationText = when {
                    focus && settings.selectedPackages.isNotEmpty() && !hasOverlayPermission() ->
                        "Focus active · blocking screen needs overlay access"
                    focus -> "Focus session · ends ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(settings.activeUntil)}"
                    inBedtime -> "Bedtime protection is active"
                    else -> "Bedtime schedule enabled · waiting for start time"
                }
                updateNotification(if (focus) settings.activeUntil else 0L, inBedtime, notificationText)
                SystemClock.sleep(2_000L)
            } catch (_: InterruptedException) {
                return
            } catch (_: SecurityException) {
                dismissOverlay()
                SystemClock.sleep(4_000L)
            } catch (_: Exception) {
                dismissOverlay()
                SystemClock.sleep(3_000L)
            }
        }
    }

    private fun foregroundPackage(): String {
        val manager = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = manager.queryEvents(end - 12_000L, end)
        val event = UsageEvents.Event()
        var latestPackage = ""
        var latestTime = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val foreground = if (Build.VERSION.SDK_INT >= 29) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (foreground && event.timeStamp >= latestTime) {
                latestTime = event.timeStamp
                latestPackage = event.packageName ?: ""
            }
        }
        return latestPackage
    }

    private fun isInBedtime(settings: AppSettings, time: LocalTime): Boolean {
        val current = time.hour * 60 + time.minute
        val start = settings.bedtimeStartMinute
        val end = settings.bedtimeEndMinute
        return if (start == end) false
        else if (start > end) current >= start || current < end
        else current in start until end
    }

    private fun hasOverlayPermission() =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        return ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun showOverlay(app: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showOverlay(app) }
            return
        }
        if (!hasOverlayPermission()) return
        if (blockingView != null && blockedPackage == app) return
        dismissOverlay()
        blockedPackage = app
        val index = ((System.currentTimeMillis() / 30_000L) % MESSAGES.size).toInt()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(36, 32, 36, 32)
            setBackgroundColor(Color.rgb(15, 23, 42))
        }
        container.addView(TextView(this).apply {
            text = "LOCKIN  •  BACK TO YOUR GOAL"
            setTextColor(Color.rgb(250, 204, 21))
            textSize = 14f
            gravity = Gravity.CENTER
        })
        container.addView(TextView(this).apply {
            text = MESSAGES[index]
            setTextColor(Color.rgb(248, 250, 252))
            textSize = 26f
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 28)
        })
        container.addView(Button(this).apply {
            text = "Back to study"
            setOnClickListener {
                dismissOverlay()
                val intent = Intent(this@FocusMonitorService, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
            }
        })
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        try {
            windowManager?.addView(container, params)
            blockingView = container
        } catch (_: Exception) {
            blockingView = null
            blockedPackage = null
        }
    }

    private fun dismissOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissOverlay() }
            return
        }
        blockingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        blockingView = null
        blockedPackage = null
    }

    private fun startForegroundCompat() {
        val notification = buildNotification(0L, false)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Focus session", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shows when LockIn's local focus monitor is active." }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(until: Long, bedtime: Boolean, overrideText: String? = null) {
        val text = overrideText ?: if (until > 0L) {
            "Focus session · ends ${java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(until)}"
        } else if (bedtime) "Bedtime protection is active" else "Monitoring your focus session"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(until, bedtime, text))
    }

    private fun buildNotification(
        until: Long,
        bedtime: Boolean,
        message: String = when {
            until > 0 -> "Focus session in progress"
            bedtime -> "Bedtime protection is active"
            else -> "Focus monitor is ready"
        }
    ): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lockin)
            .setContentTitle("LockIn")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        monitor?.interrupt()
        dismissOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.lockin.app.START_MONITOR"
        const val ACTION_STOP = "com.lockin.app.STOP_MONITOR"
        private const val CHANNEL_ID = "focus_monitor"
        private const val NOTIFICATION_ID = 3101
        private val MESSAGES = listOf(
            "You paid for this. Show up for yourself today.",
            "Your future self is counting on you.",
            "Just one more focused minute.",
            "Discipline today, freedom tomorrow.",
            "You came here to study. Keep your promise."
        )

        fun start(context: Context) {
            val intent = Intent(context, FocusMonitorService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
