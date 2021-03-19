package com.cleveroad.audiowidget.example

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.cleveroad.audiowidget.example.BaseFilter.FilterableAdapter
import java.util.*

/**
 * Base adapter for recycler view
 */
internal abstract class BaseRecyclerViewAdapter<TData, TViewHolder : ViewHolder?>
    : RecyclerView.Adapter<TViewHolder>, FilterableAdapter<TData> {
    protected val context: Context
    protected val inflater: LayoutInflater
    private val data: MutableList<TData>

    var filter: BaseFilter<TData>? = null
        private set

    val snapshot: List<TData>
        get() = ArrayList(data)

    constructor(context: Context) {
        this.context = context.applicationContext
        inflater = LayoutInflater.from(context)
        data = ArrayList()
    }

    constructor(context: Context, data: List<TData>) {
        this.context = context.applicationContext
        inflater = LayoutInflater.from(context)
        this.data = ArrayList(data)
    }

    override fun withFilter(filter: BaseFilter<TData>?) {
        this.filter?.adapterDataObserver?.let {
            unregisterAdapterDataObserver(it)
        }
        this.filter = filter
        this.filter?.let {
            it.init(this)
            registerAdapterDataObserver(it.adapterDataObserver!!)
        }
    }

    override fun getItemCount(): Int {
        val filter = this.filter
        return if (filter?.isFiltered == true) filter.count else data.size
    }

    fun getItem(position: Int): TData {
        val filter = this.filter
        return if (filter?.isFiltered == true) filter.getItem(position) else data[position]
    }

    override val isFiltered: Boolean
        get() = filter?.isFiltered == true

    override fun highlightFilteredSubstring(text: String?): Spannable? {
        return if (isFiltered) filter!!.highlightFilteredSubstring(text!!) else SpannableString(text)
    }

    override fun getNonFilteredItem(position: Int): TData {
        return data[position]
    }

    override val nonFilteredCount: Int
        get() = data.size

    fun add(`object`: TData): Boolean {
        return data.add(`object`)
    }

    fun remove(`object`: TData): Boolean {
        return data.remove(`object`)
    }

    fun remove(position: Int): TData {
        return data.removeAt(position)
    }

    fun clear() {
        data.clear()
    }

    fun addAll(collection: Collection<TData>): Boolean {
        return data.addAll(collection)
    }
}