package com.cleveroad.audiowidget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Vibrator
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.cleveroad.audiowidget.DrawableUtils.rotateX
import com.cleveroad.audiowidget.DrawableUtils.rotateY
import com.cleveroad.audiowidget.ExpandCollapseWidget.AnimationProgressListener
import com.cleveroad.audiowidget.TouchManager.BoundsChecker
import java.lang.ref.WeakReference
import java.util.*
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Audio widget implementation.
 */
class AudioWidget private constructor(builder: Builder) {
    private val albumCoverCache: MutableMap<Int, WeakReference<Drawable>> = WeakHashMap()
    private val context: Context = builder.context.applicationContext
    private val controller: Controller = newController()
    private val expToPpbBoundsChecker: BoundsChecker
    private val expandCollapseWidget: ExpandCollapseWidget
    private val expandedWidgetManager: TouchManager
    private val handler: Handler = Handler()
    private val hiddenRemWidPos: Point = Point()
    private val onControlsClickListener: OnControlsClickListenerWrapper?
    private val playPauseButton: PlayPauseButton
    private val playPauseButtonManager: TouchManager
    private val ppbToExpBoundsChecker: BoundsChecker
    private val removeBounds: RectF = RectF()
    private val removeWidgetView: RemoveWidgetView
    private val screenSize: Point = Point()
    private val vibrator: Vibrator =
        builder.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val visibleRemWidPos: Point = Point()
    private val windowManager: WindowManager =
        builder.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var animatedRemBtnYPos = -1
    private var onWidgetStateChangedListener: OnWidgetStateChangedListener? = null
    private var playbackState: PlaybackState? = null
    private var released = false
    private var removeWidgetShown = false

    /**
     * Get current visibility state.
     *
     * @return true if widget shown on screen, false otherwise.
     */
    var isShown = false
        private set
    private var widgetWidth = 0f
    private var widgetHeight = 0f
    private var radius = 0f
    fun collapse() {
        expandCollapseWidget.setCollapseListener(object : AnimationProgressListener {
            override fun onValueChanged(alpha: Float) {
                playPauseButton.alpha = alpha
            }
        })
        val params = expandCollapseWidget.layoutParams as WindowManager.LayoutParams
        val cx = params.x + expandCollapseWidget.width / 2
        if (cx > screenSize.x / 2) {
            expandCollapseWidget.expandDirection(ExpandCollapseWidget.DIRECTION_LEFT)
        } else {
            expandCollapseWidget.expandDirection(ExpandCollapseWidget.DIRECTION_RIGHT)
        }
        updatePlayPauseButtonPosition()
        if (expandCollapseWidget.collapse()) {
            playPauseButtonManager.animateToBounds()
            expandedWidgetManager.animateToBounds(expToPpbBoundsChecker, null)
        }
    }

    /**
     * Get widget controller.
     *
     * @return widget controller
     */
    fun controller(): Controller {
        return controller
    }

    fun expand() {
        removeWidgetShown = false
        playPauseButton.enableProgressChanges(false)
        playPauseButton.postDelayed(
            { checkSpaceAndShowExpanded() },
            PlayPauseButton.PROGRESS_CHANGES_DURATION
        )
    }

    /**
     * Hide widget.
     */
    fun hide() {
        hideInternal(true)
    }

    /**
     * Show widget at specified position.
     *
     * @param cx center x
     * @param cy center y
     */
    fun show(cx: Int, cy: Int) {
        if (isShown) {
            return
        }
        isShown = true
        val remWidX = screenSize.x / 2f - radius * RemoveWidgetView.SCALE_LARGE
        hiddenRemWidPos[remWidX.toInt()] =
            (screenSize.y + widgetHeight + navigationBarHeight()).toInt()
        visibleRemWidPos[remWidX.toInt()] =
            (screenSize.y - radius - if (hasNavigationBar()) 0F else widgetHeight).toInt()
        try {
            show(removeWidgetView, hiddenRemWidPos.x, hiddenRemWidPos.y)
        } catch (e: IllegalArgumentException) {
            // widget not removed yet, animation in progress
        }
        show(playPauseButton, (cx - widgetHeight).toInt(), (cy - widgetHeight).toInt())
        playPauseButtonManager.animateToBounds()
    }

    private fun checkSpaceAndShowExpanded() {
        val params = playPauseButton.layoutParams as WindowManager.LayoutParams
        val x = params.x
        val y = params.y
        val expandDirection: Int
        expandDirection = if (x + widgetHeight > screenSize.x / 2) {
            ExpandCollapseWidget.DIRECTION_LEFT
        } else {
            ExpandCollapseWidget.DIRECTION_RIGHT
        }
        playPauseButtonManager.animateToBounds(ppbToExpBoundsChecker) {
            val params1 = playPauseButton.layoutParams as WindowManager.LayoutParams
            var x1 = params1.x
            val y1 = params1.y
            if (expandDirection == ExpandCollapseWidget.DIRECTION_LEFT) {
                x1 -= (widgetWidth - widgetHeight * 1.5f).toInt()
            } else {
                x1 += (widgetHeight / 2f.toInt()).toInt()
            }
            show(expandCollapseWidget, x1, y1)
            playPauseButton.setLayerType(View.LAYER_TYPE_NONE, null)
            expandCollapseWidget.expandListener = object : AnimationProgressListener {
                override fun onValueChanged(percent: Float) {
                    playPauseButton.alpha = 1f - percent
                }
            }
            expandCollapseWidget.expand(expandDirection)
        }
    }

