/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import android.app.Notification;
import android.os.Bundle;

import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaNotification;
import androidx.media3.session.MediaSession;

import com.google.common.collect.ImmutableList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class RememberingMediaNotificationProviderTest {

    @Test
    public void createNotification_cachesInitialNotification() {
        MediaNotification initial = new MediaNotification(7, new Notification());
        FakeProvider delegate = new FakeProvider(initial);
        RememberingMediaNotificationProvider provider =
                new RememberingMediaNotificationProvider(delegate);

        MediaSession session = mock(MediaSession.class);
        when(session.getId()).thenReturn("session-1");

        MediaNotification created =
                provider.createNotification(
                        session,
                        ImmutableList.<CommandButton>of(),
                        mock(MediaNotification.ActionFactory.class),
                        notification -> {
                        });

        assertSame(initial, created);
        assertSame(initial, provider.getCachedNotification(session));
    }

    @Test
    public void providerCallback_replacesCachedNotificationAndForwards() {
        MediaNotification initial = new MediaNotification(7, new Notification());
        MediaNotification updated = new MediaNotification(7, new Notification());
        FakeProvider delegate = new FakeProvider(initial);
        RememberingMediaNotificationProvider provider =
                new RememberingMediaNotificationProvider(delegate);

        MediaSession session = mock(MediaSession.class);
        when(session.getId()).thenReturn("session-2");

        AtomicReference<MediaNotification> forwarded = new AtomicReference<>();
        provider.createNotification(
                session,
                ImmutableList.<CommandButton>of(),
                mock(MediaNotification.ActionFactory.class),
                forwarded::set);

        delegate.dispatchUpdate(updated);

        assertSame(updated, provider.getCachedNotification(session));
        assertSame(updated, forwarded.get());
    }

    private static final class FakeProvider implements MediaNotification.Provider {

        private final MediaNotification initialNotification;
        private Callback callback;

        FakeProvider(MediaNotification initialNotification) {
            this.initialNotification = initialNotification;
        }

        @Override
        public MediaNotification createNotification(MediaSession mediaSession,
                                                    ImmutableList<CommandButton> mediaButtonPreferences,
                                                    MediaNotification.ActionFactory actionFactory,
                                                    Callback onNotificationChangedCallback) {
            this.callback = onNotificationChangedCallback;
            return initialNotification;
        }

        @Override
        public boolean handleCustomCommand(MediaSession session, String action, Bundle extras) {
            return false;
        }

        @Override
        public NotificationChannelInfo getNotificationChannelInfo() {
            return new NotificationChannelInfo("channel", "Channel");
        }

        void dispatchUpdate(MediaNotification notification) {
            callback.onNotificationChanged(notification);
        }
    }
}
