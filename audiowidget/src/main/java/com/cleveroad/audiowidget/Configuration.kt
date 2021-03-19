package com.cleveroad.audiowidget

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.ViewConfiguration
import android.view.animation.Interpolator
import androidx.annotation.ColorInt
import java.util.*

/**
 * Audio widget configuration class.
 */
internal class Configuration private constructor(private val builder: Builder) {
    val lightColor: Int = builder.lightColor
    val darkColor: Int = builder.darkColor
    val progressColor: Int = builder.progressColor
    val expandedColor: Int = builder.expandedColor
    val random: Random = builder.random!!
    val playDrawable: Drawable = builder.playDrawable!!
    val pauseDrawable: Drawable = builder.pauseDrawable!!
    val prevDrawable: Drawable = builder.prevDrawable!!
    val nextDrawable: Drawable = builder.nextDrawable!!
    val playlistDrawable: Drawable = builder.playlistDrawable!!
    val albumDrawable: Drawable = builder.albumDrawable!!
    val context: Context? = builder.context
    val playbackState: PlaybackState = builder.playbackState!!
    val buttonPadding: Int = builder.buttonPadding
    val crossStrokeWidth: Float = builder.crossStrokeWidth
    val progressStrokeWidth: Float = builder.progressStrokeWidth
    val shadowRadius: Float = builder.shadowRadius
    val shadowDx: Float = builder.shadowDx
    val shadowDy: Float = builder.shadowDy
    val shadowColor: Int = builder.shadowColor
    val bubblesMinSize: Float = builder.bubblesMinSize
    val bubblesMaxSize: Float = builder.bubblesMaxSize
    val crossColor: Int = builder.crossColor
    val crossOverlappedColor: Int = builder.crossOverlappedColor
    val accDecInterpolator: Interpolator = builder.accDecInterpolator!!
    val prevNextExtraPadding: Int = builder.prevNextExtraPadding

    val radius: Float
        get() = builder.radius

    val widgetWidth: Float
        get() = builder.width

    internal class Builder {
        var lightColor = 0
        var darkColor = 0
        var progressColor = 0
        var expandedColor = 0
        var width = 0f
        var radius = 0f
        var context: Context? = null
        var random: Random? = null
        var playDrawable: Drawable? = null
        var pauseDrawable: Drawable? = null
        var prevDrawable: Drawable? = null
        var nextDrawable: Drawable? = null
        var playlistDrawable: Drawable? = null
        var albumDrawable: Drawable? = null
        var playbackState: PlaybackState? = null
        var buttonPadding = 0
        var crossStrokeWidth = 0f
        var progressStrokeWidth = 0f
        var shadowRadius = 0f
        var shadowDx = 0f
        var shadowDy = 0f
        var shadowColor = 0
        var bubblesMinSize = 0f
        var bubblesMaxSize = 0f
        var crossColor = 0
        var crossOverlappedColor = 0
        var accDecInterpolator: Interpolator? = null
        var prevNextExtraPadding = 0

        fun context(context: Context): Builder {
            this.context = context
            return this
        }

        fun playColor(@ColorInt pauseColor: Int): Builder {
            lightColor = pauseColor
            return this
        }

        fun darkColor(@ColorInt playColor: Int): Builder {
            darkColor = playColor
            return this
        }

        fun progressColor(@ColorInt progressColor: Int): Builder {
            this.progressColor = progressColor
            return this
        }

        fun expandedColor(@ColorInt expandedColor: Int): Builder {
            this.expandedColor = expandedColor
            return this
        }

        fun random(random: Random): Builder {
            this.random = random
            return this
        }

        fun widgetWidth(width: Float): Builder {
            this.width = width
            return this
        }

        fun radius(radius: Float): Builder {
            this.radius = radius
            return this
        }

        fun playDrawable(playDrawable: Drawable): Builder {
            this.playDrawable = playDrawable
            return this
        }

        fun pauseDrawable(pauseDrawable: Drawable): Builder {
            this.pauseDrawable = pauseDrawable
            return this
        }

        fun prevDrawable(prevDrawable: Drawable): Builder {
            this.prevDrawable = prevDrawable
            return this
        }

        fun nextDrawable(nextDrawable: Drawable): Builder {
            this.nextDrawable = nextDrawable
            return this
        }

        fun playlistDrawable(plateDrawable: Drawable): Builder {
            playlistDrawable = plateDrawable
            return this
        }

        fun albumDrawable(albumDrawable: Drawable): Builder {
            this.albumDrawable = albumDrawable
            return this
        }

        fun playbackState(playbackState: PlaybackState): Builder {
            this.playbackState = playbackState
            return this
        }

        fun buttonPadding(buttonPadding: Int): Builder {
            this.buttonPadding = buttonPadding
            return this
        }

        fun crossStrokeWidth(crossStrokeWidth: Float): Builder {
            this.crossStrokeWidth = crossStrokeWidth
            return this
        }

        fun progressStrokeWidth(progressStrokeWidth: Float): Builder {
            this.progressStrokeWidth = progressStrokeWidth
            return this
        }

        fun shadowRadius(shadowRadius: Float): Builder {
            this.shadowRadius = shadowRadius
            return this
        }

        fun shadowDx(shadowDx: Float): Builder {
            this.shadowDx = shadowDx
            return this
        }

        fun shadowDy(shadowDy: Float): Builder {
            this.shadowDy = shadowDy
            return this
        }

        fun shadowColor(@ColorInt shadowColor: Int): Builder {
            this.shadowColor = shadowColor
            return this
        }

        fun bubblesMinSize(bubblesMinSize: Float): Builder {
            this.bubblesMinSize = bubblesMinSize
            return this
        }

        fun bubblesMaxSize(bubblesMaxSize: Float): Builder {
            this.bubblesMaxSize = bubblesMaxSize
            return this
        }

        fun crossColor(@ColorInt crossColor: Int): Builder {
            this.crossColor = crossColor
            return this
        }

        fun crossOverlappedColor(@ColorInt crossOverlappedColor: Int): Builder {
            this.crossOverlappedColor = crossOverlappedColor
            return this
        }

        fun accDecInterpolator(accDecInterpolator: Interpolator): Builder {
            this.accDecInterpolator = accDecInterpolator
            return this
        }

        fun prevNextExtraPadding(prevNextExtraPadding: Int): Builder {
            this.prevNextExtraPadding = prevNextExtraPadding
            return this
        }

        fun build(): Configuration {
            return Configuration(this)
        }
    }

    companion object {
        const val FRAME_SPEED = 70.0f

        @JvmField
        val LONG_CLICK_THRESHOLD = ViewConfiguration.getLongPressTimeout() + 128.toLong()
        const val STATE_STOPPED = 0
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2
        const val TOUCH_ANIMATION_DURATION: Long = 100
    }
}