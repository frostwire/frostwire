/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.cache;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import com.frostwire.android.util.SystemUtils;
import java.io.File;
import java.io.FileDescriptor;

/**
 * Decodes an album's artwork into a bitmap, for the places that need the bitmap itself rather than
 * an image view: the playback notification and home-screen shortcuts. Tries MediaStore's album art,
 * then the picture embedded in one of the album's songs, then a {@code .art} file next to it. Reads
 * the disk; call it off the main thread.
 */
public final class AlbumArtwork {

  private static final Uri ARTWORK_URI = Uri.parse("content://media/external/audio/albumart");

  private static final int TARGET_SIZE = 512;

  private AlbumArtwork() {}

  /** The album's artwork, scaled down to about 512 px, or null if it has none. */
  public static Bitmap decode(final Context context, final long albumId) {
    if (context == null || albumId < 0) {
      return null;
    }
    try (ParcelFileDescriptor descriptor =
        context
            .getContentResolver()
            .openFileDescriptor(ContentUris.withAppendedId(ARTWORK_URI, albumId), "r")) {
      if (descriptor != null) {
        final Bitmap artwork = decodeSampled(descriptor.getFileDescriptor());
        if (artwork != null) {
          return artwork;
        }
      }
    } catch (Exception | OutOfMemoryError ignored) {
      // No MediaStore art for it: try the song files.
    }
    return decodeFromSongFile(context, albumId);
  }

  private static Bitmap decodeFromSongFile(final Context context, final long albumId) {
    String filePath = null;
    final Uri mediaUri =
        SystemUtils.hasAndroid10OrNewer()
            ? MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    try (Cursor cursor =
        context
            .getContentResolver()
            .query(
                mediaUri,
                new String[] {MediaStore.Audio.Media.DATA},
                MediaStore.Audio.Media.ALBUM_ID + "=?",
                new String[] {String.valueOf(albumId)},
                null)) {
      if (cursor != null && cursor.moveToFirst()) {
        filePath = cursor.getString(0);
      }
    } catch (Exception ignored) {
      // No MediaStore access.
    }
    if (filePath == null) {
      return null;
    }
    final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
    try {
      retriever.setDataSource(filePath);
      final byte[] art = retriever.getEmbeddedPicture();
      if (art != null) {
        final BitmapFactory.Options options = boundsOnly();
        BitmapFactory.decodeByteArray(art, 0, art.length, options);
        return BitmapFactory.decodeByteArray(art, 0, art.length, sampled(options));
      }
    } catch (Exception | OutOfMemoryError ignored) {
      // Unreadable tags: try the .art file.
    } finally {
      try {
        retriever.release();
      } catch (Exception ignored) {
        // Nothing to release.
      }
    }
    final int dot = filePath.lastIndexOf('.');
    final File artFile = new File((dot > 0 ? filePath.substring(0, dot) : filePath) + ".art");
    if (artFile.exists()) {
      try {
        final BitmapFactory.Options options = boundsOnly();
        BitmapFactory.decodeFile(artFile.getAbsolutePath(), options);
        return BitmapFactory.decodeFile(artFile.getAbsolutePath(), sampled(options));
      } catch (Exception | OutOfMemoryError ignored) {
        // Unreadable .art file.
      }
    }
    return null;
  }

  private static Bitmap decodeSampled(final FileDescriptor descriptor) {
    final BitmapFactory.Options options = boundsOnly();
    BitmapFactory.decodeFileDescriptor(descriptor, null, options);
    return BitmapFactory.decodeFileDescriptor(descriptor, null, sampled(options));
  }

  private static BitmapFactory.Options boundsOnly() {
    final BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    return options;
  }

  /** {@code bounds} turned into decode options that scale the image to about 512 px. */
  private static BitmapFactory.Options sampled(final BitmapFactory.Options bounds) {
    bounds.inSampleSize = inSampleSize(bounds.outWidth, bounds.outHeight, TARGET_SIZE);
    bounds.inJustDecodeBounds = false;
    return bounds;
  }

  /** The largest power of 2 that keeps both sides at least {@code target}. */
  static int inSampleSize(final int width, final int height, final int target) {
    int sampleSize = 1;
    while ((height / 2) / sampleSize >= target && (width / 2) / sampleSize >= target) {
      sampleSize *= 2;
    }
    return sampleSize;
  }
}
