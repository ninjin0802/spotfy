package com.ninjin.spotifyweb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder

/** WebView の再生をバックグラウンドで継続させるためのフォアグラウンドサービス */
class PlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "再生", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("バックグラウンドで再生中")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        foregrounded = true
        // startForeground() より前に停止が要求されていたら、ここで止める
        if (stopRequested) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        foregrounded = false
        stopRequested = false
        super.onDestroy()
    }

    companion object {
        @Volatile private var foregrounded = false
        @Volatile private var stopRequested = false

        fun start(context: Context): Boolean {
            stopRequested = false
            return runCatching { context.startForegroundService(Intent(context, PlaybackService::class.java)) }.isSuccess
        }

        /** startForeground() 前に stopService() すると落ちるため、その場合は起動後に止める */
        fun stop(context: Context) {
            if (foregrounded) context.stopService(Intent(context, PlaybackService::class.java))
            else stopRequested = true
        }

        private const val CHANNEL_ID = "playback"
    }
}
