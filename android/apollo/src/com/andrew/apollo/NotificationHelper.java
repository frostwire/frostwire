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
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import androidx.core.app.NotificationCompat;
import android.widget.RemoteViews;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;

/**
 * Builds the notification for Apollo's service. Jelly Bean and higher uses the
 * expanded notification by default.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class NotificationHelper {
    private static final String INTENT_AUDIO_PLAYER = "com.frostwire.android.AUDIO_PLAYER";

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
                                  final String trackName, final Bitmap albumArt,
                                  final boolean isPlaying) {

        // Default notification layout
        mNotificationTemplate = new RemoteViews(mService.getPackageName(),
                R.layout.notification_template_base);

        // Set up the content view
        initCollapsedLayout(trackName, artistName, albumArt);

        //  Save this for debugging
        PendingIntent pendingintent = getPendingIntent();

        // Notification Builder
        Notification aNotification = new NotificationCompat.Builder(mService, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(getNotificationIcon())
                .setContentIntent(pendingintent)
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
        try {
            mService.startForeground(Constants.NOTIFICATION_FROSTWIRE_PLAYER_STATUS, mNotification);
        } catch (Throwable ignored) {
            ignored.printStackTrace();
        }
        // TODO: research RuntimeException in Android 7
        // we are getting this error at Bitmap.nativeWriteToParcel(Native Method:0)
        // in very low numbers and only in android 7, better research it
        // and not hide it for now
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
        try {
            if (mNotification != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    NotificationChannel channel = new NotificationChannel(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID, "FrostWire", NotificationManager.IMPORTANCE_DEFAULT);
                    channel.setSound(null, null);
                    mNotificationManager.createNotificationChannel(channel);
                }
                mNotificationManager.notify(Constants.NOTIFICATION_FROSTWIRE_PLAYER_STATUS, mNotification);
            }
        } catch (SecurityException t) {
            // java.lang.SecurityException
        } catch (NullPointerException t2) {
            // possible java.lang.NullPointerException: Attempt to read from field 'android.os.Bundle android.app.Notification.extras' on a null object reference
            // when closing the player notification with the 'X' icon.
        }
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
    private PendingIntent retrievePlaybackActions(final int which) {
        Intent action;
        final ComponentName serviceName = new ComponentName(mService, MusicPlaybackService.class);
        switch (which) {
            case 1:
                // Play and pause
                action = new Intent(MusicPlaybackService.TOGGLEPAUSE_ACTION);
                break;
            case 2:
                // Skip tracks
                action = new Intent(MusicPlaybackService.NEXT_ACTION);
                break;
            case 3:
                // Previous tracks
                action = new Intent(MusicPlaybackService.PREVIOUS_ACTION);
                break;
            case 4:
                // Stop and collapse the notification
                action = new Intent(MusicPlaybackService.STOP_ACTION);
                break;
            default:
                return null;
        }
        action.setComponent(serviceName);
        return PendingIntent.getService(mService, which, action, 0);
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
