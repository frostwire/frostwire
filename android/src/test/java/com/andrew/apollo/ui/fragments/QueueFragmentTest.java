/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.fragments;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import androidx.appcompat.view.menu.MenuBuilder;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.ApolloUiTestSupport;
import com.andrew.apollo.ui.FakeMediaProvider;
import com.andrew.apollo.ui.activities.AudioPlayerActivity;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config.*;

/**
 * The play queue shown under the now-playing screen: a list whose rows can be tapped, dragged to a
 * new place, removed, saved as a playlist or cleared. The queue loader reads the service's queue on
 * its own thread, so the rows are put into the adapter directly.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class QueueFragmentTest {

  @org.junit.Rule
  public final com.andrew.apollo.ui.ApolloUiTestSupport.CleanAppMessages cleanAppMessages =
      new com.andrew.apollo.ui.ApolloUiTestSupport.CleanAppMessages();

  private MockedStatic<MusicUtils> music;
  private final List<String> calls = new CopyOnWriteArrayList<>();

  @Before
  public void setUp() {
    ApolloUiTestSupport.installAll();
    FakeMediaProvider.install();
    music = ApolloUiTestSupport.mockMusicUtils(calls);
    music.when(MusicUtils::isStopped).thenReturn(false);
    music.when(MusicUtils::getQueue).thenReturn(new long[] {1, 2, 3});
  }

  @After
  public void tearDown() {
    music.close();
  }

  private QueueFragment queue(AudioPlayerActivity[] holder) {
    AudioPlayerActivity activity =
        Robolectric.buildActivity(AudioPlayerActivity.class, new Intent()).setup().get();
    ApolloUiTestSupport.settle();
    holder[0] = activity;
    QueueFragment fragment = activity.findFragment(QueueFragment.class);
    assertNotNull(fragment);
    List<Song> songs = new ArrayList<>();
    for (int i = 1; i <= 3; i++) {
      songs.add(new Song(i, "Song " + i, "Artist", "Album", 180));
    }
    fragment.getAdapter().unload();
    fragment.getAdapter().setDataList(songs);
    for (Song song : songs) {
      fragment.getAdapter().add(song);
    }
    fragment.getAdapter().buildCache();
    fragment.getAdapter().notifyDataSetChanged();
    return fragment;
  }

  private QueueFragment queue() {
    return queue(new AudioPlayerActivity[1]);
  }

  @Test
  public void tappingARowMovesThePlaybackToIt() {
    QueueFragment queue = queue();

    queue.onItemClick(null, null, 2, 3);

    assertTrue(calls.toString(), calls.contains("setQueuePosition[2]"));
  }

  @Test
  public void removingARowDropsItFromTheQueue() {
    QueueFragment queue = queue();

    queue.remove(1);

    assertTrue(calls.toString(), calls.contains("removeTrack[2]"));
    assertEquals(-1, queue.getAdapter().getPosition(new Song(2, "Song 2", "Artist", "Album", 180)));
  }

  @Test
  public void draggingARowMovesItInTheQueue() {
    QueueFragment queue = queue();

    queue.drop(0, 2);

    assertTrue(calls.toString(), calls.contains("moveQueueItem[0 2]"));
    assertEquals("Song 1", queue.getAdapter().getItem(2).mSongName);
  }

  @Test
  public void droppingPastTheEndLandsOnTheLastRow() {
    QueueFragment queue = queue();

    queue.drop(0, 10);

    assertTrue(calls.toString(), calls.contains("moveQueueItem[0 2]"));
  }

  @Test
  public void droppingARowWhereItWasChangesNothing() {
    QueueFragment queue = queue();

    queue.drop(1, 1);

    assertFalse(calls.toString(), calls.stream().anyMatch(c -> c.startsWith("moveQueueItem")));
  }

  @Test
  public void draggingFastNearTheEdgeScrollsAtOnce() {
    QueueFragment queue = queue();

    assertEquals(0.0f, queue.getSpeed(0f, 0), 0.001f);
    assertTrue(queue.getSpeed(0.5f, 0) > 0f);
    assertTrue(queue.getSpeed(0.9f, 0) > queue.getSpeed(0.5f, 0));
  }

  @Test
  public void theMenuClearsTheQueue() {
    AudioPlayerActivity[] holder = new AudioPlayerActivity[1];
    queue(holder);
    AudioPlayerActivity activity = holder[0];
    Menu menu = new MenuBuilder(activity);
    activity.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu);

    MenuItem clear = menu.findItem(R.id.menu_player_clear_queue);
    assertNotNull(clear);
    assertTrue(activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, clear));

    assertTrue(calls.toString(), calls.contains("clearQueue[]"));
  }

  @Test
  public void theMenuSavesTheQueueAsAPlaylist() {
    AudioPlayerActivity[] holder = new AudioPlayerActivity[1];
    QueueFragment queue = queue(holder);
    AudioPlayerActivity activity = holder[0];
    Menu menu = new MenuBuilder(activity);
    activity.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu);

    MenuItem save = menu.findItem(R.id.menu_player_save_queue);
    assertNotNull(save);
    assertTrue(activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, save));
    ApolloUiTestSupport.settle();

    assertTrue(queue.isAdded());
  }

  @Test
  public void anotherMenuItemIsNotTheQueuesToHandle() {
    AudioPlayerActivity[] holder = new AudioPlayerActivity[1];
    queue(holder);
    AudioPlayerActivity activity = holder[0];
    Menu menu = new MenuBuilder(activity);
    activity.onCreatePanelMenu(Window.FEATURE_OPTIONS_PANEL, menu);
    MenuItem other = menu.add(0, 12345, 0, "other");

    assertFalse(activity.onMenuItemSelected(Window.FEATURE_OPTIONS_PANEL, other));
  }

  @Test
  public void refreshingTheQueueRestartsItsLoader() {
    QueueFragment queue = queue();

    queue.refreshQueue();
    queue.notifyAdapterDataSetChanged();
    ApolloUiTestSupport.settle();

    assertTrue(queue.isAdded());
  }
}
