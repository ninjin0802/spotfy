package com.ninjin.spotifyweb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import java.net.URL
import java.util.concurrent.Executors

/**
 * Android のメディアコントロール（通知・ロック画面・クイック設定）に曲情報を出し、
 * そこからの操作を Web Player に伝える。
 */
class MediaControls(private val context: Context, private val onAction: (String, Long) -> Unit) {

    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private val nm = context.getSystemService(NotificationManager::class.java)

    val session = MediaSession(context, "SpotifyWeb").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() = onAction("play", 0)
            override fun onPause() = onAction("pause", 0)
            override fun onSkipToNext() = onAction("next", 0)
            override fun onSkipToPrevious() = onAction("prev", 0)
            override fun onSeekTo(pos: Long) = onAction("seek", pos)
        })
        isActive = true
    }

    private var title = ""
    private var artist = ""
    private var album = ""
    private var artUrl = ""
    private var art: Bitmap? = null
    var playing = false
        private set
    private var hasTrack = false

    init {
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, tr("再生", "Playback"), NotificationManager.IMPORTANCE_LOW))
        instance = this
    }

    fun update(title: String, artist: String, album: String, artUrl: String, playing: Boolean, positionMs: Long, durationMs: Long) {
        val trackChanged = title != this.title || artist != this.artist || album != this.album
        this.title = title
        this.artist = artist
        this.album = album
        this.playing = playing
        hasTrack = title.isNotEmpty()
        if (artUrl != this.artUrl) {
            this.artUrl = artUrl
            art = null
            if (artUrl.startsWith("https://i.scdn.co/")) loadArt(artUrl)
        }
        if (trackChanged || art != null) publishMetadata(durationMs)
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO
                )
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, positionMs, if (playing) 1f else 0f)
                .build()
        )
        lastDuration = durationMs
        post()
    }

    private var lastDuration = 0L

    private fun publishMetadata(durationMs: Long) {
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
                .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
    }

    private fun loadArt(url: String) = loader.execute {
        val bmp = runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it) } }.getOrNull()
        main.post {
            if (url != artUrl || bmp == null) return@post
            art = bmp
            publishMetadata(lastDuration)
            post()
        }
    }

    fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(icon: Int, label: String, name: String) = Notification.Action.Builder(
            android.graphics.drawable.Icon.createWithResource(context, icon), label,
            PendingIntent.getBroadcast(
                context, name.hashCode(), Intent(context, Receiver::class.java).setAction(name),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        ).build()
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifEmpty { context.getString(R.string.app_name) })
            .setContentText(artist)
            .setLargeIcon(art)
            .setContentIntent(open)
            .setOngoing(playing)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(action(android.R.drawable.ic_media_previous, tr("前へ", "Previous"), "prev"))
            .addAction(
                if (playing) action(android.R.drawable.ic_media_pause, tr("一時停止", "Pause"), "pause")
                else action(android.R.drawable.ic_media_play, tr("再生", "Play"), "play")
            )
            .addAction(action(android.R.drawable.ic_media_next, tr("次へ", "Next"), "next"))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()
    }

    /** 曲があれば通知を出す（再生中はサービスが同じ ID でフォアグラウンド化する） */
    fun post() {
        if (hasTrack) runCatching { nm.notify(NOTIFICATION_ID, buildNotification()) }
    }

    fun release() {
        nm.cancel(NOTIFICATION_ID)
        session.release()
        if (instance === this) instance = null
    }

    /** 通知ボタンからの操作 */
    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            instance?.onAction?.invoke(intent.action ?: return, 0)
        }
    }

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 1
        @Volatile var instance: MediaControls? = null
            private set
    }
}