    /**
     * Check if device has navigation bar.
     *
     * @return true if device has navigation bar, false otherwise.
     */
    private fun hasNavigationBar(): Boolean {
        val hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
        val hasHomeKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME)
        val id = context.resources.getIdentifier("config_showNavigationBar", "bool", "android")
        return !hasBackKey && !hasHomeKey || id > 0 && context.resources.getBoolean(id)
    }

    private fun hideInternal(byPublic: Boolean) {
        if (!isShown) {
            return
        }
        isShown = false
        released = true
        try {
            windowManager.removeView(playPauseButton)
        } catch (e: IllegalArgumentException) {
            // view not attached to window
        }
        if (byPublic) {
            try {
                windowManager.removeView(removeWidgetView)
            } catch (e: IllegalArgumentException) {
                // view not attached to window
            }
        }
        try {
            windowManager.removeView(expandCollapseWidget)
        } catch (e: IllegalArgumentException) {
            // widget not added to window yet
        }
        if (onWidgetStateChangedListener != null) {
            onWidgetStateChangedListener!!.onWidgetStateChanged(State.REMOVED)
        }
    }

    /**
     * Get navigation bar height.
     *
     * @return navigation bar height
     */
    private fun navigationBarHeight(): Int {
        if (hasNavigationBar()) {
            val resourceId =
                context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            return if (resourceId > 0) {
                context.resources.getDimensionPixelSize(resourceId)
            } else context.resources.getDimensionPixelSize(R.dimen.aw_navigation_bar_height)
        }
        return 0
    }

    /**
     * Create new controller.
     *
     * @return new controller
     */
    private fun newController(): Controller {
        return object : Controller {
            override fun start() {
                playbackState!!.start(this)
            }

            override fun pause() {
                playbackState!!.pause(this)
            }

            override fun stop() {
                playbackState!!.stop(this)
            }

            override fun duration(): Int {
                return playbackState!!.duration()
            }

            override fun duration(duration: Int) {
                playbackState!!.duration(duration)
            }

            override fun position(): Int {
                return playbackState!!.position()
            }

            override fun position(position: Int) {
                playbackState!!.position(position)
            }

            override fun onControlsClickListener(onControlsClickListener: OnControlsClickListener?) {
                this@AudioWidget.onControlsClickListener!!.onControlsClickListener(
                    onControlsClickListener
                )
            }

            override fun onWidgetStateChangedListener(
                onWidgetStateChangedListener: OnWidgetStateChangedListener?
            ) {
                this@AudioWidget.onWidgetStateChangedListener = onWidgetStateChangedListener
            }

            override fun albumCover(albumCover: Drawable?) {
                expandCollapseWidget.albumCover(albumCover)
                playPauseButton.albumCover(albumCover)
            }

            override fun albumCoverBitmap(bitmap: Bitmap?) {
                if (bitmap == null) {
                    expandCollapseWidget.albumCover(null)
                    playPauseButton.albumCover(null)
                } else {
                    val wrDrawable = albumCoverCache[bitmap.hashCode()]
                    if (wrDrawable != null) {
                        val drawable = wrDrawable.get()
                        if (drawable != null) {
                            expandCollapseWidget.albumCover(drawable)
                            playPauseButton.albumCover(drawable)
                            return
                        }
                    }
                    val albumCover: Drawable = BitmapDrawable(context.resources, bitmap)
                    expandCollapseWidget.albumCover(albumCover)
                    playPauseButton.albumCover(albumCover)
                    albumCoverCache[bitmap.hashCode()] = WeakReference(albumCover)
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun oreoShow(view: View, left: Int, top: Int) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.START or Gravity.TOP
        params.x = left
        params.y = top
        windowManager.addView(view, params)
    }

    private fun preOreoShow(view: View, left: Int, top: Int) {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.START or Gravity.TOP
        params.x = left
        params.y = top
        windowManager.addView(view, params)
    }

    /**
     * Prepare configuration for widget.
     *
     * @param builder user defined settings
     * @return new configuration for widget
     */
    private fun prepareConfiguration(builder: Builder): Configuration {
        val darkColor = if (builder.darkColorSet) builder.darkColor else ContextCompat.getColor(
            context,
            R.color.aw_dark
        )
        val lightColor = if (builder.lightColorSet) builder.lightColor else ContextCompat.getColor(
            context,
            R.color.aw_light
        )
        val progressColor =
            if (builder.progressColorSet) builder.progressColor else ContextCompat.getColor(
                context,
                R.color.aw_progress
            )
        val expandColor =
            if (builder.expandWidgetColorSet) builder.expandWidgetColor else ContextCompat.getColor(
                context,
                R.color.aw_expanded
            )
        val crossColor = if (builder.crossColorSet) builder.crossColor else ContextCompat.getColor(
            context,
            R.color.aw_cross_default
        )
        val crossOverlappedColor =
            if (builder.crossOverlappedColorSet) builder.crossOverlappedColor else ContextCompat.getColor(
                context,
                R.color.aw_cross_overlapped
            )
        val shadowColor =
            if (builder.shadowColorSet) builder.shadowColor else ContextCompat.getColor(
                context,
                R.color.aw_shadow
            )
        val playDrawable =
            if (builder.playDrawable != null) builder.playDrawable else ContextCompat.getDrawable(
                context,
                R.drawable.aw_ic_play
            )
        val pauseDrawable =
            if (builder.pauseDrawable != null) builder.pauseDrawable else ContextCompat.getDrawable(
                context,
                R.drawable.aw_ic_pause
            )
        val prevDrawable =
            if (builder.prevDrawable != null) builder.prevDrawable else ContextCompat.getDrawable(
                context,
                R.drawable.aw_ic_prev
            )
        val nextDrawable =
            if (builder.nextDrawable != null) builder.nextDrawable else ContextCompat.getDrawable(
                context,
                R.drawable.aw_ic_next
            )
        val playlistDrawable =
            if (builder.playlistDrawable != null) builder.playlistDrawable else ContextCompat.getDrawable(
                context,
                R.drawable.aw_ic_playlist
            )
        val albumDrawable =
            if (builder.defaultAlbumDrawable != null) builder.defaultAlbumDrawable else ContextCompat.getDrawable(
                context,
                R.drawable.aw_ic_default_album
            )
        val buttonPadding =
            if (builder.buttonPaddingSet) builder.buttonPadding else context.resources.getDimensionPixelSize(
                R.dimen.aw_button_padding
            )
        val crossStrokeWidth =
            if (builder.crossStrokeWidthSet) builder.crossStrokeWidth else context.resources.getDimension(
                R.dimen.aw_cross_stroke_width
            )
        val progressStrokeWidth =
            if (builder.progressStrokeWidthSet) builder.progressStrokeWidth else context.resources.getDimension(
                R.dimen.aw_progress_stroke_width
            )
        val shadowRadius =
            if (builder.shadowRadiusSet) builder.shadowRadius else context.resources.getDimension(R.dimen.aw_shadow_radius)
        val shadowDx =
            if (builder.shadowDxSet) builder.shadowDx else context.resources.getDimension(R.dimen.aw_shadow_dx)
        val shadowDy =
            if (builder.shadowDySet) builder.shadowDy else context.resources.getDimension(R.dimen.aw_shadow_dy)
        val bubblesMinSize =
            if (builder.bubblesMinSizeSet) builder.bubblesMinSize else context.resources.getDimension(
                R.dimen.aw_bubbles_min_size
            )
        val bubblesMaxSize =
            if (builder.bubblesMaxSizeSet) builder.bubblesMaxSize else context.resources.getDimension(
                R.dimen.aw_bubbles_max_size
            )
        val prevNextExtraPadding =
            context.resources.getDimensionPixelSize(R.dimen.aw_prev_next_button_extra_padding)
        widgetHeight = context.resources.getDimensionPixelSize(R.dimen.aw_player_height).toFloat()
        widgetWidth = context.resources.getDimensionPixelSize(R.dimen.aw_player_width).toFloat()
        radius = widgetHeight / 2f
        playbackState = PlaybackState()
        return Configuration.Builder()
            .context(context)
            .playbackState(playbackState!!)
            .random(Random())
            .accDecInterpolator(AccelerateDecelerateInterpolator())
            .darkColor(darkColor)
            .playColor(lightColor)
            .progressColor(progressColor)
            .expandedColor(expandColor)
            .widgetWidth(widgetWidth)
            .radius(radius)
            .playlistDrawable(playlistDrawable!!)
            .playDrawable(playDrawable!!)
            .prevDrawable(prevDrawable!!)
            .nextDrawable(nextDrawable!!)
            .pauseDrawable(pauseDrawable!!)
            .albumDrawable(albumDrawable!!)
            .buttonPadding(buttonPadding)
            .prevNextExtraPadding(prevNextExtraPadding)
            .crossStrokeWidth(crossStrokeWidth)
            .progressStrokeWidth(progressStrokeWidth)
            .shadowRadius(shadowRadius)
            .shadowDx(shadowDx)
            .shadowDy(shadowDy)
            .shadowColor(shadowColor)
            .bubblesMinSize(bubblesMinSize)
            .bubblesMaxSize(bubblesMaxSize)
            .crossColor(crossColor)
            .crossOverlappedColor(crossOverlappedColor)
            .build()
    }

    private fun show(view: View, left: Int, top: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            preOreoShow(view, left, top)
        } else {
            oreoShow(view, left, top)
        }
    }

    /**
     * Get status bar height.
     *
     * @return status bar height.
     */
    private fun statusBarHeight(): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else context.resources.getDimensionPixelSize(R.dimen.aw_status_bar_height)
    }

    private fun updatePlayPauseButtonPosition() {
        val widgetParams = expandCollapseWidget.layoutParams as WindowManager.LayoutParams
        val params = playPauseButton.layoutParams as WindowManager.LayoutParams
        if (expandCollapseWidget.expandDirection() == ExpandCollapseWidget.DIRECTION_RIGHT) {
            params.x = (widgetParams.x - radius).toInt()
        } else {
            params.x = (widgetParams.x + widgetWidth - widgetHeight - radius).toInt()
        }
        params.y = widgetParams.y
        try {
            windowManager.updateViewLayout(playPauseButton, params)
        } catch (e: IllegalArgumentException) {
            // view not attached to window
        }
        onWidgetStateChangedListener?.onWidgetPositionChanged(
            (params.x + widgetHeight).toInt(),
            (params.y + widgetHeight).toInt()
        )
    }

    /**
     * Widget state.
     */
    enum class State {
        COLLAPSED, EXPANDED, REMOVED
    }

    /**
     * Audio widget controller.
     */
    interface Controller {
        /**
         * Set album cover.
         *
         * @param albumCover album cover or null to set default one
         */
        fun albumCover(albumCover: Drawable?)

        /**
         * Set album cover.
         *
         * @param albumCover album cover or null to set default one
         */
        fun albumCoverBitmap(albumCover: Bitmap?)

        /**
         * Get track duration.
         *
         * @return track duration
         */
        fun duration(): Int

        /**
         * Set track duration.
         *
         * @param duration track duration
         */
        fun duration(duration: Int)

        /**
         * Set controls click listener.
         *
         * @param onControlsClickListener controls click listener
         */
        fun onControlsClickListener(onControlsClickListener: OnControlsClickListener?)

        /**
         * Set widget state change listener.
         *
         * @param onWidgetStateChangedListener widget state change listener
         */
        fun onWidgetStateChangedListener(onWidgetStateChangedListener: OnWidgetStateChangedListener?)

        /**
         * Pause playback.
         */
        fun pause()

        /**
         * Get track position.
         *
         * @return track position
         */
        fun position(): Int

        /**
         * Set track position.
         *
         * @param position track position
         */
        fun position(position: Int)

        /**
         * Start playback.
         */
        fun start()

        /**
         * Stop playback.
         */
        fun stop()
    }

    /**
     * Listener for control clicks.
     */
    interface OnControlsClickListener {
        /**
         * Called when album icon clicked.
         */
        fun onAlbumClicked()

        /**
         * Called when album icon long clicked.
         */
        fun onAlbumLongClicked()

        /**
         * Called when next track button clicked.
         */
        fun onNextClicked()

        /**
         * Called when next track button long clicked.
         */
        fun onNextLongClicked()

        /**
         * Called when play/pause button clicked.
         *
         * @return true if you consume the action, false to use default behavior (change play/pause state)
         */
        fun onPlayPauseClicked(): Boolean

        /**
         * Called when play/pause button long clicked.
         */
        fun onPlayPauseLongClicked()

        /**
         * Called when playlist button clicked.
         *
         * @return true if you consume the action, false to use default behavior (collapse widget)
         */
        fun onPlaylistClicked(): Boolean

        /**
         * Called when playlist button long clicked.
         */
        fun onPlaylistLongClicked()

        /**
         * Called when previous track button clicked.
         */
        fun onPreviousClicked()

        /**
         * Called when previous track button long clicked.
         */
        fun onPreviousLongClicked()
    }

    /**
     * Listener for widget state changes.
     */
    interface OnWidgetStateChangedListener {
        /**
         * Called when position of widget is changed.
         *
         * @param cx center x
         * @param cy center y
         */
        fun onWidgetPositionChanged(cx: Int, cy: Int)

        /**
         * Called when widget state changed.
         *
         * @param state new widget state
         */
        fun onWidgetStateChanged(state: State)
    }

    internal abstract class BoundsCheckerWithOffset(
        private val offsetX: Int,
        private val offsetY: Int
    ) : BoundsChecker {
        override fun stickyLeftSide(screenWidth: Float): Float {
            return stickyLeftSideImpl(screenWidth) + offsetX
        }

        override fun stickyRightSide(screenWidth: Float): Float {
            return stickyRightSideImpl(screenWidth) - offsetX
        }

        override fun stickyTopSide(screenHeight: Float): Float {
            return stickyTopSideImpl(screenHeight) + offsetY
        }

        override fun stickyBottomSide(screenHeight: Float): Float {
            return stickyBottomSideImpl(screenHeight) - offsetY
        }

        protected abstract fun stickyBottomSideImpl(screenHeight: Float): Float
        protected abstract fun stickyLeftSideImpl(screenWidth: Float): Float
        protected abstract fun stickyRightSideImpl(screenWidth: Float): Float
        protected abstract fun stickyTopSideImpl(screenHeight: Float): Float
    }

    /**
     * Builder class for [AudioWidget].
     */
    class Builder(val context: Context) {
        var bubblesMaxSize = 0f
        var bubblesMaxSizeSet = false
        var bubblesMinSize = 0f
        var bubblesMinSizeSet = false
        var buttonPadding = 0
        var buttonPaddingSet = false

        @ColorInt
        var crossColor = 0
        var crossColorSet = false

        @ColorInt
        var crossOverlappedColor = 0
        var crossOverlappedColorSet = false
        var crossStrokeWidth = 0f
        var crossStrokeWidthSet = false

        @ColorInt
        var darkColor = 0
        var darkColorSet = false
        var defaultAlbumDrawable: Drawable? = null
        var edgeOffsetXCollapsed = 0
        var edgeOffsetXCollapsedSet = false
        var edgeOffsetXExpanded = 0
        var edgeOffsetXExpandedSet = false
        var edgeOffsetYCollapsed = 0
        var edgeOffsetYCollapsedSet = false
        var edgeOffsetYExpanded = 0
        var edgeOffsetYExpandedSet = false

        @ColorInt
        var expandWidgetColor = 0
        var expandWidgetColorSet = false

        @ColorInt
        var lightColor = 0
        var lightColorSet = false
        var nextDrawable: Drawable? = null
        var pauseDrawable: Drawable? = null
        var playDrawable: Drawable? = null
        var playlistDrawable: Drawable? = null
        var prevDrawable: Drawable? = null

        @ColorInt
        var progressColor = 0
        var progressColorSet = false
        var progressStrokeWidth = 0f
        var progressStrokeWidthSet = false

        @ColorInt
        var shadowColor = 0
        var shadowColorSet = false
        var shadowDx = 0f
        var shadowDxSet = false
        var shadowDy = 0f
        var shadowDySet = false
        var shadowRadius = 0f
        var shadowRadiusSet = false

        /**
         * Set bubbles maximum size in pixels. Default value: 10dp.
         *
         * @param bubblesMaxSize bubbles maximum size
         */
        fun bubblesMaxSize(bubblesMaxSize: Float): Builder {
            this.bubblesMaxSize = bubblesMaxSize
            bubblesMaxSizeSet = true
            return this
        }

        /**
         * Set bubbles minimum size in pixels. Default value: 5dp.
         *
         * @param bubblesMinSize bubbles minimum size
         */
        fun bubblesMinSize(bubblesMinSize: Float): Builder {
            this.bubblesMinSize = bubblesMinSize
            bubblesMinSizeSet = true
            return this
        }

        /**
         * Create new audio widget.
         *
         * @return new audio widget
         * @throws IllegalStateException if size parameters have wrong values (less than zero).
         */
        fun build(): AudioWidget {
            if (buttonPaddingSet) {
                checkOrThrow(buttonPadding, "Button padding")
            }
            if (shadowRadiusSet) {
                checkOrThrow(shadowRadius, "Shadow radius")
            }
            if (shadowDxSet) {
                checkOrThrow(shadowDx, "Shadow dx")
            }
            if (shadowDySet) {
                checkOrThrow(shadowDy, "Shadow dy")
            }
            if (bubblesMinSizeSet) {
                checkOrThrow(bubblesMinSize, "Bubbles min size")
            }
            if (bubblesMaxSizeSet) {
                checkOrThrow(bubblesMaxSize, "Bubbles max size")
            }
            require(!(bubblesMinSizeSet && bubblesMaxSizeSet && bubblesMaxSize < bubblesMinSize)) {
                "Bubbles max size must be greater than bubbles min size"
            }
            if (crossStrokeWidthSet) {
                checkOrThrow(crossStrokeWidth, "Cross stroke width")
            }
            if (progressStrokeWidthSet) {
                checkOrThrow(progressStrokeWidth, "Progress stroke width")
            }
            return AudioWidget(this)
        }

        /**
         * Set button padding in pixels. Default value: 10dp.
         *
         * @param buttonPadding button padding
         */
        fun buttonPadding(buttonPadding: Int): Builder {
            this.buttonPadding = buttonPadding
            buttonPaddingSet = true
            return this
        }

        /**
         * Set remove widget cross color.
         *
         * @param crossColor cross color
         */
        fun crossColor(@ColorInt crossColor: Int): Builder {
            this.crossColor = crossColor
            crossColorSet = true
            return this
        }

        /**
         * Set remove widget cross color in overlapped state (audio widget overlapped remove widget).
         *
         * @param crossOverlappedColor cross color in overlapped state
         */
        fun crossOverlappedColor(@ColorInt crossOverlappedColor: Int): Builder {
            this.crossOverlappedColor = crossOverlappedColor
            crossOverlappedColorSet = true
            return this
        }

        /**
         * Set stroke width of remove widget. Default value: 4dp.
         *
         * @param crossStrokeWidth stroke width of remove widget
         */
        fun crossStrokeWidth(crossStrokeWidth: Float): Builder {
            this.crossStrokeWidth = crossStrokeWidth
            crossStrokeWidthSet = true
            return this
        }

        /**
         * Set dark color (playing state).
         *
         * @param darkColor dark color
         */
        fun darkColor(@ColorInt darkColor: Int): Builder {
            this.darkColor = darkColor
            darkColorSet = true
            return this
        }

        /**
         * Set drawable for default album icon.
         *
         * @param defaultAlbumCover drawable for default album icon
         */
        fun defaultAlbumDrawable(defaultAlbumCover: Drawable): Builder {
            defaultAlbumDrawable = defaultAlbumCover
            return this
        }

        /**
         * Set widget edge offset on X axis
         *
         * @param edgeOffsetX widget edge offset on X axis
         */
        fun edgeOffsetXCollapsed(edgeOffsetX: Int): Builder {
            edgeOffsetXCollapsed = edgeOffsetX
            edgeOffsetXCollapsedSet = true
            return this
        }

        fun edgeOffsetXExpanded(edgeOffsetX: Int): Builder {
            edgeOffsetXExpanded = edgeOffsetX
            edgeOffsetXExpandedSet = true
            return this
        }

        /**
         * Set widget edge offset on Y axis
         *
         * @param edgeOffsetY widget edge offset on Y axis
         */
        fun edgeOffsetYCollapsed(edgeOffsetY: Int): Builder {
            edgeOffsetYCollapsed = edgeOffsetY
            edgeOffsetYCollapsedSet = true
            return this
        }

        fun edgeOffsetYExpanded(edgeOffsetY: Int): Builder {
            edgeOffsetYExpanded = edgeOffsetY
            edgeOffsetYExpandedSet = true
            return this
        }

        /**
         * Set widget color in expanded state.
         *
         * @param expandWidgetColor widget color in expanded state
         */
        fun expandWidgetColor(@ColorInt expandWidgetColor: Int): Builder {
            this.expandWidgetColor = expandWidgetColor
            expandWidgetColorSet = true
            return this
        }

        /**
         * Set light color (paused state).
         *
         * @param lightColor light color
         */
        fun lightColor(@ColorInt lightColor: Int): Builder {
            this.lightColor = lightColor
            lightColorSet = true
            return this
        }

        /**
         * Set drawable for next track button.
         *
         * @param nextDrawable drawable for next track button.
         */
        fun nextTrackDrawable(nextDrawable: Drawable): Builder {
            this.nextDrawable = nextDrawable
            return this
        }

        /**
         * Set drawable for pause button.
         *
         * @param pauseDrawable drawable for pause button
         */
        fun pauseDrawable(pauseDrawable: Drawable): Builder {
            this.pauseDrawable = pauseDrawable
            return this
        }

        /**
         * Set drawable for play button.
         *
         * @param playDrawable drawable for play button
         */
        fun playDrawable(playDrawable: Drawable): Builder {
            this.playDrawable = playDrawable
            return this
        }

        /**
         * Set drawable for playlist button.
         *
         * @param playlistDrawable drawable for playlist button
         */
        fun playlistDrawable(playlistDrawable: Drawable): Builder {
            this.playlistDrawable = playlistDrawable
            return this
        }

        /**
         * Set drawable for previous track button.
         *
         * @param prevDrawable drawable for previous track button
         */
        fun prevTrackDrawale(prevDrawable: Drawable): Builder {
            this.prevDrawable = prevDrawable
            return this
        }

        /**
         * Set progress bar color.
         *
         * @param progressColor progress bar color
         */
        fun progressColor(@ColorInt progressColor: Int): Builder {
            this.progressColor = progressColor
            progressColorSet = true
            return this
        }

        /**
         * Set stroke width of progress bar. Default value: 4dp.
         *
         * @param progressStrokeWidth stroke width of progress bar
         */
        fun progressStrokeWidth(progressStrokeWidth: Float): Builder {
            this.progressStrokeWidth = progressStrokeWidth
            progressStrokeWidthSet = true
            return this
        }

        /**
         * Set shadow color.
         *
         * @param shadowColor shadow color
         */
        fun shadowColor(@ColorInt shadowColor: Int): Builder {
            this.shadowColor = shadowColor
            shadowColorSet = true
            return this
        }

        /**
         * Set shadow dx. Default value: 1dp.
         *
         * @param shadowDx shadow dx
         * @see Paint.setShadowLayer
         */
        fun shadowDx(shadowDx: Float): Builder {
            this.shadowDx = shadowDx
            shadowDxSet = true
            return this
        }

        /**
         * Set shadow dx. Default value: 1dp.
         *
         * @param shadowDy shadow dy
         * @see Paint.setShadowLayer
         */
        fun shadowDy(shadowDy: Float): Builder {
            this.shadowDy = shadowDy
            shadowDySet = true
            return this
        }

        /**
         * Set shadow radius. Default value: 5dp.
         *
         * @param shadowRadius shadow radius.
         * @see Paint.setShadowLayer
         */
        fun shadowRadius(shadowRadius: Float): Builder {
            this.shadowRadius = shadowRadius
            shadowRadiusSet = true
            return this
        }

        private fun checkOrThrow(number: Int, name: String) {
            require(number >= 0) { "$name must be equals or greater zero." }
        }

        private fun checkOrThrow(number: Float, name: String) {
            require(number >= 0) { "$name must be equals or greater zero." }
        }
    }

    /**
     * Helper class for dealing with expanded widget touch events.
     */
    private inner class ExpandCollapseWidgetCallback : TouchManager.Callback {
        override fun onClick(x: Float, y: Float) {
            expandCollapseWidget.onClick(x, y)
        }

        override fun onLongClick(x: Float, y: Float) {
            expandCollapseWidget.onLongClick(x, y)
        }

        override fun onTouchOutside() {
            if (!expandCollapseWidget.isAnimationInProgress) {
                collapse()
            }
        }

        override fun onTouched(x: Float, y: Float) {
            expandCollapseWidget.onTouched(x, y)
        }

        override fun onMoved(diffX: Float, diffY: Float) {
            updatePlayPauseButtonPosition()
        }

        override fun onReleased(x: Float, y: Float) {
            expandCollapseWidget.onReleased(x, y)
        }

        override fun onAnimationCompleted() {
            updatePlayPauseButtonPosition()
        }
    }

    private inner class OnControlsClickListenerWrapper : OnControlsClickListener {
        private var onControlsClickListener: OnControlsClickListener? = null

        fun onControlsClickListener(inner: OnControlsClickListener?): OnControlsClickListenerWrapper {
            this.onControlsClickListener = inner
            return this
        }

        override fun onPlaylistClicked(): Boolean {
            if (onControlsClickListener?.let { !it.onPlaylistClicked() } == true) {
                collapse()
                return true
            }
            return false
        }

        override fun onPlaylistLongClicked() {
            onControlsClickListener?.onPlaylistLongClicked()
        }

        override fun onPreviousClicked() {
            onControlsClickListener?.onPreviousClicked()
        }

        override fun onPreviousLongClicked() {
            onControlsClickListener?.onPreviousLongClicked()
        }

        override fun onPlayPauseClicked(): Boolean {
            if (onControlsClickListener == null || !onControlsClickListener!!.onPlayPauseClicked()) {
                val pbState = playbackState!!
                if (pbState.state() != Configuration.STATE_PLAYING) {
                    pbState.start(this@AudioWidget)
                } else {
                    pbState.pause(this@AudioWidget)
                }
                return true
            }
            return false
        }

        override fun onPlayPauseLongClicked() {
            onControlsClickListener?.onPlayPauseLongClicked()
        }

        override fun onNextClicked() {
            onControlsClickListener?.onNextClicked()
        }

        override fun onNextLongClicked() {
            onControlsClickListener?.onNextLongClicked()
        }

        override fun onAlbumClicked() {
            onControlsClickListener?.onAlbumClicked()
        }

        override fun onAlbumLongClicked() {
            onControlsClickListener?.onAlbumLongClicked()
        }
    }

    /**
     * Helper class for dealing with collapsed widget touch events.
     */
    private inner class PlayPauseButtonCallback : TouchManager.Callback {
        private val REMOVE_BTN_ANIM_DURATION: Long = 200
        private val animatorUpdateListener: AnimatorUpdateListener
        private var readyToRemove = false
        override fun onClick(x: Float, y: Float) {
            playPauseButton.onClick()
            onControlsClickListener?.onPlayPauseClicked()
        }

        override fun onLongClick(x: Float, y: Float) {
            released = true
            expand()
        }

        override fun onTouched(x: Float, y: Float) {
            released = false
            handler.postDelayed({
                if (!released) {
                    removeWidgetShown = true
                    val animator = ValueAnimator.ofFloat(
                        hiddenRemWidPos.y.toFloat(),
                        visibleRemWidPos.y.toFloat()
                    )
                    animator.duration = REMOVE_BTN_ANIM_DURATION
                    animator.addUpdateListener(animatorUpdateListener)
                    animator.start()
                }
            }, Configuration.LONG_CLICK_THRESHOLD)
            playPauseButton.onTouchDown()
        }

        override fun onMoved(diffX: Float, diffY: Float) {
            val curReadyToRemove = isReadyToRemove()
            if (curReadyToRemove != readyToRemove) {
                readyToRemove = curReadyToRemove
                removeWidgetView.setOverlapped(readyToRemove)
                if (readyToRemove && vibrator.hasVibrator()) {
                    vibrator.vibrate(VIBRATION_DURATION)
                }
            }
            updateRemoveBtnPosition()
        }

        override fun onReleased(x: Float, y: Float) {
            playPauseButton.onTouchUp()
            released = true
            if (removeWidgetShown) {
                val animator =
                    ValueAnimator.ofFloat(visibleRemWidPos.y.toFloat(), hiddenRemWidPos.y.toFloat())
                animator.duration = REMOVE_BTN_ANIM_DURATION
                animator.addUpdateListener(animatorUpdateListener)
                animator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        removeWidgetShown = false
                        if (!isShown) {
                            try {
                                windowManager.removeView(removeWidgetView)
                            } catch (e: IllegalArgumentException) {
                                // view not attached to window
                            }
                        }
                    }
                })
                animator.start()
            }
            if (isReadyToRemove()) {
                hideInternal(false)
            } else {
                if (onWidgetStateChangedListener != null) {
                    val params = playPauseButton.layoutParams as WindowManager.LayoutParams
                    onWidgetStateChangedListener!!.onWidgetPositionChanged(
                        (params.x + widgetHeight).toInt(),
                        (params.y + widgetHeight).toInt()
                    )
                }
            }
        }

        override fun onAnimationCompleted() {
            onWidgetStateChangedListener?.let {
                val params = playPauseButton.layoutParams as WindowManager.LayoutParams
                it.onWidgetPositionChanged(
                    (params.x + widgetHeight).toInt(),
                    (params.y + widgetHeight).toInt()
                )
            }
        }

        private fun isReadyToRemove(): Boolean {
            val removeParams = removeWidgetView.layoutParams as WindowManager.LayoutParams
            removeBounds[removeParams.x.toFloat(), removeParams.y.toFloat(), removeParams.x + widgetHeight] =
                removeParams.y + widgetHeight
            val params = playPauseButton.layoutParams as WindowManager.LayoutParams
            val cx = params.x + widgetHeight
            val cy = params.y + widgetHeight
            return removeBounds.contains(cx, cy)
        }

        private fun updateRemoveBtnPosition() {
            if (removeWidgetShown) {
                val playPauseBtnParams = playPauseButton.layoutParams as WindowManager.LayoutParams
                val removeBtnParams = removeWidgetView.layoutParams as WindowManager.LayoutParams
                val tgAlpha =
                    (screenSize.x / 2.0 - playPauseBtnParams.x) / (visibleRemWidPos.y - playPauseBtnParams.y)
                val rotationDegrees = 360 - Math.toDegrees(atan(tgAlpha))
                var distance = sqrt(
                    (animatedRemBtnYPos - playPauseBtnParams.y.toDouble()).pow(2.0) +
                            (visibleRemWidPos.x - hiddenRemWidPos.x.toDouble()).pow(2.0)
                ).toFloat()
                val maxDistance = sqrt(
                    screenSize.x.toDouble().pow(2.0) + screenSize.y.toDouble().pow(2.0)
                ).toFloat()
                distance /= maxDistance
                if (animatedRemBtnYPos == -1) {
                    animatedRemBtnYPos = visibleRemWidPos.y
                }
                removeBtnParams.x = rotateX(
                    visibleRemWidPos.x.toFloat(),
                    animatedRemBtnYPos - radius * distance,
                    hiddenRemWidPos.x.toFloat(),
                    animatedRemBtnYPos.toFloat(),
                    rotationDegrees.toFloat()
                ).toInt()
                removeBtnParams.y = rotateY(
                    visibleRemWidPos.x.toFloat(),
                    animatedRemBtnYPos - radius * distance,
                    hiddenRemWidPos.x.toFloat(),
                    animatedRemBtnYPos.toFloat(),
                    rotationDegrees.toFloat()
                ).toInt()
                try {
                    windowManager.updateViewLayout(removeWidgetView, removeBtnParams)
                } catch (e: IllegalArgumentException) {
                    // view not attached to window
                }
            }
        }


        init {
            animatorUpdateListener = AnimatorUpdateListener { animation: ValueAnimator ->
                if (!removeWidgetShown) {
                    return@AnimatorUpdateListener
                }
                animatedRemBtnYPos = (animation.animatedValue as Float).toInt()
                updateRemoveBtnPosition()
            }
        }
    }

    companion object {
        private const val VIBRATION_DURATION: Long = 100
    }

    init {
        windowManager.defaultDisplay.getSize(screenSize)
        screenSize.y -= statusBarHeight() + navigationBarHeight()
        val configuration = prepareConfiguration(builder)
        playPauseButton = PlayPauseButton(configuration)
        expandCollapseWidget = ExpandCollapseWidget(configuration)
        removeWidgetView = RemoveWidgetView(configuration)
        val offsetCollapsed =
            context.resources.getDimensionPixelOffset(R.dimen.aw_edge_offset_collapsed)
        val offsetExpanded =
            context.resources.getDimensionPixelOffset(R.dimen.aw_edge_offset_expanded)
        playPauseButtonManager = TouchManager(
            playPauseButton, playPauseButton.newBoundsChecker(
                if (builder.edgeOffsetXCollapsedSet) builder.edgeOffsetXCollapsed else offsetCollapsed,
                if (builder.edgeOffsetYCollapsedSet) builder.edgeOffsetYCollapsed else offsetCollapsed
            )
        )
            .screenWidth(screenSize.x)
            .screenHeight(screenSize.y)
        expandedWidgetManager = TouchManager(
            expandCollapseWidget, expandCollapseWidget.newBoundsChecker(
                if (builder.edgeOffsetXExpandedSet) builder.edgeOffsetXExpanded else offsetExpanded,
                if (builder.edgeOffsetYExpandedSet) builder.edgeOffsetYExpanded else offsetExpanded
            )
        )
            .screenWidth(screenSize.x)
            .screenHeight(screenSize.y)
        playPauseButtonManager.callback(PlayPauseButtonCallback())
        expandedWidgetManager.callback(ExpandCollapseWidgetCallback())
        expandCollapseWidget.onWidgetStateChangedListener(object : OnWidgetStateChangedListener {
            override fun onWidgetStateChanged(state: State) {
                if (state == State.COLLAPSED) {
                    playPauseButton.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    try {
                        windowManager.removeView(expandCollapseWidget)
                    } catch (e: IllegalArgumentException) {
                        // view not attached to window
                    }
                    playPauseButton.enableProgressChanges(true)
                }
                onWidgetStateChangedListener?.onWidgetStateChanged(state)
            }

            override fun onWidgetPositionChanged(cx: Int, cy: Int) {}
        })
        onControlsClickListener = OnControlsClickListenerWrapper()
        expandCollapseWidget.onControlsClickListener(onControlsClickListener)
        ppbToExpBoundsChecker = playPauseButton.newBoundsChecker(
            if (builder.edgeOffsetXExpandedSet) builder.edgeOffsetXExpanded else offsetExpanded,
            if (builder.edgeOffsetYExpandedSet) builder.edgeOffsetYExpanded else offsetExpanded
        )
        expToPpbBoundsChecker = expandCollapseWidget.newBoundsChecker(
            if (builder.edgeOffsetXCollapsedSet) builder.edgeOffsetXCollapsed else offsetCollapsed,
            if (builder.edgeOffsetYCollapsedSet) builder.edgeOffsetYCollapsed else offsetCollapsed
        )
    }
}