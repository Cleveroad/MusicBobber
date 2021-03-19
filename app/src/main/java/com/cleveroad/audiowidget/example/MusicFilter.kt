package com.cleveroad.audiowidget.example

import android.text.TextUtils
import java.util.*

/**
 * Filter for list of tracks.
 */
internal class MusicFilter(highlightColor: Int) : BaseFilter<MusicItem>(highlightColor) {
    override fun performFilteringImpl(constraint: CharSequence): FilterResults {
        val results = FilterResults()
        if (TextUtils.isEmpty(constraint) || TextUtils.isEmpty(
                constraint.toString().trim { it <= ' ' })
        ) {
            results.count = -1
            return results
        }
        val str = constraint.toString().trim { it <= ' ' }
        val result: MutableList<MusicItem?> = ArrayList()
        val size = nonFilteredCount
        for (i in 0 until size) {
            val item = getNonFilteredItem(i)
            if (check(str, item.title)
                || check(str, item.album)
                || check(str, item.artist)
            ) {
                result.add(item)
            }
        }
        results.count = result.size
        results.values = result
        return results
    }

    private fun check(what: String, where: String?): Boolean {
        var what = what
        var where = where
        if (where.isNullOrBlank()) return false
        where = where.toLowerCase()
        what = what.toLowerCase()
        return where.contains(what)
    }
}