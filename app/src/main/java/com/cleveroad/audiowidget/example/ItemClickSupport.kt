package com.cleveroad.audiowidget.example

import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.recyclerview.widget.RecyclerView

internal class ItemClickSupport private constructor(private val mRecyclerView: RecyclerView) {
    /**
     * Interface definition for a callback to be invoked when an item in the
     * RecyclerView has been clicked.
     */
    interface OnItemClickListener {
        /**
         * Callback method to be invoked when an item in the RecyclerView
         * has been clicked.
         *
         * @param parent The RecyclerView where the click happened.
         * @param view The view within the RecyclerView that was clicked
         * @param position The position of the view in the adapter.
         * @param id The row id of the item that was clicked.
         */
        fun onItemClick(parent: RecyclerView?, view: View?, position: Int, id: Long)
    }

    /**
     * Interface definition for a callback to be invoked when an item in the
     * RecyclerView has been clicked and held.
     */
    interface OnItemLongClickListener {
        /**
         * Callback method to be invoked when an item in the RecyclerView
         * has been clicked and held.
         *
         * @param parent The RecyclerView where the click happened
         * @param view The view within the RecyclerView that was clicked
         * @param position The position of the view in the list
         * @param id The row id of the item that was clicked
         *
         * @return true if the callback consumed the long click, false otherwise
         */
        fun onItemLongClick(parent: RecyclerView?, view: View?, position: Int, id: Long): Boolean
    }

    private val mTouchListener: TouchListener
    private var mItemClickListener: OnItemClickListener? = null
    private var mItemLongClickListener: OnItemLongClickListener? = null

    /**
     * Register a callback to be invoked when an item in the
     * RecyclerView has been clicked.
     *
     * @param listener The callback that will be invoked.
     */
    fun setOnItemClickListener(listener: OnItemClickListener) {
        mItemClickListener = listener
    }

    /**
     * Register a callback to be invoked when an item in the
     * RecyclerView has been clicked and held.
     *
     * @param listener The callback that will be invoked.
     */
    fun setOnItemLongClickListener(listener: OnItemLongClickListener?) {
        if (!mRecyclerView.isLongClickable) {
            mRecyclerView.isLongClickable = true
        }
        mItemLongClickListener = listener
    }

    private inner class TouchListener constructor(recyclerView: RecyclerView) :
        ClickItemTouchListener(recyclerView) {
        override fun performItemClick(
            parent: RecyclerView?,
            view: View,
            position: Int,
            id: Long
        ): Boolean {
            return mItemClickListener?.let {
                view.playSoundEffect(SoundEffectConstants.CLICK)
                it.onItemClick(parent, view, position, id)
                true
            } ?: false
        }

        override fun performItemLongClick(
            parent: RecyclerView?,
            view: View,
            position: Int,
            id: Long
        ): Boolean {
            if (mItemLongClickListener != null) {
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                return mItemLongClickListener!!.onItemLongClick(parent, view, position, id)
            }
            return false
        }

        override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
    }

    companion object {
        fun addTo(recyclerView: RecyclerView): ItemClickSupport {
            var itemClickSupport = from(recyclerView)
            if (itemClickSupport == null) {
                itemClickSupport = ItemClickSupport(recyclerView)
                recyclerView.setTag(R.id.twowayview_item_click_support, itemClickSupport)
            }
            return itemClickSupport
        }

        fun removeFrom(recyclerView: RecyclerView) {
            val itemClickSupport = from(recyclerView) ?: return
            recyclerView.removeOnItemTouchListener(itemClickSupport.mTouchListener)
            recyclerView.setTag(R.id.twowayview_item_click_support, null)
        }

        fun from(recyclerView: RecyclerView?): ItemClickSupport? {
            return if (recyclerView == null) {
                null
            } else recyclerView.getTag(R.id.twowayview_item_click_support) as ItemClickSupport?
        }
    }

    init {
        mTouchListener = TouchListener(mRecyclerView)
        mRecyclerView.addOnItemTouchListener(mTouchListener)
    }
}