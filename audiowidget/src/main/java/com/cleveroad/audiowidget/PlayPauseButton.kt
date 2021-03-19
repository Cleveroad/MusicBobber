package com.cleveroad.audiowidget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.AsyncTask
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import androidx.palette.graphics.Palette
import com.cleveroad.audiowidget.AudioWidget.BoundsCheckerWithOffset
import com.cleveroad.audiowidget.DrawableUtils.between
import com.cleveroad.audiowidget.DrawableUtils.customFunction
import com.cleveroad.audiowidget.DrawableUtils.isBetween
import com.cleveroad.audiowidget.DrawableUtils.normalize
import com.cleveroad.audiowidget.DrawableUtils.rotateX
import com.cleveroad.audiowidget.DrawableUtils.rotateY
import com.cleveroad.audiowidget.PlaybackState.PlaybackStateListener
import com.cleveroad.audiowidget.TouchManager.BoundsChecker
import java.util.*

/**
 * Collapsed state view.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
internal class PlayPauseButton(
    configuration: Configuration
) : ImageView(configuration.context), PlaybackStateListener {
    private val albumPlaceholderPaint: Paint = Paint()
    private val buttonPaint: Paint = Paint()
    private val bubblesPaint: Paint = Paint()
    private val progressPaint: Paint = Paint()
    private val pausedColor: Int
    private val playingColor: Int
    private val bubbleSizes: FloatArray
    private val bubbleSpeeds: FloatArray
    private val bubbleSpeedCoefficients: FloatArray
    private val random: Random
    private val colorChanger: ColorChanger
    private val playDrawable: Drawable
    private val pauseDrawable: Drawable
    private val bounds: RectF = RectF()
    private val radius: Float
    private val playbackState: PlaybackState
    private val touchDownAnimator: ValueAnimator
    private val touchUpAnimator: ValueAnimator
    private val bubblesAnimator: ValueAnimator
    private val progressAnimator: ValueAnimator = ValueAnimator()
    private val buttonPadding: Int
    private val bubblesMinSize: Float
    private val bubblesMaxSize: Float
    private val isNeedToFillAlbumCoverMap: MutableMap<Int, Boolean> = HashMap()
    var isAnimationInProgress = false
    private var randomStartAngle = 0f
    private var buttonSize = 1.0f
    private var progress = 0.0f
    private var animatedProgress = 0f
    private var progressChangesEnabled = false
    private var albumCover: Drawable? = null
    private var lastPaletteAsyncTask: AsyncTask<*, *, *>? = null
    private val hsvArray = FloatArray(3)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.makeMeasureSpec((radius * 4).toInt(), MeasureSpec.EXACTLY)
        super.onMeasure(size, size)
    }

    private fun updateBubblesPosition(position: Long, fraction: Float) {
        val alpha =
            customFunction(fraction, 0f, 0f, 0f, 0.3f, 255f, 0.5f, 225f, 0.7f, 0f, 1f).toInt()
        bubblesPaint.alpha = alpha
        if (isBetween(
                position.toFloat(),
                COLOR_ANIMATION_TIME_START_F,
                COLOR_ANIMATION_TIME_END_F
            )
        ) {
            val colorDt = normalize(
                position.toFloat(),
                COLOR_ANIMATION_TIME_START_F,
                COLOR_ANIMATION_TIME_END_F
            )
            buttonPaint.color = colorChanger.nextColor(colorDt)
            if (playbackState.state() == Configuration.STATE_PLAYING) {
                pauseDrawable.alpha = between(255 * colorDt, 0f, 255f).toInt()
                playDrawable.alpha = between(255 * (1 - colorDt), 0f, 255f).toInt()
            } else {
                playDrawable.alpha = between(255 * colorDt, 0f, 255f).toInt()
                pauseDrawable.alpha = between(255 * (1 - colorDt), 0f, 255f).toInt()
            }
        }
        for (i in 0 until TOTAL_BUBBLES_COUNT) {
            bubbleSpeeds[i] = fraction * bubbleSpeedCoefficients[i]
        }
    }

    fun onClick() {
        if (isAnimationInProgress) {
            return
        }
        if (playbackState.state() == Configuration.STATE_PLAYING) {
            colorChanger
                .fromColor(playingColor)
                .toColor(pausedColor)
            bubblesPaint.color = pausedColor
        } else {
            colorChanger
                .fromColor(pausedColor)
                .toColor(playingColor)
            bubblesPaint.color = playingColor
        }
        startBubblesAnimation()
    }

    private fun startBubblesAnimation() {
        randomStartAngle = 360 * random.nextFloat()
        for (i in 0 until TOTAL_BUBBLES_COUNT) {
            val speed = 0.5f + 0.5f * random.nextFloat()
            val size = bubblesMinSize + (bubblesMaxSize - bubblesMinSize) * random.nextFloat()
            val radius = size / 2f
            bubbleSizes[i] = radius
            bubbleSpeedCoefficients[i] = speed
        }
        bubblesAnimator.start()
    }

    fun onTouchDown() {
        touchDownAnimator.start()
    }

    fun onTouchUp() {
        touchUpAnimator.start()
    }

    public override fun onDraw(canvas: Canvas) {
        val cx = (width shr 1.toFloat().toInt()).toFloat()
        val cy = (height shr 1.toFloat().toInt()).toFloat()
        canvas.scale(buttonSize, buttonSize, cx, cy)
        if (isAnimationInProgress) {
            for (i in 0 until TOTAL_BUBBLES_COUNT) {
                val angle = randomStartAngle + BUBBLES_ANGLE_STEP * i
                val speed = bubbleSpeeds[i]
                val x = rotateX(cx, cy * (1 - speed), cx, cy, angle)
                val y = rotateY(cx, cy * (1 - speed), cx, cy, angle)
                canvas.drawCircle(x, y, bubbleSizes[i], bubblesPaint)
            }
        } else if (playbackState.state() != Configuration.STATE_PLAYING) {
            playDrawable.alpha = 255
            pauseDrawable.alpha = 0
            // in case widget was drawn without animation in different state
            if (buttonPaint.color != pausedColor) {
                buttonPaint.color = pausedColor
            }
        } else {
            playDrawable.alpha = 0
            pauseDrawable.alpha = 255
            // in case widget was drawn without animation in different state
            if (buttonPaint.color != playingColor) {
                buttonPaint.color = playingColor
            }
        }
        canvas.drawCircle(cx, cy, radius, buttonPaint)
        if (albumCover != null) {
            canvas.drawCircle(cx, cy, radius, buttonPaint)
            albumCover!!.setBounds(
                (cx - radius).toInt(),
                (cy - radius).toInt(),
                (cx + radius).toInt(),
                (cy + radius).toInt()
            )
            albumCover!!.draw(canvas)
            val isNeedToFillAlbumCover = isNeedToFillAlbumCoverMap[albumCover.hashCode()]
            if (isNeedToFillAlbumCover != null && isNeedToFillAlbumCover) {
                canvas.drawCircle(cx, cy, radius, albumPlaceholderPaint)
            }
        }
        val padding = progressPaint.strokeWidth / 2f
        bounds[cx - radius + padding, cy - radius + padding, cx + radius - padding] =
            cy + radius - padding
        canvas.drawArc(bounds, -90f, animatedProgress, false, progressPaint)
        val l = (cx - radius + buttonPadding).toInt()
        val t = (cy - radius + buttonPadding).toInt()
        val r = (cx + radius - buttonPadding).toInt()
        val b = (cy + radius - buttonPadding).toInt()
        if (isAnimationInProgress || playbackState.state() != Configuration.STATE_PLAYING) {
            playDrawable.setBounds(l, t, r, b)
            playDrawable.draw(canvas)
        }
        if (isAnimationInProgress || playbackState.state() == Configuration.STATE_PLAYING) {
            pauseDrawable.setBounds(l, t, r, b)
            pauseDrawable.draw(canvas)
        }
    }

    override fun onStateChanged(
        oldState: Int,
        newState: Int,
        initiator: Any?
    ) {
        if (initiator is AudioWidget) return
        if (newState == Configuration.STATE_PLAYING) {
            buttonPaint.color = playingColor
            pauseDrawable.alpha = 255
            playDrawable.alpha = 0
        } else {
            buttonPaint.color = pausedColor
            pauseDrawable.alpha = 0
            playDrawable.alpha = 255
        }
        postInvalidate()
    }

    override fun onProgressChanged(
        position: Int,
        duration: Int,
        percentage: Float
    ) {
        if (percentage > progress) {
            val old = progress
            post {
                if (animateProgressChanges(old * 360, percentage * 360, PROGRESS_STEP_DURATION)) {
                    progress = percentage
                }
            }
        } else {
            progress = percentage
            animatedProgress = percentage * 360
            postInvalidate()
        }
    }

    fun enableProgressChanges(enable: Boolean) {
        if (progressChangesEnabled == enable) return
        progressChangesEnabled = enable
        if (progressChangesEnabled) {
            animateProgressChangesForce(0f, progress * 360, PROGRESS_CHANGES_DURATION)
        } else {
            animateProgressChangesForce(progress * 360, 0f, PROGRESS_CHANGES_DURATION)
        }
    }

    private fun animateProgressChangesForce(
        oldValue: Float,
        newValue: Float,
        duration: Long
    ) {
        if (progressAnimator.isRunning) {
            progressAnimator.cancel()
        }
        animateProgressChanges(oldValue, newValue, duration)
    }

    private fun animateProgressChanges(
        oldValue: Float,
        newValue: Float,
        duration: Long
    ): Boolean {
        if (progressAnimator.isRunning) {
            return false
        }
        progressAnimator.setFloatValues(oldValue, newValue)
        progressAnimator.duration = duration
        progressAnimator.start()
        return true
    }

    fun newBoundsChecker(offsetX: Int, offsetY: Int): BoundsChecker {
        return BoundsCheckerImpl(radius, offsetX, offsetY)
    }

    fun albumCover(newAlbumCover: Drawable?) {
        if (albumCover === newAlbumCover) return
        albumCover = newAlbumCover
        if (albumCover is BitmapDrawable && !isNeedToFillAlbumCoverMap.containsKey(albumCover.hashCode())) {
            val bitmap = (albumCover as BitmapDrawable).bitmap
            if (bitmap != null && !bitmap.isRecycled) {
                if (lastPaletteAsyncTask != null && !lastPaletteAsyncTask!!.isCancelled) {
                    lastPaletteAsyncTask!!.cancel(true)
                }
                lastPaletteAsyncTask = Palette.from(bitmap).generate { palette: Palette? ->
                    val dominantColor = palette!!.getDominantColor(Int.MAX_VALUE)
                    if (dominantColor != Int.MAX_VALUE) {
                        Color.colorToHSV(dominantColor, hsvArray)
                        isNeedToFillAlbumCoverMap[albumCover.hashCode()] = hsvArray[2] > 0.65f
                        postInvalidate()
                    }
                }
            }
        }
        postInvalidate()
    }

    private class BoundsCheckerImpl(
        private val radius: Float,
        offsetX: Int,
        offsetY: Int
    ) : BoundsCheckerWithOffset(offsetX, offsetY) {
        public override fun stickyLeftSideImpl(screenWidth: Float): Float {
            return -radius
        }

        public override fun stickyRightSideImpl(screenWidth: Float): Float {
            return screenWidth - radius * 3
        }

        public override fun stickyBottomSideImpl(screenHeight: Float): Float {
            return screenHeight - radius * 3
        }

        public override fun stickyTopSideImpl(screenHeight: Float): Float {
            return -radius
        }
    }

    companion object {
        private const val BUBBLES_ANGLE_STEP = 18.0f
        private const val ANIMATION_TIME_F = 8 * Configuration.FRAME_SPEED
        private const val ANIMATION_TIME_L = ANIMATION_TIME_F.toLong()
        private const val COLOR_ANIMATION_TIME_F = ANIMATION_TIME_F / 4f
        private const val COLOR_ANIMATION_TIME_START_F =
            (ANIMATION_TIME_F - COLOR_ANIMATION_TIME_F) / 2
        private const val COLOR_ANIMATION_TIME_END_F =
            COLOR_ANIMATION_TIME_START_F + COLOR_ANIMATION_TIME_F
        private const val TOTAL_BUBBLES_COUNT = (360 / BUBBLES_ANGLE_STEP).toInt()
        const val PROGRESS_CHANGES_DURATION = (6 * Configuration.FRAME_SPEED).toLong()
        private const val PROGRESS_STEP_DURATION = (3 * Configuration.FRAME_SPEED).toLong()
        private const val ALBUM_COVER_PLACEHOLDER_ALPHA = 100
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        playbackState = configuration.playbackState
        random = configuration.random
        buttonPaint.color = configuration.lightColor
        buttonPaint.style = Paint.Style.FILL
        buttonPaint.isAntiAlias = true
        buttonPaint.setShadowLayer(
            configuration.shadowRadius,
            configuration.shadowDx,
            configuration.shadowDy,
            configuration.shadowColor
        )
        bubblesMinSize = configuration.bubblesMinSize
        bubblesMaxSize = configuration.bubblesMaxSize
        bubblesPaint.style = Paint.Style.FILL
        progressPaint.isAntiAlias = true
        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeWidth = configuration.progressStrokeWidth
        progressPaint.color = configuration.progressColor
        albumPlaceholderPaint.style = Paint.Style.FILL
        albumPlaceholderPaint.color = configuration.lightColor
        albumPlaceholderPaint.isAntiAlias = true
        albumPlaceholderPaint.alpha = ALBUM_COVER_PLACEHOLDER_ALPHA
        pausedColor = configuration.lightColor
        playingColor = configuration.darkColor
        radius = configuration.radius
        buttonPadding = configuration.buttonPadding
        bubbleSizes = FloatArray(TOTAL_BUBBLES_COUNT)
        bubbleSpeeds = FloatArray(TOTAL_BUBBLES_COUNT)
        bubbleSpeedCoefficients = FloatArray(TOTAL_BUBBLES_COUNT)
        colorChanger = ColorChanger()
        playDrawable = configuration.playDrawable.constantState!!.newDrawable().mutate()
        pauseDrawable = configuration.pauseDrawable.constantState!!.newDrawable().mutate()
        pauseDrawable.alpha = 0
        playbackState.addPlaybackStateListener(this)
        val listener = AnimatorUpdateListener { animation: ValueAnimator ->
            buttonSize = animation.animatedValue as Float
            invalidate()
        }
        touchDownAnimator =
            ValueAnimator.ofFloat(1f, 0.9f).setDuration(Configuration.TOUCH_ANIMATION_DURATION)
        touchDownAnimator.addUpdateListener(listener)
        touchUpAnimator =
            ValueAnimator.ofFloat(0.9f, 1f).setDuration(Configuration.TOUCH_ANIMATION_DURATION)
        touchUpAnimator.addUpdateListener(listener)
        bubblesAnimator =
            ValueAnimator.ofInt(0, ANIMATION_TIME_L.toInt()).setDuration(ANIMATION_TIME_L)
        bubblesAnimator.interpolator = LinearInterpolator()
        bubblesAnimator.addUpdateListener { animation: ValueAnimator ->
            val position = animation.currentPlayTime
            val fraction = animation.animatedFraction
            updateBubblesPosition(position, fraction)
            invalidate()
        }
        bubblesAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                isAnimationInProgress = true
            }

            override fun onAnimationEnd(animation: Animator) {
                isAnimationInProgress = false
            }

            override fun onAnimationCancel(animation: Animator) {
                isAnimationInProgress = false
            }
        })
        progressAnimator.addUpdateListener { animation: ValueAnimator ->
            animatedProgress = animation.animatedValue as Float
            invalidate()
        }
    }
}