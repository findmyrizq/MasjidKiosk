package com.masjid.kiosk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class KioskService : Service() {

    companion object {
        private const val CHANNEL_ID = "kiosk"
        private const val NOTIFICATION_ID = 1
        private const val WATCHDOG_INTERVAL_MS = 30_000L

        fun start(ctx: Context) {
            val intent = Intent(ctx, KioskService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    ctx.startForegroundService(intent)
                } else {
                    ctx.startService(intent)
                }
            } catch (e: Exception) {
                // best effort — MainActivity will start us again next time
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private val watchdog = object : Runnable {
        override fun run() {
            if (Kiosk.locked) Kiosk.launchTarget(this@KioskService)
            handler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Kiosk.locked) Kiosk.launchTarget(context)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        handler.postDelayed(watchdog, WATCHDOG_INTERVAL_MS)
    }

    private fun startInForeground() {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Kiosk watchdog", NotificationManager.IMPORTANCE_MIN)
            )
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle("Masjid Kiosk active")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(watchdog)
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
