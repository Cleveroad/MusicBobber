package com.cleveroad.audiowidget.example

import android.content.Context
import android.database.DataSetObserver
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.Filter
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver

/**
 * Base filter that can be easily integrated with [BaseRecyclerViewAdapter].<br></br><br></br>
 * For iterating through adapter's data use [.getNonFilteredCount] and [.getNonFilteredItem].
 */
internal abstract class BaseFilter<T> : Filter {
    private lateinit var adapter: FilterableAdapter<T>
    private var lastConstraint: CharSequence? = null
    private var lastResults: FilterResults? = null
    var dataSetObserver: DataSetObserver? = null
        private set
    var adapterDataObserver: AdapterDataObserver? = null
        private set
    private var highlightColor = 0

    constructor(context: Context) {
        highlightColor = ContextCompat.getColor(context, R.color.colorAccent)
    }

    constructor(highlightColor: Int) {
        setHighlightColor(highlightColor)
    }

    fun setHighlightColor(highlightColor: Int): BaseFilter<*> {
        this.highlightColor = highlightColor
        return this
    }

    fun init(adapter: FilterableAdapter<T>) {
        this.adapter = adapter
        dataSetObserver = object : DataSetObserver() {
            override fun onChanged() {
                super.onChanged()
                if (!isFiltered) return
                performFiltering(lastConstraint!!)
            }

            override fun onInvalidated() {
                super.onInvalidated()
                if (!isFiltered) return
                lastResults = FilterResults().also  {
                    it.count = -1
                    it.values = emptyList<Any>()
                }
            }
        }
        adapterDataObserver = object : AdapterDataObserver() {
            override fun onChanged() {
                super.onChanged()
                if (!isFiltered) return
                performFiltering(lastConstraint!!)
            }
        }
    }

    protected val nonFilteredCount: Int
        get() = adapter.nonFilteredCount

    protected fun getNonFilteredItem(position: Int): T {
        return adapter.getNonFilteredItem(position)
    }

    override fun performFiltering(constraint: CharSequence): FilterResults {
        return performFilteringImpl(constraint)
    }

    /**
     * Perform filtering as always. Returned [FilterResults] object must be non-null.
     * @param constraint the constraint used to filter the data
     * @return filtering results. <br></br>
     * You can set [FilterResults.count] to -1 to specify that no filtering was applied.<br></br>
     * [FilterResults.values] must be instance of [List].
     */
    protected abstract fun performFilteringImpl(constraint: CharSequence): FilterResults

    override fun publishResults(constraint: CharSequence, results: FilterResults) {
        lastConstraint = constraint
        lastResults = results
        adapter.notifyDataSetChanged()
    }

    val isFiltered: Boolean
        get() = lastResults != null && lastResults!!.count > -1

    fun getItem(position: Int): T {
        return (lastResults!!.values as List<T>)[position]
    }

    val count: Int
        get() = lastResults!!.count

    fun highlightFilteredSubstring(name: String): Spannable {
        val string = SpannableString(name)
        if (!isFiltered) return string
        val filteredString = lastConstraint.toString().trim { it <= ' ' }.toLowerCase()
        val lowercase = name.toLowerCase()
        val length = filteredString.length
        var index = -1
        var prevIndex: Int
        do {
            prevIndex = index
            index = lowercase.indexOf(filteredString, prevIndex + 1)
            if (index == -1) {
                break
            }
            string.setSpan(ForegroundColorSpan(highlightColor), index, index + length, 0)
        } while (true)
        return string
    }

    internal interface FilterableAdapter<T> {
        val nonFilteredCount: Int
        fun getNonFilteredItem(position: Int): T
        fun notifyDataSetChanged()
        fun withFilter(filter: BaseFilter<T>?)
        val isFiltered: Boolean
        fun highlightFilteredSubstring(text: String?): Spannable?
    }
}