/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import android.app.Notification;
import android.app.NotificationManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.Util;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaNotification;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaStyleNotificationHelper.MediaStyle;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.google.common.collect.ImmutableList;

final class ApolloMediaNotificationProvider implements MediaNotification.Provider {

    private static final String CHANNEL_NAME = "FrostWire Player";

    private final MusicPlaybackService service;
    private final NotificationManager notificationManager;

    ApolloMediaNotificationProvider(MusicPlaybackService service) {
        this.service = service;
        this.notificationManager =
                (NotificationManager) service.getSystemService(MusicPlaybackService.NOTIFICATION_SERVICE);
    }

    @Override
    public MediaNotification createNotification(MediaSession mediaSession,
                                                ImmutableList<CommandButton> mediaButtonPreferences,
                                                MediaNotification.ActionFactory actionFactory,
                                                Callback onNotificationChangedCallback) {
        Util.ensureNotificationChannel(
                notificationManager,
                Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID,
                CHANNEL_NAME);

        Player player = mediaSession.getPlayer();
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(service, Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID);

        MediaStyle mediaStyle = new MediaStyle(mediaSession);
        mediaStyle.setShowActionsInCompactView(addTransportActions(mediaSession, player, builder, actionFactory));

        MediaMetadata metadata = player.getMediaMetadata();
        builder.setContentTitle(firstNonEmpty(metadata.title, service.getTrackName()))
                .setContentText(firstNonEmpty(metadata.artist, service.getArtistName()));

        Bitmap albumArt = service.getAlbumArt();
        if (albumArt != null) {
            builder.setLargeIcon(albumArt);
        }

        long playbackStartTimeMs = getPlaybackStartTimeEpochMs(player);
        boolean showChronometer = playbackStartTimeMs != C.TIME_UNSET;
        builder.setWhen(showChronometer ? playbackStartTimeMs : 0L)
                .setShowWhen(showChronometer)
                .setUsesChronometer(showChronometer);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        Notification notification = builder
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setContentIntent(mediaSession.getSessionActivity())
                .setDeleteIntent(actionFactory.createNotificationDismissalIntent(mediaSession))
                .setOnlyAlertOnce(true)
                .setSmallIcon(R.drawable.frostwire_notification_flat)
                .setStyle(mediaStyle)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(false)
                .build();

        return new MediaNotification(Constants.JOB_ID_MUSIC_PLAYBACK_SERVICE, notification);
    }

    @Override
    public boolean handleCustomCommand(MediaSession session, String action, Bundle extras) {
        return false;
    }

    @Override
    public NotificationChannelInfo getNotificationChannelInfo() {
        return new NotificationChannelInfo(Constants.FROSTWIRE_NOTIFICATION_CHANNEL_ID, CHANNEL_NAME);
    }

    private int[] addTransportActions(MediaSession mediaSession,
                                      Player player,
                                      NotificationCompat.Builder builder,
                                      MediaNotification.ActionFactory actionFactory) {
        Player.Commands commands = player.getAvailableCommands();
        int[] compactView = new int[3];
        int compactCount = 0;
        int actionIndex = 0;

        if (commands.containsAny(Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)) {
            builder.addAction(
                    actionFactory.createMediaAction(
                            mediaSession,
                            IconCompat.createWithResource(service, androidx.media3.session.R.drawable.media3_icon_previous),
                            service.getString(androidx.media3.session.R.string.media3_controls_seek_to_previous_description),
                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM));
            compactView[compactCount++] = actionIndex++;
        }

        if (commands.contains(Player.COMMAND_PLAY_PAUSE)) {
            boolean showPlayButton =
                    Util.shouldShowPlayButton(player, mediaSession.getShowPlayButtonIfPlaybackIsSuppressed());
            builder.addAction(
                    actionFactory.createMediaAction(
                            mediaSession,
                            IconCompat.createWithResource(
                                    service,
                                    showPlayButton
                                            ? androidx.media3.session.R.drawable.media3_icon_play
                                            : androidx.media3.session.R.drawable.media3_icon_pause),
                            service.getString(
                                    showPlayButton
                                            ? androidx.media3.session.R.string.media3_controls_play_description
                                            : androidx.media3.session.R.string.media3_controls_pause_description),
                            Player.COMMAND_PLAY_PAUSE));
            compactView[compactCount++] = actionIndex++;
        }

        if (commands.containsAny(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)) {
            builder.addAction(
                    actionFactory.createMediaAction(
                            mediaSession,
                            IconCompat.createWithResource(service, androidx.media3.session.R.drawable.media3_icon_next),
                            service.getString(androidx.media3.session.R.string.media3_controls_seek_to_next_description),
                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM));
            compactView[compactCount++] = actionIndex;
        }

        int[] result = new int[compactCount];
        System.arraycopy(compactView, 0, result, 0, compactCount);
        return result;
    }

    @Nullable
    private static CharSequence firstNonEmpty(@Nullable CharSequence preferred, @Nullable String fallback) {
        if (preferred != null && preferred.length() > 0) {
            return preferred;
        }
        if (fallback != null && !fallback.isEmpty()) {
            return fallback;
        }
        return null;
    }

    private static long getPlaybackStartTimeEpochMs(Player player) {
        if (player.isPlaying()
                && !player.isPlayingAd()
                && !player.isCurrentMediaItemDynamic()
                && player.getPlaybackParameters().speed == 1f) {
            return System.currentTimeMillis() - player.getContentPosition();
        }
        return C.TIME_UNSET;
    }
}
