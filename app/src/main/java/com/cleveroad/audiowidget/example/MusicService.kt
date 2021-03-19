package com.cleveroad.audiowidget.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaPlayer.OnCompletionListener
import android.media.MediaPlayer.OnPreparedListener
import android.os.Build
import android.os.IBinder
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.cleveroad.audiowidget.AudioWidget
import com.cleveroad.audiowidget.AudioWidget.OnControlsClickListener
import com.cleveroad.audiowidget.AudioWidget.OnWidgetStateChangedListener
import java.io.IOException
import java.util.*

/**
 * Simple implementation of music service.
 */
class MusicService : Service(), OnPreparedListener, OnCompletionListener,
    MediaPlayer.OnErrorListener, OnControlsClickListener, OnWidgetStateChangedListener {
    private val items = arrayListOf<MusicItem>()
    private val audioWidget: AudioWidget by lazy {
        AudioWidget.Builder(this).build()
    }
    private val mediaPlayer: MediaPlayer by lazy {
        MediaPlayer()
    }
    private var preparing = false
    private var playingIndex = -1
    private var paused = false
    private var timer: Timer? = null
    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        mediaPlayer.setOnPreparedListener(this)
        mediaPlayer.setOnCompletionListener(this)
        mediaPlayer.setOnErrorListener(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
        } else {
            @Suppress("DEPRECATION")
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC)
        }
        audioWidget.controller().onControlsClickListener(this)
        audioWidget.controller().onWidgetStateChangedListener(this)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (intent.action != null) {
            when (intent.action) {
                ACTION_SET_TRACKS -> {
                    updateTracks()
                }
                ACTION_PLAY_TRACKS -> {
                    selectNewTrack(intent)
                }
                ACTION_CHANGE_STATE -> {
                    if (!(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(
                            this
                        ))
                    ) {
                        val show = intent.getBooleanExtra(EXTRA_CHANGE_STATE, false)
                        if (show) {
                            audioWidget.show(
                                preferences.getInt(KEY_POSITION_X, 100), preferences.getInt(
                                    KEY_POSITION_Y, 100
                                )
                            )
                        } else {
                            audioWidget.hide()
                        }
                    } else {
                        Log.w(
                            TAG,
                            "Can't change audio widget state! Device does not have drawOverlays permissions!"
                        )
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun selectNewTrack(intent: Intent) {
        if (preparing) {
            return
        }
        val item: MusicItem = intent.getParcelableExtra(EXTRA_SELECT_TRACK)
        if (playingIndex != -1 && items[playingIndex] == item) {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                audioWidget.controller().pause()
            } else {
                mediaPlayer.start()
                audioWidget.controller().start()
            }
            return
        }
        playingIndex = items.indexOf(item)
        startCurrentTrack()
    }

    private fun startCurrentTrack() {
        if (mediaPlayer.isPlaying || paused) {
            mediaPlayer.stop()
            paused = false
        }
        mediaPlayer.reset()
        if (playingIndex < 0) {
            return
        }
        try {
            mediaPlayer.setDataSource(this, items[playingIndex].fileUri!!)
            mediaPlayer.prepareAsync()
            preparing = true
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun updateTracks() {
        var playingItem: MusicItem? = null
        if (playingIndex != -1) {
            playingItem = items[playingIndex]
        }
        items.clear()
        tracks?.let {
            items.addAll(it)
        }
        tracks = null
        playingIndex = if (playingItem == null) {
            -1
        } else {
            items.indexOf(playingItem)
        }
        if (playingIndex == -1 && mediaPlayer.isPlaying) {
            mediaPlayer.stop()
            mediaPlayer.reset()
        }
    }

    override fun onDestroy() {
        audioWidget.controller().onControlsClickListener(null)
        audioWidget.controller().onWidgetStateChangedListener(null)
        audioWidget.hide()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.reset()
        mediaPlayer.release()
        stopTrackingPosition()
        super.onDestroy()
    }

    override fun onPrepared(mp: MediaPlayer) {
        preparing = false
        mediaPlayer.start()
        if (!audioWidget.isShown) {
            audioWidget.show(
                preferences.getInt(KEY_POSITION_X, 100), preferences.getInt(
                    KEY_POSITION_Y, 100
                )
            )
        }
        audioWidget.controller().start()
        audioWidget.controller().position(0)
        audioWidget.controller().duration(mediaPlayer.duration)
        stopTrackingPosition()
        startTrackingPosition()
        val size = resources.getDimensionPixelSize(R.dimen.cover_size)
        Glide.with(this)
            .asBitmap()
            .load(items[playingIndex].albumArtUri)
            .override(size, size)
            .centerCrop()
            .apply(RequestOptions().circleCrop())
            .into(object : CustomTarget<Bitmap?>() {
                override fun onLoadFailed(errorDrawable: Drawable?) {
                    audioWidget.controller().albumCover(null)
                }

                override fun onResourceReady(
                    resource: Bitmap,
                    transition: Transition<in Bitmap?>?
                ) {
                    audioWidget.controller().albumCoverBitmap(resource)
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
    }

    override fun onCompletion(mp: MediaPlayer) {
        if (playingIndex == -1) {
            audioWidget.controller().stop()
            return
        }
        playingIndex++
        if (playingIndex >= items.size) {
            playingIndex = 0
            if (items.size == 0) {
                audioWidget.controller().stop()
                return
            }
        }
        startCurrentTrack()
    }

    override fun onError(mp: MediaPlayer, what: Int, extra: Int): Boolean {
        preparing = true
        return false
    }

    override fun onPlaylistClicked(): Boolean {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return false
    }

    override fun onPlaylistLongClicked() {
        Log.d(TAG, "playlist long clicked")
    }

    override fun onPreviousClicked() {
        if (items.size == 0) return
        playingIndex--
        if (playingIndex < 0) {
            playingIndex = items.size - 1
        }
        startCurrentTrack()
    }

    override fun onPreviousLongClicked() {
        Log.d(TAG, "previous long clicked")
    }

    override fun onPlayPauseClicked(): Boolean {
        if (playingIndex == -1) {
            Toast.makeText(this, R.string.song_not_selected, Toast.LENGTH_SHORT).show()
            return true
        }
        paused = if (mediaPlayer.isPlaying) {
            stopTrackingPosition()
            mediaPlayer.pause()
            audioWidget.controller().start()
            true
        } else {
            startTrackingPosition()
            audioWidget.controller().pause()
            mediaPlayer.start()
            false
        }
        return false
    }

    override fun onPlayPauseLongClicked() {
        Log.d(TAG, "play/pause long clicked")
    }

    override fun onNextClicked() {
        if (items.size == 0) return
        playingIndex++
        if (playingIndex >= items.size) {
            playingIndex = 0
        }
        startCurrentTrack()
    }

    override fun onNextLongClicked() {
        Log.d(TAG, "next long clicked")
    }

    override fun onAlbumClicked() {
        Log.d(TAG, "album clicked")
    }

    override fun onAlbumLongClicked() {
        Log.d(TAG, "album long clicked")
    }

    private fun startTrackingPosition() {
        timer = Timer(TAG).also {
            it.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    val widget = audioWidget
                    val player = mediaPlayer
                    widget.controller().position(player.currentPosition)
                }
            }, UPDATE_INTERVAL, UPDATE_INTERVAL)
        }
    }

    private fun stopTrackingPosition() {
        timer?.let {
            it.cancel()
            it.purge()
        }
        timer = null
    }

    override fun onWidgetStateChanged(state: AudioWidget.State) {}

    override fun onWidgetPositionChanged(cx: Int, cy: Int) {
        preferences.edit()
            .putInt(KEY_POSITION_X, cx)
            .putInt(KEY_POSITION_Y, cy)
            .apply()
    }

    companion object {
        private const val TAG = "MusicService"
        private const val ACTION_SET_TRACKS = "ACTION_SET_TRACKS"
        private const val ACTION_PLAY_TRACKS = "ACTION_PLAY_TRACKS"
        private const val ACTION_CHANGE_STATE = "ACTION_CHANGE_STATE"
        private const val EXTRA_SELECT_TRACK = "EXTRA_SELECT_TRACK"
        private const val EXTRA_CHANGE_STATE = "EXTRA_CHANGE_STATE"
        private const val UPDATE_INTERVAL: Long = 1000
        private const val KEY_POSITION_X = "position_x"
        private const val KEY_POSITION_Y = "position_y"
        private var tracks: Array<MusicItem>? = null

        fun setTracks(context: Context, tracks: Array<MusicItem>) {
            val intent = Intent(ACTION_SET_TRACKS, null, context, MusicService::class.java)
            Companion.tracks = tracks
            context.startService(intent)
        }

        fun playTrack(context: Context, item: MusicItem) {
            val intent = Intent(ACTION_PLAY_TRACKS, null, context, MusicService::class.java)
            intent.putExtra(EXTRA_SELECT_TRACK, item)
            context.startService(intent)
        }

        fun setState(context: Context, isShowing: Boolean) {
            val intent = Intent(ACTION_CHANGE_STATE, null, context, MusicService::class.java)
            intent.putExtra(EXTRA_CHANGE_STATE, isShowing)
            context.startService(intent)
        }
    }
}