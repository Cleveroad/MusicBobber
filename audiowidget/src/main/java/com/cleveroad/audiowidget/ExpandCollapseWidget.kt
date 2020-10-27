package com.cleveroad.audiowidget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import com.cleveroad.audiowidget.AudioWidget.*
import com.cleveroad.audiowidget.DrawableUtils.between
import com.cleveroad.audiowidget.DrawableUtils.customFunction
import com.cleveroad.audiowidget.DrawableUtils.enlarge
import com.cleveroad.audiowidget.DrawableUtils.isBetween
import com.cleveroad.audiowidget.DrawableUtils.normalize
import com.cleveroad.audiowidget.DrawableUtils.reduce
import com.cleveroad.audiowidget.PlaybackState.PlaybackStateListener
import com.cleveroad.audiowidget.TouchManager.BoundsChecker
import java.util.*

/**
 * Expanded state view.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
internal class ExpandCollapseWidget(configuration: Configuration) :
    ImageView(configuration.context), PlaybackStateListener {
    private val paint: Paint
    private val radius: Float
    private val widgetWidth: Float
    private val widgetHeight: Float
    private val colorChanger: ColorChanger
    private val playColor: Int
    private val pauseColor: Int
    private val widgetColor: Int
    private val drawables: Array<Drawable?>
    private val buttonBounds: Array<Rect?>
    private val sizeStep: Float
    private val bubbleSizes: FloatArray
    private val bubbleSpeeds: FloatArray
    private val bubblePositions: FloatArray
    private val bubblesMinSize: Float
    private val bubblesMaxSize: Float
    private val random: Random
    private val bubblesPaint: Paint
    private val bounds: RectF
    private val tmpRect: Rect
    private val playbackState: PlaybackState
    private val expandAnimator: ValueAnimator
    private val collapseAnimator: ValueAnimator
    private val defaultAlbumCover: Drawable
    private val buttonPadding: Int
    private val prevNextExtraPadding: Int
    private val accDecInterpolator: Interpolator
    private val touchDownAnimator: ValueAnimator
    private val touchUpAnimator: ValueAnimator
    private val bubblesTouchAnimator: ValueAnimator
    private var bubblesTime = 0f
    private var expanded = false
    private var animatingExpand = false
    private var animatingCollapse = false
    private var expandDirection = 0
    private var onWidgetStateChangedListener: OnWidgetStateChangedListener? = null
    private val padding: Int
    private var onControlsClickListener: OnControlsClickListener? = null
    private var touchedButtonIndex = 0

    var expandListener: AnimationProgressListener? = null
    private var collapseListener: AnimationProgressListener? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.makeMeasureSpec(widgetWidth.toInt() + padding * 2, MeasureSpec.EXACTLY)
        val h = MeasureSpec.makeMeasureSpec(
            (widgetHeight * 2).toInt() + padding * 2,
            MeasureSpec.EXACTLY
        )
        super.onMeasure(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (bubblesTime >= 0) {
            val half = TOTAL_BUBBLES_COUNT / 2
            for (i in 0 until TOTAL_BUBBLES_COUNT) {
                val radius = bubbleSizes[i]
                val speed = bubbleSpeeds[i] * bubblesTime
                val cx = bubblePositions[2 * i]
                var cy = bubblePositions[2 * i + 1]
                cy *= if (i < half) 1 - speed else 1 + speed
                canvas.drawCircle(cx, cy, radius, bubblesPaint)
            }
        }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        drawMediaButtons(canvas)
    }

    private fun drawMediaButtons(canvas: Canvas) {
        for (i in buttonBounds.indices) {
            val drawable: Drawable? = if (i == INDEX_PLAY) {
                if (playbackState.state() == Configuration.STATE_PLAYING) {
                    drawables[INDEX_PAUSE]
                } else {
                    drawables[INDEX_PLAY]
                }
            } else {
                drawables[i]
            }
            drawable!!.bounds = buttonBounds[i]!!
            drawable.draw(canvas)
        }
    }

    private fun updateExpandAnimation(position: Long) {
        if (isBetween(position.toFloat(), 0f, EXPAND_COLOR_END_F)) {
            val t = normalize(position.toFloat(), 0f, EXPAND_COLOR_END_F)
            paint.color = colorChanger.nextColor(t)
        }
        if (isBetween(position.toFloat(), 0f, EXPAND_SIZE_END_F)) {
            var time = normalize(position.toFloat(), 0f, EXPAND_SIZE_END_F)
            time = accDecInterpolator.getInterpolation(time)
            val l: Float
            val r: Float
            val b: Float
            val height = radius * 2
            val t: Float = radius
            b = t + height
            if (expandDirection == DIRECTION_LEFT) {
                r = widgetWidth
                l = r - height - (widgetWidth - height) * time
            } else {
                l = 0f
                r = l + height + (widgetWidth - height) * time
            }
            bounds[l, t, r] = b
        } else if (position > EXPAND_SIZE_END_F) {
            if (expandDirection == DIRECTION_LEFT) {
                bounds.left = 0f
            } else {
                bounds.right = widgetWidth
            }
        }
        if (isBetween(position.toFloat(), 0f, EXPAND_POSITION_START_F)) {
            if (expandDirection == DIRECTION_LEFT) {
                calculateBounds(INDEX_ALBUM, buttonBounds[INDEX_PLAY])
            } else {
                calculateBounds(INDEX_PLAYLIST, buttonBounds[INDEX_PLAY])
            }
        }
        if (isBetween(position.toFloat(), 0f, EXPAND_ELEMENTS_START_F)) {
            for (i in buttonBounds.indices) {
                if (i != INDEX_PLAY) {
                    drawables[i]!!.alpha = 0
                }
            }
        }
        if (isBetween(position.toFloat(), EXPAND_ELEMENTS_START_F, EXPAND_ELEMENTS_END_F)) {
            val time = normalize(position.toFloat(), EXPAND_ELEMENTS_START_F, EXPAND_ELEMENTS_END_F)
            expandCollapseElements(time)
        }
        if (isBetween(position.toFloat(), EXPAND_POSITION_START_F, EXPAND_POSITION_END_F)) {
            var time = normalize(position.toFloat(), EXPAND_POSITION_START_F, EXPAND_POSITION_END_F)
            time = accDecInterpolator.getInterpolation(time)
            val playBounds = buttonBounds[INDEX_PLAY]
            calculateBounds(INDEX_PLAY, playBounds)
            val l: Int
            val t: Int
            val r: Int
            val b: Int
            t = playBounds!!.top
            b = playBounds.bottom
            if (expandDirection == DIRECTION_LEFT) {
                calculateBounds(INDEX_ALBUM, tmpRect)
                l = reduce(tmpRect.left.toFloat(), playBounds.left.toFloat(), time).toInt()
                r = l + playBounds.width()
            } else {
                calculateBounds(INDEX_PLAYLIST, tmpRect)
                l = enlarge(tmpRect.left.toFloat(), playBounds.left.toFloat(), time).toInt()
                r = l + playBounds.width()
            }
            playBounds[l, t, r] = b
        } else if (position >= EXPAND_POSITION_END_F) {
            calculateBounds(INDEX_PLAY, buttonBounds[INDEX_PLAY])
        }
        if (isBetween(position.toFloat(), EXPAND_BUBBLES_START_F, EXPAND_BUBBLES_END_F)) {
            val time = normalize(position.toFloat(), EXPAND_BUBBLES_START_F, EXPAND_BUBBLES_END_F)
            bubblesPaint.alpha =
                customFunction(time, 0f, 0f, 255f, 0.33f, 255f, 0.66f, 0f, 1f).toInt()
        } else {
            bubblesPaint.alpha = 0
        }
        if (isBetween(position.toFloat(), EXPAND_BUBBLES_START_F, EXPAND_BUBBLES_END_F)) {
            bubblesTime =
                normalize(position.toFloat(), EXPAND_BUBBLES_START_F, EXPAND_BUBBLES_END_F)
        }
    }

    private fun calculateBounds(index: Int, bounds: Rect?) {
        var padding = buttonPadding
        if (index == INDEX_PREV || index == INDEX_NEXT) {
            padding += prevNextExtraPadding
        }
        calculateBounds(index, bounds, padding)
    }

    private fun calculateBounds(index: Int, bounds: Rect?, padding: Int) {
        val l = (index * sizeStep + padding).toInt()
        val t = (radius + padding).toInt()
        val r = ((index + 1) * sizeStep - padding).toInt()
        val b = (radius * 3 - padding).toInt()
        bounds!![l, t, r] = b
    }

    private fun updateCollapseAnimation(position: Long) {
        if (isBetween(position.toFloat(), 0f, COLLAPSE_ELEMENTS_END_F)) {
            val time = 1 - normalize(position.toFloat(), 0f, COLLAPSE_ELEMENTS_END_F)
            expandCollapseElements(time)
        }
        if (position > COLLAPSE_ELEMENTS_END_F) {
            for (i in buttonBounds.indices) {
                if (i != INDEX_PLAY) {
                    drawables[i]!!.alpha = 0
                }
            }
        }
        if (isBetween(position.toFloat(), COLLAPSE_POSITION_START_F, COLLAPSE_POSITION_END_F)) {
            var time =
                normalize(position.toFloat(), COLLAPSE_POSITION_START_F, COLLAPSE_POSITION_END_F)
            time = accDecInterpolator.getInterpolation(time)
            val playBounds = buttonBounds[INDEX_PLAY]
            calculateBounds(INDEX_PLAY, playBounds)
            val l: Int
            val t: Int
            val r: Int
            val b: Int
            t = playBounds!!.top
            b = playBounds.bottom
            if (expandDirection == DIRECTION_LEFT) {
                calculateBounds(INDEX_ALBUM, tmpRect)
                l = enlarge(playBounds.left.toFloat(), tmpRect.left.toFloat(), time).toInt()
                r = l + playBounds.width()
            } else {
                calculateBounds(INDEX_PLAYLIST, tmpRect)
                l = reduce(playBounds.left.toFloat(), tmpRect.left.toFloat(), time).toInt()
                r = l + playBounds.width()
            }
            buttonBounds[INDEX_PLAY]!![l, t, r] = b
        }
        if (isBetween(position.toFloat(), COLLAPSE_SIZE_START_F, COLLAPSE_SIZE_END_F)) {
            var time = normalize(position.toFloat(), COLLAPSE_SIZE_START_F, COLLAPSE_SIZE_END_F)
            time = accDecInterpolator.getInterpolation(time)
            paint.color = colorChanger.nextColor(time)
            val l: Float
            val r: Float
            val b: Float
            val height = radius * 2
            val t: Float = radius
            b = t + height
            if (expandDirection == DIRECTION_LEFT) {
                r = widgetWidth
                l = r - height - (widgetWidth - height) * (1 - time)
            } else {
                l = 0f
                r = l + height + (widgetWidth - height) * (1 - time)
            }
            bounds[l, t, r] = b
        }
    }

    private fun expandCollapseElements(time: Float) {
        val alpha = between(time * 255, 0f, 255f).toInt()
        for (i in buttonBounds.indices) {
            if (i != INDEX_PLAY) {
                var padding = buttonPadding
                if (i == INDEX_PREV || i == INDEX_NEXT) {
                    padding += prevNextExtraPadding
                }
                calculateBounds(i, buttonBounds[i])
                val size = time * (sizeStep / 2f - padding)
                val cx = buttonBounds[i]!!.centerX()
                val cy = buttonBounds[i]!!.centerY()
                buttonBounds[i]!![(cx - size).toInt(), (cy - size).toInt(), (cx + size).toInt()] =
                    (cy + size).toInt()
                drawables[i]!!.alpha = alpha
            }
        }
    }

    fun onClick(x: Float, y: Float) {
        if (isAnimationInProgress) return
        val index = getTouchedAreaIndex(x.toInt(), y.toInt())
        if (index == INDEX_PLAY || index == INDEX_PREV || index == INDEX_NEXT) {
            if (!bubblesTouchAnimator.isRunning) {
                randomizeBubblesPosition()
                bubblesTouchAnimator.start()
            }
        }
        when (index) {
            INDEX_PLAYLIST -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onPlaylistClicked()
                }
            }
            INDEX_PREV -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onPreviousClicked()
                }
            }
            INDEX_PLAY -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onPlayPauseClicked()
                }
            }
            INDEX_NEXT -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onNextClicked()
                }
            }
            INDEX_ALBUM -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onAlbumClicked()
                }
            }
            else -> {
                Log.w(ExpandCollapseWidget::class.java.simpleName, "Unknown index: $index")
            }
        }
    }

    fun onLongClick(x: Float, y: Float) {
        if (isAnimationInProgress) return
        when (val index = getTouchedAreaIndex(x.toInt(), y.toInt())) {
            INDEX_PLAYLIST -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onPlaylistLongClicked()
                }
            }
            INDEX_PREV -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onPreviousLongClicked()
                }
            }
            INDEX_PLAY -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onPlayPauseLongClicked()
                }
            }
            INDEX_NEXT -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onNextLongClicked()
                }
            }
            INDEX_ALBUM -> {
                if (onControlsClickListener != null) {
                    onControlsClickListener!!.onAlbumLongClicked()
                }
            }
            else -> {
                Log.w(ExpandCollapseWidget::class.java.simpleName, "Unknown index: $index")
            }
        }
    }

    private fun getTouchedAreaIndex(x: Int, y: Int): Int {
        var index = -1
        for (i in buttonBounds.indices) {
            calculateBounds(i, tmpRect, 0)
            if (tmpRect.contains(x, y)) {
                index = i
                break
            }
        }
        return index
    }

    fun expand(expandDirection: Int) {
        if (expanded) {
            return
        }
        this.expandDirection = expandDirection
        startExpandAnimation()
    }

    private fun startExpandAnimation() {
        if (isAnimationInProgress) return
        animatingExpand = true
        if (playbackState.state() == Configuration.STATE_PLAYING) {
            colorChanger
                .fromColor(playColor)
                .toColor(widgetColor)
        } else {
            colorChanger
                .fromColor(pauseColor)
                .toColor(widgetColor)
        }
        randomizeBubblesPosition()
        expandAnimator.start()
    }

    private fun randomizeBubblesPosition() {
        val half = TOTAL_BUBBLES_COUNT / 2
        val step = widgetWidth / half
        for (i in 0 until TOTAL_BUBBLES_COUNT) {
            val index = i % half
            val speed = 0.3f + 0.7f * random.nextFloat()
            val size = bubblesMinSize + (bubblesMaxSize - bubblesMinSize) * random.nextFloat()
            val radius = size / 2f
            val cx =
                padding + index * step + step * random.nextFloat() * if (random.nextBoolean()) 1 else -1
            val cy = widgetHeight + padding
            bubbleSpeeds[i] = speed
            bubbleSizes[i] = radius
            bubblePositions[2 * i] = cx
            bubblePositions[2 * i + 1] = cy
        }
    }

    private fun startCollapseAnimation() {
        if (isAnimationInProgress) {
            return
        }
        collapseAnimator.start()
    }

    val isAnimationInProgress: Boolean
        get() = animatingCollapse || animatingExpand

    fun collapse(): Boolean {
        if (!expanded) {
            return false
        }
        if (playbackState.state() == Configuration.STATE_PLAYING) {
            colorChanger
                .fromColor(widgetColor)
                .toColor(playColor)
        } else {
            colorChanger
                .fromColor(widgetColor)
                .toColor(pauseColor)
        }
        startCollapseAnimation()
        return true
    }

    override fun onStateChanged(oldState: Int, newState: Int, initiator: Any?) {
        invalidate()
    }

    override fun onProgressChanged(position: Int, duration: Int, percentage: Float) {}

    fun onWidgetStateChangedListener(
        onWidgetStateChangedListener: OnWidgetStateChangedListener?
    ): ExpandCollapseWidget {
        this.onWidgetStateChangedListener = onWidgetStateChangedListener
        return this
    }

    fun expandDirection(): Int {
        return expandDirection
    }

    fun expandDirection(expandDirection: Int) {
        this.expandDirection = expandDirection
    }

    fun onControlsClickListener(onControlsClickListener: OnControlsClickListener?) {
        this.onControlsClickListener = onControlsClickListener
    }

    fun albumCover(albumCover: Drawable?) {
        if (drawables[INDEX_ALBUM] === albumCover) return
        if (albumCover == null) {
            drawables[INDEX_ALBUM] = defaultAlbumCover
        } else {
            if (albumCover.constantState != null)
                drawables[INDEX_ALBUM] = albumCover.constantState!!.newDrawable().mutate()
            else
                drawables[INDEX_ALBUM] = albumCover
        }
        invalidate()
    }

    fun onTouched(x: Float, y: Float) {
        val index = getTouchedAreaIndex(x.toInt(), y.toInt())
        if (index == INDEX_PLAY || index == INDEX_NEXT || index == INDEX_PREV) {
            touchedButtonIndex = index
            touchDownAnimator.start()
        }
    }

    fun onReleased(x: Float, y: Float) {
        val index = getTouchedAreaIndex(x.toInt(), y.toInt())
        if (index == INDEX_PLAY || index == INDEX_NEXT || index == INDEX_PREV) {
            touchedButtonIndex = index
            touchUpAnimator.start()
        }
    }

    fun newBoundsChecker(offsetX: Int, offsetY: Int): BoundsChecker {
        return BoundsCheckerImpl(radius, widgetWidth, offsetX, offsetY)
    }

    fun setCollapseListener(collapseListener: AnimationProgressListener?) {
        this.collapseListener = collapseListener
    }

    internal interface AnimationProgressListener {
        fun onValueChanged(percent: Float)
    }

    private class BoundsCheckerImpl(
        private val radius: Float,
        private val widgetWidth: Float,
        offsetX: Int,
        offsetY: Int
    ) : BoundsCheckerWithOffset(offsetX, offsetY) {
        public override fun stickyLeftSideImpl(screenWidth: Float): Float {
            return 0F
        }

        public override fun stickyRightSideImpl(screenWidth: Float): Float {
            return screenWidth - widgetWidth
        }

        public override fun stickyBottomSideImpl(screenHeight: Float): Float {
            return screenHeight - 3 * radius
        }

        public override fun stickyTopSideImpl(screenHeight: Float): Float {
            return -radius
        }
    }

    companion object {
        const val DIRECTION_LEFT = 1
        const val DIRECTION_RIGHT = 2
        private const val EXPAND_DURATION_F = 34 * Configuration.FRAME_SPEED
        private const val EXPAND_DURATION_L = EXPAND_DURATION_F.toLong()
        private const val EXPAND_COLOR_END_F = 9 * Configuration.FRAME_SPEED
        private const val EXPAND_SIZE_END_F = 12 * Configuration.FRAME_SPEED
        private const val EXPAND_POSITION_START_F = 10 * Configuration.FRAME_SPEED
        private const val EXPAND_POSITION_END_F = 18 * Configuration.FRAME_SPEED
        private const val EXPAND_BUBBLES_START_F = 18 * Configuration.FRAME_SPEED
        private const val EXPAND_BUBBLES_END_F = 32 * Configuration.FRAME_SPEED
        private const val EXPAND_ELEMENTS_START_F = 20 * Configuration.FRAME_SPEED
        private const val EXPAND_ELEMENTS_END_F = 27 * Configuration.FRAME_SPEED
        private const val COLLAPSE_DURATION_F = 12 * Configuration.FRAME_SPEED
        private const val COLLAPSE_DURATION_L = COLLAPSE_DURATION_F.toLong()
        private const val COLLAPSE_ELEMENTS_END_F = 3 * Configuration.FRAME_SPEED
        private const val COLLAPSE_SIZE_START_F = 2 * Configuration.FRAME_SPEED
        private const val COLLAPSE_SIZE_END_F = 12 * Configuration.FRAME_SPEED
        private const val COLLAPSE_POSITION_START_F = 3 * Configuration.FRAME_SPEED
        private const val COLLAPSE_POSITION_END_F = 12 * Configuration.FRAME_SPEED
        private const val INDEX_PLAYLIST = 0
        private const val INDEX_PREV = 1
        private const val INDEX_PLAY = 2
        private const val INDEX_NEXT = 3
        private const val INDEX_ALBUM = 4
        private const val INDEX_PAUSE = 5
        private const val TOTAL_BUBBLES_COUNT = 30
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        playbackState = configuration.playbackState
        accDecInterpolator = configuration.accDecInterpolator
        random = configuration.random
        bubblesPaint = Paint()
        bubblesPaint.style = Paint.Style.FILL
        bubblesPaint.isAntiAlias = true
        bubblesPaint.color = configuration.expandedColor
        bubblesPaint.alpha = 0
        paint = Paint()
        paint.color = configuration.expandedColor
        paint.isAntiAlias = true
        paint.setShadowLayer(
            configuration.shadowRadius,
            configuration.shadowDx,
            configuration.shadowDy,
            configuration.shadowColor
        )
        radius = configuration.radius
        widgetWidth = configuration.widgetWidth
        colorChanger = ColorChanger()
        playColor = configuration.darkColor
        pauseColor = configuration.lightColor
        widgetColor = configuration.expandedColor
        buttonPadding = configuration.buttonPadding
        prevNextExtraPadding = configuration.prevNextExtraPadding
        bubblesMinSize = configuration.bubblesMinSize
        bubblesMaxSize = configuration.bubblesMaxSize
        tmpRect = Rect()
        buttonBounds = arrayOfNulls(5)
        drawables = arrayOfNulls(6)
        bounds = RectF()
        drawables[INDEX_PLAYLIST] =
            configuration.playlistDrawable.constantState!!.newDrawable().mutate()
        drawables[INDEX_PREV] = configuration.prevDrawable.constantState!!.newDrawable().mutate()
        drawables[INDEX_PLAY] = configuration.playDrawable.constantState!!.newDrawable().mutate()
        drawables[INDEX_PAUSE] = configuration.pauseDrawable.constantState!!.newDrawable().mutate()
        drawables[INDEX_NEXT] = configuration.nextDrawable.constantState!!.newDrawable().mutate()
        defaultAlbumCover = configuration.albumDrawable.constantState!!.newDrawable().mutate()
        drawables[INDEX_ALBUM] = defaultAlbumCover
        sizeStep = widgetWidth / 5f
        widgetHeight = radius * 2
        for (i in buttonBounds.indices) {
            buttonBounds[i] = Rect()
        }
        bubbleSizes = FloatArray(TOTAL_BUBBLES_COUNT)
        bubbleSpeeds = FloatArray(TOTAL_BUBBLES_COUNT)
        bubblePositions = FloatArray(TOTAL_BUBBLES_COUNT * 2)
        playbackState.addPlaybackStateListener(this)
        expandAnimator = ValueAnimator.ofPropertyValuesHolder(
            PropertyValuesHolder.ofFloat("percent", 0f, 1f),
            PropertyValuesHolder.ofInt("expandPosition", 0, EXPAND_DURATION_L.toInt()),
            PropertyValuesHolder.ofFloat(
                "alpha",
                0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f
            )
        ).setDuration(EXPAND_DURATION_L)
        val interpolator = LinearInterpolator()
        expandAnimator.interpolator = interpolator
        expandAnimator.addUpdateListener { animation: ValueAnimator ->
            updateExpandAnimation((animation.getAnimatedValue("expandPosition") as Int).toLong())
            alpha = animation.getAnimatedValue("alpha") as Float
            invalidate()
            expandListener?.onValueChanged(animation.getAnimatedValue("percent") as Float)
        }
        expandAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                animatingExpand = true
            }

            override fun onAnimationEnd(animation: Animator) {
                animatingExpand = false
                expanded = true
                onWidgetStateChangedListener?.onWidgetStateChanged(State.EXPANDED)
            }

            override fun onAnimationCancel(animation: Animator) {
                animatingExpand = false
            }
        })
        collapseAnimator = ValueAnimator.ofPropertyValuesHolder(
            PropertyValuesHolder.ofFloat("percent", 0f, 1f),
            PropertyValuesHolder.ofInt("expandPosition", 0, COLLAPSE_DURATION_L.toInt()),
            PropertyValuesHolder.ofFloat("alpha", 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f)
        ).setDuration(COLLAPSE_DURATION_L)
        collapseAnimator.interpolator = interpolator
        collapseAnimator.addUpdateListener { animation: ValueAnimator ->
            updateCollapseAnimation(
                (animation.getAnimatedValue("expandPosition") as Int).toLong()
            )
            alpha = animation.getAnimatedValue("alpha") as Float
            invalidate()
            collapseListener?.onValueChanged(animation.getAnimatedValue("percent") as Float)
        }
        collapseAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                animatingCollapse = true
            }

            override fun onAnimationEnd(animation: Animator) {
                animatingCollapse = false
                expanded = false
                onWidgetStateChangedListener?.onWidgetStateChanged(State.COLLAPSED)
            }

            override fun onAnimationCancel(animation: Animator) {
                animatingCollapse = false
            }
        })
        padding =
            configuration.context!!.resources.getDimensionPixelSize(R.dimen.aw_expand_collapse_widget_padding)
        val listener = AnimatorUpdateListener {
            if (touchedButtonIndex == -1 || touchedButtonIndex >= buttonBounds.size) {
                return@AnimatorUpdateListener
            }
            calculateBounds(touchedButtonIndex, tmpRect)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                invalidate()
                return@AnimatorUpdateListener
            }
            invalidate()
        }
        touchDownAnimator =
            ValueAnimator.ofFloat(1f, 0.9f).setDuration(Configuration.TOUCH_ANIMATION_DURATION)
        touchDownAnimator.addUpdateListener(listener)
        touchUpAnimator =
            ValueAnimator.ofFloat(0.9f, 1f).setDuration(Configuration.TOUCH_ANIMATION_DURATION)
        touchUpAnimator.addUpdateListener(listener)
        bubblesTouchAnimator =
            ValueAnimator.ofFloat(0f, EXPAND_BUBBLES_END_F - EXPAND_BUBBLES_START_F)
                .setDuration((EXPAND_BUBBLES_END_F - EXPAND_BUBBLES_START_F).toLong())
        bubblesTouchAnimator.interpolator = interpolator
        bubblesTouchAnimator.addUpdateListener {
            bubblesTime = it.animatedFraction
            bubblesPaint.alpha =
                customFunction(bubblesTime, 0f, 0f, 255f, 0.33f, 255f, 0.66f, 0f, 1f).toInt()
            invalidate()
        }
        bubblesTouchAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                bubblesTime = 0f
            }

            override fun onAnimationCancel(animation: Animator) {
                bubblesTime = 0f
            }
        })
    }
}