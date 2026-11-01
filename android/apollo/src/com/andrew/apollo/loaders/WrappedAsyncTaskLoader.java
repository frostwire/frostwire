package com.andrew.apollo.loaders;

import android.content.Context;
import android.provider.MediaStore;
import androidx.loader.content.AsyncTaskLoader;

/**
 * <a href="http://code.google.com/p/android/issues/detail?id=14944">Issue 14944</a>
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
      } catch (Throwable t) {
        /* not much else we can do */
      }
    }
    // else { An asynchronous query came in while the loader is stopped }
  }

  /**
   * Reloads the list when MediaStore's audio changes, e.g. when a finished download is scanned. The
   * loader only ever delivered its cached list, so new songs didn't show in Songs, Albums, Artists
   * or Last added until the screen was recreated (GitHub #1445).
   */
  private ForceLoadContentObserver mediaObserver;

  @Override
  protected void onStartLoading() {
    if (mediaObserver == null) {
      mediaObserver = new ForceLoadContentObserver();
      try {
        getContext()
            .getContentResolver()
            .registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
      } catch (Throwable t) {
        // No MediaStore access (permission not granted yet): reload when started instead.
        mediaObserver = null;
      }
    }
    if (this.mData != null) {
      deliverResult(this.mData);
    }
    // A change while the screen was stopped is reloaded now that it shows again.
    if (takeContentChanged() || this.mData == null) {
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
    if (mediaObserver != null) {
      getContext().getContentResolver().unregisterContentObserver(mediaObserver);
      mediaObserver = null;
    }
  }
}
