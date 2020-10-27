package com.cleveroad.audiowidget.example

import android.content.Context
import androidx.loader.content.AsyncTaskLoader

/**
 * Base AsyncTaskLoader implementation
 */
internal abstract class BaseAsyncTaskLoader<T>(context: Context) : AsyncTaskLoader<T>(context) {
    private var mData: T? = null

    /**
     * Called when there is new data to deliver to the client.  The
     * super class will take care of delivering it; the implementation
     * here just adds a little more logic.
     */
    override fun deliverResult(data: T?) {
        if (isReset) {
            // An async query came in while the loader is stopped.  We
            // don't need the result.
            data?.let { onReleaseResources(it) }
        }
        val oldData = mData
        mData = data
        if (isStarted) {
            // If the Loader is currently started, we can immediately
            // deliver its results.
            super.deliverResult(data)
        }

        // At this point we can release the resources associated with
        // 'oldData' if needed; now that the new result is delivered we
        // know that it is no longer in use.
        oldData?.let { onReleaseResources(it) }
    }

    /**
     * Handles a request to start the Loader.
     */
    override fun onStartLoading() {
        if (mData != null) {
            // If we currently have a result available, deliver it
            // immediately.
            deliverResult(mData)
        }
        if (takeContentChanged() || mData == null) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad()
        }
    }

    /**
     * Handles a request to stop the Loader.
     */
    override fun onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad()
    }

    /**
     * Handles a request to cancel a load.
     */
    override fun onCanceled(data: T?) {
        super.onCanceled(data)
        // At this point we can release the resources associated with 'apps'
        // if needed.
        onReleaseResources(data)
    }

    /**
     * Handles a request to completely reset the Loader.
     */
    override fun onReset() {
        super.onReset()

        // Ensure the loader is stopped
        onStopLoading()

        // At this point we can release the resources associated with 'apps'
        // if needed.
        if (mData != null) {
            onReleaseResources(mData)
            mData = null
        }
    }

    /**
     * Helper function to take care of releasing resources associated
     * with an actively loaded data set.
     */
    private fun onReleaseResources(apps: T?) {
        // For a simple List<> there is nothing to do.  For something
        // like a Cursor, we would close it here.
    }
}