package com.cleveroad.audiowidget.example

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.util.*

/**
 * Loader for list of tracks.
 */
internal class MusicLoader(context: Context) :
    BaseAsyncTaskLoader<Collection<MusicItem>>(context) {
    private val albumArtUriBase  = Uri.parse("content://media/external/audio/albumart")

    override fun loadInBackground(): Collection<MusicItem> {
        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val items: MutableList<MusicItem> = ArrayList()
        val cursor = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Audio.Media.IS_MUSIC + "=1",
            null,
            "LOWER(" + MediaStore.Audio.Media.ARTIST + ") ASC, " +
                    "LOWER(" + MediaStore.Audio.Media.ALBUM + ") ASC, " +
                    "LOWER(" + MediaStore.Audio.Media.TITLE + ") ASC"
        )
        cursor!!.use { c ->
            if (c.moveToFirst()) {
                val title = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val album = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val artist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val duration = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumId = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val data = c.getColumnIndex(MediaStore.Audio.Media.DATA)
                do {
                    val item = with(MusicItem()) {
                        this.title = c.getString(title)
                        this.album = c.getString(album)
                        this.artist = c.getString(artist)
                        this.duration = c.getLong(duration)
                        this.albumArtUri = ContentUris.withAppendedId(
                            albumArtUriBase,
                            c.getLong(albumId)
                        )
                        this.fileUri = Uri.parse(c.getString(data))
                        this
                    }
                    items.add(item)
                } while (c.moveToNext())
            }
        }
        return items
    }
}