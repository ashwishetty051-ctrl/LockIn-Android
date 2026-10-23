package com.lockin.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {
    const val ACTION_FIRE = "com.lockin.app.ALARM_FIRE"
    private const val REQUEST_CODE = 4100

    fun hasExactAlarmAccess(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    }

    fun schedule(context: Context, hour: Int, minute: Int) {
        val manager = context.getSystemService(AlarmManager::class.java)
        if (!hasExactAlarmAccess(context)) return
        val next = LocalDateTime.now().withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            .let { if (it.isAfter(LocalDateTime.now())) it else it.plusDays(1) }
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val receiver = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, receiver,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setAlarmClock(AlarmManager.AlarmClockInfo(next, pending), pending)
    }

    fun cancel(context: Context) {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java).cancel(pending)
        pending.cancel()
        context.stopService(Intent(context, AlarmSoundService::class.java))
        context.getSystemService(NotificationManager::class.java).cancel(4201)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as LockInApplication
        val settings = runBlocking { app.settings.settings.first() }
        if (!settings.alarmEnabled || intent?.action != AlarmScheduler.ACTION_FIRE) return
        if (AlarmScheduler.hasExactAlarmAccess(context)) {
            AlarmScheduler.schedule(context, settings.alarmHour, settings.alarmMinute)
        }
        val sound = Intent(context, AlarmSoundService::class.java)
            .setAction(AlarmSoundService.ACTION_START)
        ContextCompat.startForegroundService(context, sound)

        val channelId = "lockin_alarm"
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Wake-up alarm", NotificationManager.IMPORTANCE_HIGH)
                    .apply {
                        description = "Wake-up alarm with a maths challenge"
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
            )
        }
        val fullScreen = PendingIntent.getActivity(
            context, 4200, Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_lockin)
            .setContentTitle("Time to wake up")
            .setContentText("Solve three quick questions to dismiss.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true)
            .build()
        manager.notify(4201, notification)
    }
}

class AlarmSoundService : Service() {
    private var ringtone: Ringtone? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            "alarm_sound_service", "Alarm sound", NotificationManager.IMPORTANCE_LOW
        )
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, "alarm_sound_service")
            .setSmallIcon(R.drawable.ic_lockin)
            .setContentTitle("LockIn alarm")
            .setContentText("Alarm is ringing. Open LockIn to solve the questions.")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            ServiceCompat.startForeground(
                this, 4202, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else startForeground(4202, notification)

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= 28) isLooping = true
            play()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        ringtone?.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.lockin.app.START_ALARM_SOUND"
        const val ACTION_STOP = "com.lockin.app.STOP_ALARM_SOUND"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as LockInApplication
        val settings = runBlocking { app.settings.settings.first() }
        if (settings.alarmEnabled && AlarmScheduler.hasExactAlarmAccess(context)) {
            AlarmScheduler.schedule(context, settings.alarmHour, settings.alarmMinute)
        }
        val focusNeeded = settings.activeUntil > 0L || settings.bedtimeEnabled
        if (focusNeeded) {
            try { FocusMonitorService.start(context) } catch (_: Exception) { }
        }
    }
}
