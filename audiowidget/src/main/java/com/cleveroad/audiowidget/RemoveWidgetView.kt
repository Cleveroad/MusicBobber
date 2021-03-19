package com.cleveroad.audiowidget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.cleveroad.audiowidget.DrawableUtils.rotateX
import com.cleveroad.audiowidget.DrawableUtils.rotateY

/**
 * Remove widget view.
 */
@SuppressLint("ViewConstructor")
internal class RemoveWidgetView(configuration: Configuration) : View(configuration.context) {
    private val size: Float = configuration.radius * SCALE_LARGE * 2
    private val radius: Float = configuration.radius
    private val defaultColor: Int = configuration.crossColor
    private val overlappedColor: Int = configuration.crossOverlappedColor
    private val sizeAnimator: ValueAnimator = ValueAnimator()
    private var scale = 1.0f

    private val paint: Paint by lazy {
        val p = Paint()
        p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeWidth = configuration.crossStrokeWidth
        p.color = configuration.crossColor
        p.strokeCap = Paint.Cap.ROUND
        p
    }

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val size = MeasureSpec.makeMeasureSpec(size.toInt(), MeasureSpec.EXACTLY)
        super.onMeasure(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = canvas.width shr 1
        val cy = canvas.height shr 1
        val rad = radius * 0.75f
        canvas.save()
        canvas.scale(scale, scale, cx.toFloat(), cy.toFloat())
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), rad, paint)
        drawCross(canvas, cx.toFloat(), cy.toFloat(), rad * 0.5f, 45f)
        canvas.restore()
    }

    private fun drawCross(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        startAngle: Float
    ) {
        drawLine(canvas, cx, cy, radius, startAngle)
        drawLine(canvas, cx, cy, radius, startAngle + 90)
    }

    private fun drawLine(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        angle: Float
    ) {
        var angle = angle
        val x1 = rotateX(cx, cy + radius, cx, cy, angle)
        val y1 = rotateY(cx, cy + radius, cx, cy, angle)
        angle += 180f
        val x2 = rotateX(cx, cy + radius, cx, cy, angle)
        val y2 = rotateY(cx, cy + radius, cx, cy, angle)
        canvas.drawLine(x1, y1, x2, y2, paint)
    }

    /**
     * Set overlapped state.
     * @param overlapped true if widget overlapped, false otherwise
     */
    fun setOverlapped(overlapped: Boolean) {
        sizeAnimator.cancel()
        if (overlapped) {
            sizeAnimator.setFloatValues(scale, SCALE_LARGE)
            if (paint.color != overlappedColor) {
                paint.color = overlappedColor
                invalidate()
            }
        } else {
            sizeAnimator.setFloatValues(scale, SCALE_DEFAULT)
            if (paint.color != defaultColor) {
                paint.color = defaultColor
                invalidate()
            }
        }
        sizeAnimator.start()
    }

    init {
        sizeAnimator.addUpdateListener { animation: ValueAnimator ->
            scale = animation.animatedValue as Float
            invalidate()
        }
    }

    companion object {
        const val SCALE_DEFAULT = 1.0f
        const val SCALE_LARGE = 1.5f
    }
}