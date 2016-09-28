/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Toast;
import com.andrew.apollo.Config;
import com.andrew.apollo.cache.ImageCache;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.ui.activities.ShortcutActivity;
import com.devspark.appmsg.AppMsg;
import com.frostwire.android.R;

/**
 * Mostly general and UI helpers.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class ApolloUtils {

    /* This class is never initiated */
    public ApolloUtils() {
    }

    /**
     * Used to determine if the device is currently in landscape mode
     *
     * @param context The {@link Context} to use.
     * @return True if the device is in landscape mode, false otherwise.
     */
    public static final boolean isLandscape(final Context context) {
        final int orientation = context.getResources().getConfiguration().orientation;
        return orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    /**
     * Execute an {@link AsyncTask} on a thread pool
     *
     * @param task Task to execute
     * @param args Optional arguments to pass to
     *             {@link AsyncTask#execute(Object[])}
     * @param <T>  Task argument type
     */
    public static <T> void execute(final AsyncTask<T, ?, ?> task,
                                   final T... args) {
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, args);
    }

    /**
     * Display a {@link Toast} letting the user know what an item does when long
     * pressed.
     *
     * @param view The {@link View} to copy the content description from.
     */
    public static void showCheatSheet(final View view) {

        final int[] screenPos = new int[2]; // origin is device display
        final Rect displayFrame = new Rect(); // includes decorations (e.g.
        // status bar)
        view.getLocationOnScreen(screenPos);
        view.getWindowVisibleDisplayFrame(displayFrame);

        final Context context = view.getContext();
        final int viewWidth = view.getWidth();
        final int viewHeight = view.getHeight();
        final int viewCenterX = screenPos[0] + viewWidth / 2;
        final int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        final int estimatedToastHeight = (int) (48 * context.getResources().getDisplayMetrics().density);

        final Toast cheatSheet = Toast.makeText(context, view.getContentDescription(),
                Toast.LENGTH_SHORT);
        final boolean showBelow = screenPos[1] < estimatedToastHeight;
        if (showBelow) {
            // Show below
            // Offsets are after decorations (e.g. status bar) are factored in
            cheatSheet.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, viewCenterX
                    - screenWidth / 2, screenPos[1] - displayFrame.top + viewHeight);
        } else {
            // Show above
            // Offsets are after decorations (e.g. status bar) are factored in
            cheatSheet.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, viewCenterX
                    - screenWidth / 2, displayFrame.bottom - screenPos[1]);
        }
        cheatSheet.show();
    }

    /**
     * Runs a piece of code after the next layout run
     *
     * @param view     The {@link View} used.
     * @param runnable The {@link Runnable} used after the next layout run
     */
    public static void doAfterLayout(final View view, final Runnable runnable) {
        final OnGlobalLayoutListener listener = new OnGlobalLayoutListener() {
            @SuppressWarnings("deprecation")
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                runnable.run();
            }
        };
        view.getViewTreeObserver().addOnGlobalLayoutListener(listener);
    }

    /**
     * Creates a new instance of the {@link ImageCache} and {@link ImageFetcher}
     *
     * @param activity The {@link Activity} to use.
     * @return A new {@link ImageFetcher} used to fetch images asynchronously.
     */
    public static final ImageFetcher getImageFetcher(final Activity activity) {
        final ImageFetcher imageFetcher = ImageFetcher.getInstance(activity);
        imageFetcher.setImageCache(ImageCache.findOrCreateCache(activity));
        return imageFetcher;
    }

    /**
     * Used to create shortcuts for an artist, album, or playlist that is then
     * placed on the default launcher homescreen
     *
     * @param displayName The shortcut name
     * @param id          The ID of the artist, album, playlist, or genre
     * @param mimeType    The MIME type of the shortcut
     * @param context     The {@link Context} to use to
     */
    public static void createShortcutIntent(final String displayName, final String artistName,
                                            final Long id, final String mimeType, final Activity context) {
        try {
            final ImageFetcher fetcher = getImageFetcher(context);
            Bitmap bitmap = null;
            if (mimeType.equals(MediaStore.Audio.Albums.CONTENT_TYPE)) {
                bitmap = fetcher.getCachedBitmap(
                        ImageFetcher.generateAlbumCacheKey(displayName, artistName));
            } else {
                bitmap = fetcher.getCachedBitmap(displayName);
            }
            if (bitmap == null) {
                bitmap = BitmapFactory.decodeResource(context.getResources(),
                        R.drawable.default_artwork);
            }

            // Intent used when the icon is touched
            final Intent shortcutIntent = new Intent(context, ShortcutActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            shortcutIntent.putExtra(Config.ID, id);
            shortcutIntent.putExtra(Config.NAME, displayName);
            shortcutIntent.putExtra(Config.MIME_TYPE, mimeType);

            // Intent that actually sets the shortcut
            final Intent intent = new Intent();
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, BitmapUtils.resizeAndCropCenter(bitmap, 96));
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName);
            intent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            context.sendBroadcast(intent);
            AppMsg.makeText(context,
                    context.getString(R.string.pinned_to_home_screen, displayName),
                    AppMsg.STYLE_CONFIRM).show();
        } catch (final Exception e) {
            Log.e("ApolloUtils", "createShortcutIntent", e);
            AppMsg.makeText(
                    context,
                    context.getString(R.string.could_not_be_pinned_to_home_screen, displayName),
                    AppMsg.STYLE_ALERT).show();
        }
    }
}
