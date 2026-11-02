/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Intent;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.MusicStateListener;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The music screens' listeners and broadcast receiver (GitHub #1456): a fragment re-attached to the
 * same screen registered itself again and was never removed, so it reloaded once per attach on each
 * broadcast; and a broadcast arriving after the screen was collected crashed the receiver.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class BaseActivityMusicStateListenerTest {

  private static final class CountingListener implements MusicStateListener {
    final AtomicInteger reloads = new AtomicInteger();

    @Override
    public void restartLoader() {
      reloads.incrementAndGet();
    }

    @Override
    public void onMetaChanged() {}
  }

  private static BroadcastReceiver playbackStatusFor(BaseActivity activity) throws Exception {
    Class<?> type = Class.forName(BaseActivity.class.getName() + "$PlaybackStatus");
    Constructor<?> constructor = type.getDeclaredConstructor(BaseActivity.class);
    constructor.setAccessible(true);
    return (BroadcastReceiver) constructor.newInstance(activity);
  }

  @Test
  public void aListenerRegisteredTwiceHearsEachBroadcastOnce() throws Exception {
    BaseActivity activity = new BaseActivity(0) {};
    CountingListener listener = new CountingListener();
    activity.setMusicStateListenerListener(listener);
    activity.setMusicStateListenerListener(listener);

    playbackStatusFor(activity)
        .onReceive(
            ApplicationProvider.getApplicationContext(), new Intent(MusicPlaybackService.REFRESH));

    assertEquals(1, listener.reloads.get());
  }

  @Test
  public void aRemovedListenerHearsNothing() throws Exception {
    BaseActivity activity = new BaseActivity(0) {};
    CountingListener listener = new CountingListener();
    activity.setMusicStateListenerListener(listener);
    activity.removeMusicStateListener(listener);

    playbackStatusFor(activity)
        .onReceive(
            ApplicationProvider.getApplicationContext(), new Intent(MusicPlaybackService.REFRESH));

    assertEquals(0, listener.reloads.get());
  }

  @Test
  public void aBroadcastAfterTheScreenIsGoneIsIgnored() throws Exception {
    BroadcastReceiver receiver = playbackStatusFor(null);
    for (String action :
        new String[] {MusicPlaybackService.META_CHANGED, MusicPlaybackService.REFRESH}) {
      receiver.onReceive(ApplicationProvider.getApplicationContext(), new Intent(action));
    }
  }
}
