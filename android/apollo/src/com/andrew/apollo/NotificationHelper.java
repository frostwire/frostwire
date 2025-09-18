/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.andrew.apollo;

import static com.frostwire.android.core.Constants.NOTIFICATION_FROSTWIRE_PLAYER_STATUS;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.widget.RemoteViews;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.util.Logger;

/**
 * Builds the notification for Apollo's service. Jelly Bean and higher uses the
 * expanded notification by default.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class NotificationHelper {
    private static final Logger LOG = Logger.getLogger(NotificationHelper.class);
    private static final String INTENT_AUDIO_PLAYER = "com.frostwire.android.AUDIO_PLAYER";

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

    public final static Object NOTIFICATION_LOCK = new Object();

    /**
     * Constructor of <code>NotificationHelper</code>
     *
     * @param service The {@link Context} to use
     */
    NotificationHelper(final MusicPlaybackService service) {
        mService = service;
        mNotificationManager = (NotificationManager) service
                .getSystemService(Context.NOTIFICATION_SERVICE);
    }

    void createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = null;
            try {
                channel = mNotificationManager.getNotificationChannel(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID); // maybe we need another channel for the player?
                LOG.info("updatePlayState() got a channel with notificationManager.getNotificationChannel()? -> " + channel, true);
            } catch (Throwable t) {
                LOG.error("updatePlayState() " + t.getMessage(), t);
            }
            if (channel == null) {
                channel = new NotificationChannel(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID, "FrostWire", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setSound(null, null);
                LOG.info("updatePlayState() had to create a new channel with notificationManager.createNotificationChannel()", true);
            }
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    public Notification buildBasicNotification(Context context, String title, String text, String channelId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.menu_icon_my_music) // Replace with your app icon
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true); // Mark it as ongoing for a foreground service
        return builder.build();
    }

    /**
     * Call this to build the {@link Notification}.
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    void buildNotification(final String albumName,
                           final String artistName,
                           final String trackName,
                           final Bitmap albumArt,
                           final boolean isPlaying) {

        // Default notification layout
        mNotificationTemplate = new RemoteViews(mService.getPackageName(),
                R.layout.notification_template_base);

        // Set up the content view
        initCollapsedLayout(trackName, artistName, albumArt);

        //  Save this for debugging
        PendingIntent pendingintent = pendingIntent();

        // Notification Builder
        Notification aNotification = new NotificationCompat.Builder(mService, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(notificationIcon())
                .setContentIntent(pendingintent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
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
        createNotificationChannel();

        // does service.startForeground(notification)
        mService.onNotificationCreated(mNotification);
    }

    /**
     * Changes the playback controls in and out of a paused state
     *
     * @param isPlaying True if music is playing, false otherwise
     */
    void updatePlayState(final boolean isPlaying, final boolean isStopped) {
        LOG.info("updatePlayState(isPlaying=" + isPlaying + ", isStopped=" + isStopped + ")");
        if (mNotification == null && !isStopped) {
            LOG.info("updatePlayState() aborted! mNotification is null");
            return;
        }
        if (mNotificationManager == null) {
            LOG.info("updatePlayState() aborted! mNotificationManager is null");
            return;
        }
        boolean isPaused = !isPlaying && !isStopped;
        if (mNotificationTemplate != null) {

            mNotificationTemplate.setImageViewResource(R.id.notification_base_play,
                    isPaused ? R.drawable.btn_notification_playback_play : R.drawable.btn_notification_playback_pause);
        }

        if (mExpandedView != null) {
            mExpandedView.setImageViewResource(R.id.notification_expanded_base_play,
                    isPaused ? R.drawable.btn_notification_playback_play : R.drawable.btn_notification_playback_pause);
        }
        try {
            synchronized (NOTIFICATION_LOCK) {
                if (isStopped) {
                    mNotificationManager.cancel(NOTIFICATION_FROSTWIRE_PLAYER_STATUS); // otherwise we end up with 2 notifications
                }
                if (mNotification != null) {
                    LOG.info("updatePlayState() calling back MusicPlaybackService::onNotificationCreated");
                    mService.onNotificationCreated(mNotification);
                }
            }
        }  // possible java.lang.NullPointerException: Attempt to read from field 'android.os.Bundle android.app.Notification.extras' on a null object reference
        // when closing the player notification with the 'X' icon.
        catch (Throwable t) {
            // java.lang.SecurityException
            LOG.error("updatePlayState() " + t.getMessage(), t);
        }
    }

    /**
     * Remove notification
     */
    void killNotification() {
        if (mNotificationManager != null) {
            synchronized (NOTIFICATION_LOCK) {
                mNotificationManager.cancel(NOTIFICATION_FROSTWIRE_PLAYER_STATUS);
            }
            mService.stopForeground(true);
            mNotification = null;
        }
    }

    private int notificationIcon() {
        return R.drawable.frostwire_notification_flat;
    }


    /**
     * Open to the now playing screen
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
    private PendingIntent pendingIntent() {
        return PendingIntent.getActivity(mService, 0, new Intent(INTENT_AUDIO_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Lets the buttons in the remote view control playback in the expanded
     * layout
     */
    @RequiresApi(api = Build.VERSION_CODES.S)
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
    @RequiresApi(api = Build.VERSION_CODES.S)
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
    @RequiresApi(api = Build.VERSION_CODES.S)
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
        return PendingIntent.getService(mService, which, action, PendingIntent.FLAG_MUTABLE);
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
