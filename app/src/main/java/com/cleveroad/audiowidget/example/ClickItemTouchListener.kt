package com.cleveroad.audiowidget.example

import android.content.Context
import android.os.Build
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener

/**
 * Helper class for detecting click on RecyclerView's item
 */
internal abstract class ClickItemTouchListener(hostView: RecyclerView) : OnItemTouchListener {
    private val mGestureDetector: GestureDetector
    private fun isAttachedToWindow(hostView: RecyclerView): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            hostView.isAttachedToWindow
        } else {
            hostView.handler != null
        }
    }

    private fun hasAdapter(hostView: RecyclerView): Boolean {
        return hostView.adapter != null
    }

    override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
        if (!isAttachedToWindow(recyclerView) || !hasAdapter(recyclerView)) {
            return false
        }
        mGestureDetector.onTouchEvent(event)
        return false
    }

    override fun onTouchEvent(recyclerView: RecyclerView, event: MotionEvent) {
        // We can silently track tap and and long presses by silently
        // intercepting touch events in the host RecyclerView.
    }

    abstract fun performItemClick(
        parent: RecyclerView?,
        view: View,
        position: Int,
        id: Long
    ): Boolean

    abstract fun performItemLongClick(
        parent: RecyclerView?,
        view: View,
        position: Int,
        id: Long
    ): Boolean

    private class ItemClickGestureDetector(
        context: Context?,
        mGestureListener: ItemClickGestureListener
    ) : GestureDetector(context, mGestureListener)

    private inner class ItemClickGestureListener(private val mHostView: RecyclerView) :
        SimpleOnGestureListener() {
        private var mTargetChild: View? = null
        fun dispatchSingleTapUpIfNeeded(event: MotionEvent) {
            // When the long press hook is called but the long press listener
            // returns false, the target child will be left around to be
            // handled later. In this case, we should still treat the gesture
            // as potential item click.
            if (mTargetChild != null) {
                onSingleTapUp(event)
            }
        }

        override fun onDown(event: MotionEvent): Boolean {
            val x = event.x.toInt()
            val y = event.y.toInt()
            mTargetChild = mHostView.findChildViewUnder(x.toFloat(), y.toFloat())
            return mTargetChild != null
        }

        override fun onShowPress(event: MotionEvent) {
            mTargetChild?.let {
                it.isPressed = true
            }
        }

        override fun onSingleTapUp(event: MotionEvent): Boolean {
            var handled = false
            mTargetChild?.let {
                it.isPressed = false
                val position = mHostView.getChildLayoutPosition(it)
                val id = mHostView.adapter!!.getItemId(position)
                handled = performItemClick(mHostView, it, position, id)
                mTargetChild = null
            }
            return handled
        }

        override fun onScroll(
            event: MotionEvent,
            event2: MotionEvent,
            v: Float,
            v2: Float
        ): Boolean {
            return mTargetChild?.let {
                it.isPressed = false
                mTargetChild = null
                true
            } ?: false
        }

        override fun onLongPress(event: MotionEvent) {
            val target = mTargetChild ?: return
            val position = mHostView.getChildLayoutPosition(target)
            val id = mHostView.adapter!!.getItemId(position)
            val handled = performItemLongClick(mHostView, target, position, id)
            if (handled) {
                target.isPressed = false
                mTargetChild = null
            }
        }
    }

    init {
        mGestureDetector = ItemClickGestureDetector(
            hostView.context,
            ItemClickGestureListener(hostView)
        )
    }
}