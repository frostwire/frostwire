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

package com.andrew.apollo.appwidgets;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.View;
import android.widget.RemoteViews;

import com.andrew.apollo.MusicPlaybackService;
import com.frostwire.android.R;
import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.ui.activities.HomeActivity;
import com.andrew.apollo.utils.ApolloUtils;

/**
 * 4x1 App-Widget
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
@SuppressLint("NewApi")
public class AppWidgetSmall extends AppWidgetBase {

    public static final String CMDAPPWIDGETUPDATE = "app_widget_small_update";

    private static AppWidgetSmall mInstance;

    public static synchronized AppWidgetSmall getInstance() {
        if (mInstance == null) {
            mInstance = new AppWidgetSmall();
        }
        return mInstance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdate(final Context context, final AppWidgetManager appWidgetManager,
            final int[] appWidgetIds) {
        defaultAppWidget(context, appWidgetIds);
        final Intent updateIntent = new Intent(MusicPlaybackService.SERVICECMD);
        updateIntent.putExtra(MusicPlaybackService.CMDNAME, AppWidgetSmall.CMDAPPWIDGETUPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        updateIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcast(updateIntent);
    }

    /**
     * Initialize given widgets to default state, where we launch Music on
     * default click and hide actions if service not running.
     */
    private void defaultAppWidget(final Context context, final int[] appWidgetIds) {
        final RemoteViews appWidgetViews = new RemoteViews(context.getPackageName(),
                R.layout.app_widget_small);
        appWidgetViews.setViewVisibility(R.id.app_widget_small_info_container, View.INVISIBLE);
        linkButtons(context, appWidgetViews, false);
        pushUpdate(context, appWidgetIds, appWidgetViews);
    }

    private void pushUpdate(final Context context, final int[] appWidgetIds, final RemoteViews views) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null) {
            appWidgetManager.updateAppWidget(appWidgetIds, views);
        } else {
            appWidgetManager.updateAppWidget(new ComponentName(context, getClass()), views);
        }
    }

    /**
     * Check against {@link AppWidgetManager} if there are any instances of this
     * widget.
     */
    private boolean hasInstances(final Context context) {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        final int[] mAppWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context,
                getClass()));
        return mAppWidgetIds.length > 0;
    }

    /**
     * Handle a change notification coming over from
     * {@link MusicPlaybackService}
     */
    public void notifyChange(final MusicPlaybackService service, final String what) {
        if (hasInstances(service)) {
            if (MusicPlaybackService.META_CHANGED.equals(what)
                    || MusicPlaybackService.PLAYSTATE_CHANGED.equals(what)) {
                performUpdate(service, null);
            }
        }
    }

    /**
     * Update all active widget instances by pushing changes
     */
    public void performUpdate(final MusicPlaybackService service, final int[] appWidgetIds) {
        final RemoteViews appWidgetView = new RemoteViews(service.getPackageName(),
                R.layout.app_widget_small);

        final CharSequence trackName = service.getTrackName();
        final CharSequence artistName = service.getArtistName();
        final Bitmap bitmap = service.getAlbumArt();

        // Set the titles and artwork
        if (TextUtils.isEmpty(trackName) && TextUtils.isEmpty(artistName)) {
            appWidgetView.setViewVisibility(R.id.app_widget_small_info_container, View.INVISIBLE);
        } else {
            appWidgetView.setViewVisibility(R.id.app_widget_small_info_container, View.VISIBLE);
            appWidgetView.setTextViewText(R.id.app_widget_small_line_one, trackName);
            appWidgetView.setTextViewText(R.id.app_widget_small_line_two, artistName);
        }
        appWidgetView.setImageViewBitmap(R.id.app_widget_small_image, bitmap);

        // Set correct drawable for pause state
        final boolean isPlaying = service.isPlaying();
        if (isPlaying) {
            appWidgetView.setImageViewResource(R.id.app_widget_small_play,
                    R.drawable.btn_playback_pause);
            appWidgetView.setContentDescription(R.id.app_widget_small_play,
                    service.getString(R.string.accessibility_pause));
        } else {
            appWidgetView.setImageViewResource(R.id.app_widget_small_play,
                    R.drawable.btn_playback_play);
            appWidgetView.setContentDescription(R.id.app_widget_small_play,
                     service.getString(R.string.accessibility_play));
        }

        // Link actions buttons to intents
        linkButtons(service, appWidgetView, isPlaying);

        // Update the app-widget
        pushUpdate(service, appWidgetIds, appWidgetView);
    }

    /**
     * Link up various button actions using {@link PendingIntents}.
     *
     * @param playerActive True if player is active in background, which means
     *            widget click will launch {@link AudioPlayerActivity},
     *            otherwise we launch {@link MusicBrowserActivity}.
     */
    private void linkButtons(final Context context, final RemoteViews views,
            final boolean playerActive) {
        Intent action;
        PendingIntent pendingIntent;

        final ComponentName serviceName = new ComponentName(context, MusicPlaybackService.class);

        // Now playing
        if (playerActive) {
            action = new Intent(context, AudioPlayerActivity.class);
            pendingIntent = PendingIntent.getActivity(context, 0, action, 0);
            views.setOnClickPendingIntent(R.id.app_widget_small_info_container, pendingIntent);
            views.setOnClickPendingIntent(R.id.app_widget_small_image, pendingIntent);
        } else {
            // Home
            action = new Intent(context, HomeActivity.class);
            pendingIntent = PendingIntent.getActivity(context, 0, action, 0);
            views.setOnClickPendingIntent(R.id.app_widget_small_info_container, pendingIntent);
            views.setOnClickPendingIntent(R.id.app_widget_small_image, pendingIntent);
        }

        // Previous track
        pendingIntent = buildPendingIntent(context, MusicPlaybackService.PREVIOUS_ACTION, serviceName);
        views.setOnClickPendingIntent(R.id.app_widget_small_previous, pendingIntent);

        // Play and pause
        pendingIntent = buildPendingIntent(context, MusicPlaybackService.TOGGLEPAUSE_ACTION, serviceName);
        views.setOnClickPendingIntent(R.id.app_widget_small_play, pendingIntent);

        // Next track
        pendingIntent = buildPendingIntent(context, MusicPlaybackService.NEXT_ACTION, serviceName);
        views.setOnClickPendingIntent(R.id.app_widget_small_next, pendingIntent);
    }

}
