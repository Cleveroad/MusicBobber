package com.cleveroad.audiowidget.example

import android.content.Context
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.cleveroad.audiowidget.example.MusicAdapter.MusicViewHolder

/**
 * Adapter for list of tracks.
 */
internal class MusicAdapter(context: Context) :
    BaseRecyclerViewAdapter<MusicItem, MusicViewHolder>(context) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicViewHolder {
        val view = inflater.inflate(R.layout.item_music, parent, false)
        return MusicViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicViewHolder, position: Int) {
        val item = getItem(position)
        filter!!.let {
            holder.title.text = it.highlightFilteredSubstring(item.title ?: "")
            holder.artist.text = it.highlightFilteredSubstring(item.artist ?: "")
            holder.album.text = it.highlightFilteredSubstring(item.album ?: "")
            holder.duration.text = convertDuration(item.duration)
            Glide.with(context)
                .asBitmap()
                .load(item.albumArtUri)
                .apply(RequestOptions().circleCrop())
                .placeholder(R.drawable.aw_ic_default_album)
                .error(R.drawable.aw_ic_default_album)
                .into(holder.albumCover)
        }
    }

    private fun convertDuration(durationInMs: Long): String {
        return DateUtils.formatElapsedTime(durationInMs)
    }

    internal class MusicViewHolder(itemView: View) : ViewHolder(itemView) {
        var title: TextView = itemView.findViewById(R.id.title)
        var artist: TextView = itemView.findViewById(R.id.artist)
        var album: TextView = itemView.findViewById(R.id.album)
        var duration: TextView = itemView.findViewById(R.id.duration)
        var albumCover: ImageView = itemView.findViewById(R.id.album_cover)
    }
}