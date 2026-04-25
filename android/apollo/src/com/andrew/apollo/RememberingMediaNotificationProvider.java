/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaNotification;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

final class RememberingMediaNotificationProvider implements MediaNotification.Provider {

    private final MediaNotification.Provider delegate;
    private final ConcurrentMap<String, MediaNotification> notifications = new ConcurrentHashMap<>();

    RememberingMediaNotificationProvider(MediaNotification.Provider delegate) {
        this.delegate = delegate;
    }

    @Override
    public MediaNotification createNotification(MediaSession mediaSession,
                                                ImmutableList<CommandButton> mediaButtonPreferences,
                                                MediaNotification.ActionFactory actionFactory,
                                                Callback onNotificationChangedCallback) {
        Callback rememberingCallback = notification -> {
            remember(mediaSession, notification);
            onNotificationChangedCallback.onNotificationChanged(notification);
        };

        MediaNotification notification =
                delegate.createNotification(
                        mediaSession,
                        mediaButtonPreferences,
                        actionFactory,
                        rememberingCallback);
        remember(mediaSession, notification);
        return notification;
    }

    @Override
    public boolean handleCustomCommand(MediaSession session, String action, Bundle extras) {
        return delegate.handleCustomCommand(session, action, extras);
    }

    @Override
    public NotificationChannelInfo getNotificationChannelInfo() {
        return delegate.getNotificationChannelInfo();
    }

    @Nullable
    MediaNotification getCachedNotification(MediaSession mediaSession) {
        return notifications.get(mediaSession.getId());
    }

    void clear() {
        notifications.clear();
    }

    private void remember(MediaSession mediaSession, MediaNotification notification) {
        notifications.put(mediaSession.getId(), notification);
    }
}
