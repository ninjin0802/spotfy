package com.ninjin.spotifyweb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager

/** WebView の再生をバックグラウンドで継続させるためのフォアグラウンドサービス */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(MediaControls.CHANNEL_ID, "再生", NotificationManager.IMPORTANCE_LOW)
        )
        // 曲情報つきのメディア通知を、そのままフォアグラウンド通知として使う
        val notification = MediaControls.instance?.buildNotification() ?: Notification.Builder(this, MediaControls.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
        startForeground(MediaControls.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        foregrounded = true
        // startForeground() より前に停止が要求されていたら、ここで止める
        if (stopRequested) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // 一時停止中も通知（メディアコントロール）は残す
        stopForeground(STOP_FOREGROUND_DETACH)
        MediaControls.instance?.post()
        foregrounded = false
        stopRequested = false
        hold(this, false)
        super.onDestroy()
    }

    companion object {
        @Volatile private var foregrounded = false
        @Volatile private var stopRequested = false

        fun start(context: Context): Boolean {
            stopRequested = false
            return runCatching { context.startForegroundService(Intent(context, PlaybackService::class.java)) }.isSuccess
        }

        private var wakeLock: PowerManager.WakeLock? = null
        private var wifiLock: WifiManager.WifiLock? = null

        /**
         * 再生中は CPU と Wi-Fi を眠らせない（画面を消すと通信が細って、曲の途中で止まるのを防ぐ）。
         * 一時停止したら離す
         */
        @Synchronized
        fun hold(context: Context, playing: Boolean) {
            val app = context.applicationContext
            if (playing) {
                if (wakeLock == null) wakeLock = app.getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "spotfy:playback").apply { setReferenceCounted(false) }
                if (wifiLock == null) wifiLock = app.getSystemService(WifiManager::class.java)
                    .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "spotfy:playback").apply { setReferenceCounted(false) }
                // 念のため長くても 3 時間で自動的に離す（次の曲で取り直す）
                wakeLock?.acquire(3 * 60 * 60 * 1000L)
                wifiLock?.acquire()
            } else {
                wakeLock?.takeIf { it.isHeld }?.release()
                wifiLock?.takeIf { it.isHeld }?.release()
            }
        }

        /** startForeground() 前に stopService() すると落ちるため、その場合は起動後に止める */
        fun stop(context: Context) {
            if (foregrounded) context.stopService(Intent(context, PlaybackService::class.java))
            else stopRequested = true
        }

    }
}
