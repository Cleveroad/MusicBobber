package com.cleveroad.audiowidget.example

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable

/**
 * Music track model.
 */
open class MusicItem() : Parcelable {
    var title: String? = null
    var album: String? = null
    var artist: String? = null
    var duration: Long = 0
    var albumArtUri: Uri? = null
    var fileUri: Uri? = null

    constructor(`in`: Parcel) : this() {
        title = `in`.readString()
        album = `in`.readString()
        artist = `in`.readString()
        duration = `in`.readLong()
        albumArtUri = `in`.readParcelable(Uri::class.java.classLoader)
        fileUri = `in`.readParcelable(Uri::class.java.classLoader)
    }

    override fun toString(): String {
        return "MusicItem{" +
                "title='" + title + '\'' +
                ", album='" + album + '\'' +
                ", artist='" + artist + '\'' +
                ", duration=" + duration +
                ", albumArtUri=" + albumArtUri +
                ", fileUri=" + fileUri +
                '}'
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(title)
        dest.writeString(album)
        dest.writeString(artist)
        dest.writeLong(duration)
        dest.writeParcelable(albumArtUri, 0)
        dest.writeParcelable(fileUri, 0)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MusicItem

        if (fileUri != other.fileUri) return false
        return true
    }

    override fun hashCode(): Int {
        return fileUri?.hashCode() ?: 0
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<MusicItem> = object : Parcelable.Creator<MusicItem> {
            override fun createFromParcel(source: Parcel): MusicItem {
                return MusicItem(source)
            }

            override fun newArray(size: Int): Array<MusicItem> {
                return emptyArray()
            }
        }
    }
}