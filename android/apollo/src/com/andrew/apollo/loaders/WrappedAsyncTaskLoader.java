package com.andrew.apollo.loaders;

import android.content.AsyncTaskLoader;
import android.content.Context;

/**
 * <a href="http://code.google.com/p/android/issues/detail?id=14944">Issue
 * 14944</a>
 *
 * @author Alexander Blom
 */
abstract class WrappedAsyncTaskLoader<D> extends AsyncTaskLoader<D> {

    private D mData;

    /**
     * Constructor of <code>WrappedAsyncTaskLoader</code>
     *
     * @param context The {@link Context} to use.
     */
    WrappedAsyncTaskLoader(Context context) {
        super(context);
    }

    @Override
    public void deliverResult(D data) {
        if (!isReset() && data != null) {
            this.mData = data;
            try {
                super.deliverResult(data);
            } catch (Throwable t) { /* not much else we can do */ }
        }
        //else { An asynchronous query came in while the loader is stopped }
    }

    @Override
    protected void onStartLoading() {
        if (this.mData != null) {
            deliverResult(this.mData);
        } else if (takeContentChanged() || this.mData == null) {
            forceLoad();
        }
    }

    @Override
    protected void onStopLoading() {
        // Attempt to cancel the current load task if possible
        cancelLoad();
    }

    @Override
    protected void onReset() {
        super.onReset();
        // Ensure the loader is stopped
        onStopLoading();
        this.mData = null;
    }
}
