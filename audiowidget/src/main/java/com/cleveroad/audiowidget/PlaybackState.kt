package com.cleveroad.audiowidget

import java.util.*

/**
 * Helper class for managing playback state.
 */
internal class PlaybackState {
    private var state = Configuration.STATE_STOPPED
    private var position = 0
    private var duration = 0
    private val stateListeners: MutableSet<PlaybackStateListener> = HashSet()

    fun addPlaybackStateListener(playbackStateListener: PlaybackStateListener): Boolean {
        return stateListeners.add(playbackStateListener)
    }

    fun removePlaybackStateListener(playbackStateListener: PlaybackStateListener): Boolean {
        return stateListeners.remove(playbackStateListener)
    }

    fun state(): Int {
        return state
    }

    fun position(): Int {
        return position
    }

    fun duration(): Int {
        return duration
    }

    fun position(position: Int): PlaybackState {
        this.position = position
        notifyProgressChanged(position)
        return this
    }

    fun duration(duration: Int): PlaybackState {
        this.duration = duration
        return this
    }

    fun start(initiator: Any) {
        state(Configuration.STATE_PLAYING, initiator)
    }

    fun pause(initiator: Any) {
        state(Configuration.STATE_PAUSED, initiator)
    }

    fun stop(initiator: Any) {
        state(Configuration.STATE_STOPPED, initiator)
        position(0)
    }

    private fun state(state: Int, initiator: Any) {
        if (this.state == state) return
        val oldState = this.state
        this.state = state
        for (listener in stateListeners) {
            listener.onStateChanged(oldState, state, initiator)
        }
    }

    private fun notifyProgressChanged(position: Int) {
        val progress = 1f * position / duration
        for (listener in stateListeners) {
            listener.onProgressChanged(position, duration, progress)
        }
    }

    /**
     * Playback state listener.
     */
    internal interface PlaybackStateListener {
        /**
         * Called when playback state is changed.
         * @param oldState old playback state
         * @param newState new playback state
         * @param initiator who initiate changes
         */
        fun onStateChanged(oldState: Int, newState: Int, initiator: Any?)

        /**
         * Called when playback progress changed.
         * @param position current position of track
         * @param duration duration of track
         * @param percentage value equals to `position / duration`
         */
        fun onProgressChanged(position: Int, duration: Int, percentage: Float)
    }
}