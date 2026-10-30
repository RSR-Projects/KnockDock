package com.example.knockdock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service for continuous desk vibration monitoring when background mode is enabled.
 */
class KnockMonitoringService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var controller: MonitoringController

    override fun onCreate() {
        super.onCreate()
        controller = MonitoringController.getInstance(this)
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "KnockDock::DeskSensorWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITORING) {
            controller.stopMonitoring()
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
            stopSelf()
            return START_NOT_STICKY
        }

        // Start foreground synchronously in onStartCommand to avoid ForegroundServiceDidNotStartInTimeException
        val initialNotification = buildNotification("Desk Commander")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this@KnockMonitoringService,
                    NOTIFICATION_ID,
                    initialNotification,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    } else {
                        0
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (_: Exception) {
        }

        // Ensure sensor listening stays active even if the UI Activity was closed or service was restarted by START_STICKY
        controller.ensureSensorListeningFromService()

        // Update notification text asynchronously once active profile is loaded
        serviceScope.launch {
            try {
                val activeProfile = controller.repository.getActiveProfile().first()
                val profileName = activeProfile?.name ?: "Desk Commander"
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, buildNotification(profileName))
            } catch (_: Exception) {
            }
        }

        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12 hours continuous background sensor lock
            }
        } catch (_: Exception) {
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Keep monitoring alive when user closes/swipes away the app from Recents
        if (controller.isMonitoring.value || controller.repository.settingsFlow.value.backgroundMonitoringEnabled) {
            controller.ensureSensorListeningFromService()
            try {
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(12 * 60 * 60 * 1000L)
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "KnockDock Desk Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active desk acoustic and vibration knock detection status"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(profileName: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, KnockMonitoringService::class.java).apply {
            action = ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KnockDock Active")
            .setContentText("Monitoring desk vibrations • Profile: $profileName")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Monitoring",
                stopPendingIntent
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "knockdock_foreground_channel"
        const val NOTIFICATION_ID = 2048
        const val ACTION_START_MONITORING = "com.rsrprojects.knockdock.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.rsrprojects.knockdock.STOP_MONITORING"
    }
}
