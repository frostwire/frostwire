
package com.andrew.apollo.loaders;

import android.content.Context;
import android.database.AbstractCursor;
import android.database.Cursor;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.provider.MediaStore.Audio.AudioColumns;
import com.andrew.apollo.utils.MusicUtils;

import java.util.Arrays;

/**
 * A custom {@link Cursor} used to return the queue and allow for easy dragging
 * and dropping of the items in it.
 */
public class NowPlayingCursor extends AbstractCursor {

    private static final String[] PROJECTION = new String[] {
            /* 0 */
            BaseColumns._ID,
            /* 1 */
            AudioColumns.TITLE,
            /* 2 */
            AudioColumns.ARTIST,
            /* 3 */
            AudioColumns.ALBUM,
            /* 4 */
            AudioColumns.DURATION
    };

    private final Context mContext;

    private long[] mNowPlaying;

    private long[] mCursorIndexes;

    private int mSize;

    private Cursor mQueueCursor;

    /**
     * Constructor of <code>NowPlayingCursor</code>
     * 
     * @param context The {@link Context} to use
     */
    NowPlayingCursor(final Context context) {
        mContext = context;
        makeNowPlayingCursor();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return mSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onMove(final int oldPosition, final int newPosition) {
        if (oldPosition == newPosition) {
            return true;
        }

        if (mNowPlaying == null || mCursorIndexes == null || newPosition >= mNowPlaying.length) {
            return false;
        }

        final long id = mNowPlaying[newPosition];
        final int cursorIndex = Arrays.binarySearch(mCursorIndexes, id);
        mQueueCursor.moveToPosition(cursorIndex);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getString(final int column) {
        try {
            return mQueueCursor.getString(column);
        } catch (final Exception ignored) {
            onChange(true);
            return "";
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public short getShort(final int column) {
        return mQueueCursor.getShort(column);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getInt(final int column) {
        try {
            return mQueueCursor.getInt(column);
        } catch (final Exception ignored) {
            onChange(true);
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getLong(final int column) {
        try {
            return mQueueCursor.getLong(column);
        } catch (final Exception ignored) {
            onChange(true);
            return 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getFloat(final int column) {
        return mQueueCursor.getFloat(column);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getDouble(final int column) {
        return mQueueCursor.getDouble(column);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getType(final int column) {
        return mQueueCursor.getType(column);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isNull(final int column) {
        return mQueueCursor.isNull(column);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getColumnNames() {
        return PROJECTION;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("deprecation")
    @Override
    public void deactivate() {
        if (mQueueCursor != null) {
            mQueueCursor.deactivate();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean requery() {
        makeNowPlayingCursor();
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            if (mQueueCursor != null) {
                mQueueCursor.close();
                mQueueCursor = null;
            }
        } catch (Throwable ignored) {}
        super.close();
    }

    /**
     * Actually makes the queue
     */
    private void makeNowPlayingCursor() {
        mQueueCursor = null;
        mNowPlaying = MusicUtils.getQueue();
        mSize = mNowPlaying.length;
        if (mSize == 0) {
            return;
        }

        final StringBuilder selection = new StringBuilder();
        selection.append(MediaStore.Audio.Media._ID + " IN (");
        for (int i = 0; i < mSize; i++) {
            selection.append(mNowPlaying[i]);
            if (i < mSize - 1) {
                selection.append(",");
            }
        }
        selection.append(")");

        try {
            mQueueCursor = mContext.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, PROJECTION, selection.toString(),
                    null, MediaStore.Audio.Media._ID);
        } catch (Throwable e) {
            // possible android.database.sqlite.SQLiteDiskIOException (Runtime Exception)
            mQueueCursor = null;
        }

        if (mQueueCursor == null) {
            mSize = 0;
            return;
        }

        final int playlistSize = mQueueCursor.getCount();
        mCursorIndexes = new long[playlistSize];
        mQueueCursor.moveToFirst();
        final int columnIndex = mQueueCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        for (int i = 0; i < playlistSize; i++) {
            mCursorIndexes[i] = mQueueCursor.getLong(columnIndex);
            mQueueCursor.moveToNext();
        }
        mQueueCursor.moveToFirst();

        int removed = 0;
        for (int i = mNowPlaying.length - 1; i >= 0; i--) {
            final long trackId = mNowPlaying[i];
            final int cursorIndex = Arrays.binarySearch(mCursorIndexes, trackId);
            if (cursorIndex < 0) {
                removed += MusicUtils.removeTrack(trackId);
            }
        }
        if (removed > 0) {
            mNowPlaying = MusicUtils.getQueue();
            mSize = mNowPlaying.length;
            if (mSize == 0) {
                mCursorIndexes = null;
            }
        }
    }
}
