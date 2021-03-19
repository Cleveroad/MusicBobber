package com.cleveroad.audiowidget.example

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import java.lang.ref.WeakReference

/**
 * Simple observer for displaying and hiding empty view.
 */
internal class EmptyViewObserver(view: View) : AdapterDataObserver() {
    private val viewWeakReference: WeakReference<View> = WeakReference(view)
    private var recyclerViewWeakReference: WeakReference<RecyclerView>? = null

    /**
     * Bind observer to recycler view's adapter. This method must be called after setting adapter to recycler view.
     * @param recyclerView instance of recycler view
     */
    fun bind(recyclerView: RecyclerView) {
        unbind()
        recyclerViewWeakReference = WeakReference(recyclerView)
        recyclerView.adapter!!.registerAdapterDataObserver(this)
    }

    fun unbind() {
        recyclerViewWeakReference?.let {
            val recyclerView = it.get()
            if (recyclerView != null) {
                recyclerView.adapter!!.unregisterAdapterDataObserver(this)
                it.clear()
            }
        }
    }

    override fun onChanged() {
        super.onChanged()
        somethingChanged()
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
        super.onItemRangeChanged(positionStart, itemCount)
        somethingChanged()
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
        super.onItemRangeChanged(positionStart, itemCount, payload)
        somethingChanged()
    }

    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        super.onItemRangeInserted(positionStart, itemCount)
        somethingChanged()
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
        super.onItemRangeRemoved(positionStart, itemCount)
        somethingChanged()
    }

    private fun somethingChanged() {
        val view = viewWeakReference.get()
        val recyclerView = recyclerViewWeakReference!!.get()
        if (view != null && recyclerView != null) {
            if (recyclerView.adapter!!.itemCount == 0) {
                view.visibility = View.VISIBLE
            } else {
                view.visibility = View.GONE
            }
        }
    }
}