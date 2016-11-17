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

package com.andrew.apollo;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import com.frostwire.android.R;

/**
 * Builds the notification for Apollo's service. Jelly Bean and higher uses the
 * expanded notification by default.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class NotificationHelper {

    private static final String INTENT_AUDIO_PLAYER = "com.frostwire.android.AUDIO_PLAYER";

    /**
     * Notification ID
     */
    private static final int APOLLO_MUSIC_SERVICE = 1;

    /**
     * Used to allow player controls on lock screen notification on API 21+ phones
     */
    private static final int VISIBILITY_PUBLIC = 1;

    /**
     * NotificationManager
     */
    private final NotificationManager mNotificationManager;

    /**
     * Context
     */
    private final MusicPlaybackService mService;

    /**
     * Custom notification layout
     */
    private RemoteViews mNotificationTemplate;

    /**
     * The Notification
     */
    private Notification mNotification = null;

    /**
     * API 16+ bigContentView
     */
    private RemoteViews mExpandedView;

    /**
     * Constructor of <code>NotificationHelper</code>
     *
     * @param service The {@link Context} to use
     */
    public NotificationHelper(final MusicPlaybackService service) {
        mService = service;
        mNotificationManager = (NotificationManager)service
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Call this to build the {@link Notification}.
     */
    public void buildNotification(final String albumName, final String artistName,
            final String trackName, final Long albumId, final Bitmap albumArt,
            final boolean isPlaying) {

        // Default notification layout
        mNotificationTemplate = new RemoteViews(mService.getPackageName(),
                R.layout.notification_template_base);

        // Set up the content view
        initCollapsedLayout(trackName, artistName, albumArt);

        //  Save this for debugging
        PendingIntent pendingintent = getPendingIntent();

        // Notification Builder
        Notification aNotification = new NotificationCompat.Builder(mService)
                .setSmallIcon(getNotificationIcon())
                .setContentIntent(pendingintent)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .setVisibility(VISIBILITY_PUBLIC)
                .setContent(mNotificationTemplate)
                .build();
        // Control playback from the notification
        initPlaybackActions(isPlaying);

        // Expanded notification style
        mExpandedView = new RemoteViews(mService.getPackageName(),
                R.layout.notification_template_expanded_base);

        aNotification.bigContentView = mExpandedView;

        // Control playback from the notification
        initExpandedPlaybackActions(isPlaying);
        // Set up the expanded content view
        initExpandedLayout(trackName, albumName, artistName, albumArt);

        mNotification = aNotification;
        mService.startForeground(APOLLO_MUSIC_SERVICE, mNotification);
    }

    private int getNotificationIcon() {
        return R.drawable.frostwire_notification_flat;
    }

    /**
     * Remove notification
     */
    public void killNotification() {
        mService.stopForeground(true);
        mNotification = null;
    }

    /**
     * Changes the playback controls in and out of a paused state
     *
     * @param isPlaying True if music is playing, false otherwise
     */
    public void updatePlayState(final boolean isPlaying) {
        if (mNotification == null || mNotificationManager == null) {
            return;
        }
        if (mNotificationTemplate != null) {
            mNotificationTemplate.setImageViewResource(R.id.notification_base_play,
                    isPlaying ? R.drawable.btn_notification_playback_pause : R.drawable.btn_notification_playback_play);
        }

        if (mExpandedView != null) {
            mExpandedView.setImageViewResource(R.id.notification_expanded_base_play,
                    isPlaying ? R.drawable.btn_notification_playback_pause : R.drawable.btn_notification_playback_play);
        }
        mNotificationManager.notify(APOLLO_MUSIC_SERVICE, mNotification);
    }

    /**
     * Open to the now playing screen
     */
    private PendingIntent getPendingIntent() {
        return PendingIntent.getActivity(mService, 0, new Intent(INTENT_AUDIO_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);
    }

    /**
     * Lets the buttons in the remote view control playback in the expanded
     * layout
     */
    private void initExpandedPlaybackActions(boolean isPlaying) {
        // Play and pause
        mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_play,
                retrievePlaybackActions(1));

        // Skip tracks
        mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_next,
                retrievePlaybackActions(2));

        // Previous tracks
        mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_previous,
                retrievePlaybackActions(3));

        // Stop and collapse the notification
        mExpandedView.setOnClickPendingIntent(R.id.notification_expanded_base_collapse,
                retrievePlaybackActions(4));

        // Update the play button image
        mExpandedView.setImageViewResource(R.id.notification_expanded_base_play,
                isPlaying ? R.drawable.btn_notification_playback_pause : R.drawable.btn_notification_playback_play);
    }

    /**
     * Lets the buttons in the remote view control playback in the normal layout
     */
    private void initPlaybackActions(boolean isPlaying) {
        // Play and pause
        mNotificationTemplate.setOnClickPendingIntent(R.id.notification_base_play,
                retrievePlaybackActions(1));

        // Skip tracks
        mNotificationTemplate.setOnClickPendingIntent(R.id.notification_base_next,
                retrievePlaybackActions(2));

        // Previous tracks
        mNotificationTemplate.setOnClickPendingIntent(R.id.notification_base_previous,
                retrievePlaybackActions(3));

        // Stop and collapse the notification
        mNotificationTemplate.setOnClickPendingIntent(R.id.notification_base_collapse,
                retrievePlaybackActions(4));

        // Update the play button image
        mNotificationTemplate.setImageViewResource(R.id.notification_base_play,
                isPlaying ? R.drawable.btn_notification_playback_pause : R.drawable.btn_notification_playback_play);
    }

    /**
     * @param which Which {@link PendingIntent} to return
     * @return A {@link PendingIntent} ready to control playback
     */
    private final PendingIntent retrievePlaybackActions(final int which) {
        Intent action;
        PendingIntent pendingIntent;
        final ComponentName serviceName = new ComponentName(mService, MusicPlaybackService.class);
        switch (which) {
            case 1:
                // Play and pause
                action = new Intent(MusicPlaybackService.TOGGLEPAUSE_ACTION);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(mService, 1, action, 0);
                return pendingIntent;
            case 2:
                // Skip tracks
                action = new Intent(MusicPlaybackService.NEXT_ACTION);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(mService, 2, action, 0);
                return pendingIntent;
            case 3:
                // Previous tracks
                action = new Intent(MusicPlaybackService.PREVIOUS_ACTION);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(mService, 3, action, 0);
                return pendingIntent;
            case 4:
                // Stop and collapse the notification
                action = new Intent(MusicPlaybackService.STOP_ACTION);
                action.setComponent(serviceName);
                pendingIntent = PendingIntent.getService(mService, 4, action, 0);
                return pendingIntent;
            default:
                break;
        }
        return null;
    }

    /**
     * Sets the track name, artist name, and album art in the normal layout
     */
    private void initCollapsedLayout(final String trackName, final String artistName,
            final Bitmap albumArt) {
        // Track name (line one)
        mNotificationTemplate.setTextViewText(R.id.notification_base_line_one, trackName != null ? trackName : "---");
        // Artist name (line two)
        mNotificationTemplate.setTextViewText(R.id.notification_base_line_two, artistName != null ? artistName : "---");
        // Album art
        if (albumArt != null) {
            mNotificationTemplate.setImageViewBitmap(R.id.notification_base_image, albumArt);
        }
    }

    /**
     * Sets the track name, album name, artist name, and album art in the
     * expanded layout
     */
    private void initExpandedLayout(final String trackName, final String artistName,
            final String albumName, final Bitmap albumArt) {
        // Track name (line one)
        mExpandedView.setTextViewText(R.id.notification_expanded_base_line_one, trackName != null ? trackName : "---");
        // Album name (line two)
        mExpandedView.setTextViewText(R.id.notification_expanded_base_line_two, albumName != null ? albumName : "---");
        // Artist name (line three)
        mExpandedView.setTextViewText(R.id.notification_expanded_base_line_three, artistName != null ? artistName : "---");
        // Album art
        if (albumArt != null) {
            mExpandedView.setImageViewBitmap(R.id.notification_expanded_base_image, albumArt);
        }
    }
}
