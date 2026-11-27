/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.player.CoreMediaPlayer;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.FWImageLoader;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.TaskThrottle;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MiniPlayerViewTest {

  private MockedStatic<MusicUtils> music;
  private MockedStatic<NavUtils> nav;
  private MockedStatic<Engine> engines;
  private MockedStatic<TaskThrottle> throttle;
  private MockedStatic<FWImageLoader> images;
  private MockedStatic<SystemUtils> system;
  private Engine engine;
  private CoreMediaPlayer player;
  private FWImageLoader imageLoader;
  private AppCompatActivity activity;
  private MiniPlayerView view;

  @Before
  public void setUp() {
    ViewTestSupport.installUiThreadPlatform();
    music = mockStatic(MusicUtils.class);
    nav = mockStatic(NavUtils.class);
    throttle = mockStatic(TaskThrottle.class);
    throttle.when(() -> TaskThrottle.isReadyToSubmitTask(anyString(), anyLong())).thenReturn(true);
    imageLoader = mock(FWImageLoader.class);
    images = mockStatic(FWImageLoader.class);
    images.when(() -> FWImageLoader.getInstance(any())).thenReturn(imageLoader);
    images
        .when(() -> FWImageLoader.getAlbumArtUri(anyLong()))
        .thenAnswer(i -> Uri.parse("content://albums/" + i.getArgument(0)));
    system = mockStatic(SystemUtils.class, CALLS_REAL_METHODS);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            i -> {
              ((Runnable) i.getArgument(1)).run();
              return null;
            });
    system
        .when(() -> SystemUtils.postToUIThread(any(Runnable.class)))
        .thenAnswer(
            i -> {
              ((Runnable) i.getArgument(0)).run();
              return null;
            });
    player = mock(CoreMediaPlayer.class);
    engine = mock(Engine.class);
    when(engine.getMediaPlayer()).thenReturn(player);
    engines = mockStatic(Engine.class);
    engines.when(Engine::instance).thenReturn(engine);

    activity = Robolectric.buildActivity(AppCompatActivity.class).get();
    activity.setTheme(R.style.Theme_FrostWire);
    view = new MiniPlayerView(activity, null);
    view.onFinishInflate();
  }

  @After
  public void tearDown() {
    engines.close();
    system.close();
    images.close();
    throttle.close();
    nav.close();
    music.close();
  }

  private String title() {
    return ((TextView) view.findViewById(R.id.view_miniplayer_title)).getText().toString();
  }

  private String artist() {
    return ((TextView) view.findViewById(R.id.view_miniplayer_artist)).getText().toString();
  }

  private static FWFileDescriptor track(String title, String artist) {
    FWFileDescriptor fd = new FWFileDescriptor();
    fd.title = title;
    fd.artist = artist;
    return fd;
  }

  @Test
  public void theRefresherIsExposedForTheTimerService() {
    assertEquals(1, MiniPlayerView.REFRESHER_INTERVAL_IN_SECS);
    assertSame(view.getRefresher(), view.getRefresher());
  }

  @Test
  public void whilePlaybackServiceIsNotRunningTheTickDoesNothing() {
    music.when(MusicUtils::isMusicPlaybackServiceRunning).thenReturn(false);
    String before = title();

    view.refresherOnTime();

    verify(player, never()).getCurrentFD(any());
    assertEquals(before, title());
  }

  @Test
  public void aTickShowsTheCurrentTrackAndRevealsAHiddenPlayer() {
    music.when(MusicUtils::isMusicPlaybackServiceRunning).thenReturn(true);
    music.when(MusicUtils::isPlaying).thenReturn(true);
    music.when(MusicUtils::getCurrentAlbumId).thenReturn(0L);
    when(player.getCurrentFD(any())).thenReturn(track("Song", "Band"));
    view.setVisibility(View.GONE);

    view.refresherOnTime();

    assertEquals("Song", title());
    assertEquals("Band", artist());
    assertEquals(View.VISIBLE, view.getVisibility());
  }

  @Test
  public void aTickWithoutACurrentTrackClearsTheTextAndHidesThePlayer() {
    music.when(MusicUtils::isMusicPlaybackServiceRunning).thenReturn(true);
    music.when(MusicUtils::isPlaying).thenReturn(false);
    music.when(MusicUtils::getCurrentAlbumId).thenReturn(0L);
    when(player.getCurrentFD(any())).thenReturn(null);
    view.setVisibility(View.VISIBLE);

    view.refresherOnTime();

    assertEquals("", title());
    assertEquals("", artist());
    assertEquals(View.GONE, view.getVisibility());
  }

  @Test
  public void aTickWithoutAMediaPlayerHidesThePlayer() {
    music.when(MusicUtils::isMusicPlaybackServiceRunning).thenReturn(true);
    when(engine.getMediaPlayer()).thenReturn(null);
    view.setVisibility(View.VISIBLE);

    view.refresherOnTime();

    assertEquals(View.GONE, view.getVisibility());
  }

  @Test
  public void theAlbumCoverIsLoadedOncePlaybackReportsAnAlbum() {
    music.when(MusicUtils::isMusicPlaybackServiceRunning).thenReturn(true);
    music.when(MusicUtils::isPlaying).thenReturn(true);
    music.when(MusicUtils::getCurrentAlbumId).thenReturn(42L);
    music.when(MusicUtils::getFilePath).thenReturn("/music/a.mp3");
    when(player.getCurrentFD(any())).thenReturn(track("Song", "Band"));

    view.refresherOnTime();
    // the next refresh, as after a click, loads the cover the tick learned about
    view.findViewById(R.id.view_miniplayer_next).performClick();

    verify(imageLoader)
        .load(
            eq(Uri.parse("content://albums/42")),
            any(ImageView.class),
            eq(R.drawable.default_artwork),
            eq("/music/a.mp3"));
  }

  @Test
  public void aThrottledCoverRefreshLoadsNothing() {
    throttle.when(() -> TaskThrottle.isReadyToSubmitTask(anyString(), anyLong())).thenReturn(false);
    music.when(MusicUtils::isMusicPlaybackServiceRunning).thenReturn(true);
    music.when(MusicUtils::isPlaying).thenReturn(true);
    music.when(MusicUtils::getCurrentAlbumId).thenReturn(42L);
    when(player.getCurrentFD(any())).thenReturn(track("Song", "Band"));

    view.refresherOnTime();
    view.findViewById(R.id.view_miniplayer_next).performClick();

    verify(imageLoader, never()).load(any(Uri.class), any(ImageView.class), anyInt(), any());
  }

  @Test
  public void previousAndNextControlThePlayback() {
    view.findViewById(R.id.view_miniplayer_previous).performClick();
    music.verify(MusicUtils::previous);
    view.findViewById(R.id.view_miniplayer_next).performClick();
    music.verify(MusicUtils::next);
  }

  @Test
  public void playPauseTogglesPlaybackWhenThereIsAMediaPlayer() {
    view.findViewById(R.id.view_miniplayer_play_pause).performClick();
    music.verify(MusicUtils::playPauseOrResume);
  }

  @Test
  public void playPauseDoesNothingWithoutAMediaPlayer() {
    when(engine.getMediaPlayer()).thenReturn(null);
    view.findViewById(R.id.view_miniplayer_play_pause).performClick();
    music.verify(MusicUtils::playPauseOrResume, never());
  }

  @Test
  public void longPressingPlayPauseStopsPlaybackAndHidesThePlayer() {
    view.setVisibility(View.VISIBLE);

    boolean handled = view.findViewById(R.id.view_miniplayer_play_pause).performLongClick();

    assertEquals(true, handled);
    verify(player).stop();
    assertEquals(View.GONE, view.getVisibility());
  }

  @Test
  public void longPressingPlayPauseWithoutAMediaPlayerKeepsThePlayer() {
    when(engine.getMediaPlayer()).thenReturn(null);
    view.setVisibility(View.VISIBLE);

    view.findViewById(R.id.view_miniplayer_play_pause).performLongClick();

    assertEquals(View.VISIBLE, view.getVisibility());
  }

  @Test
  public void tappingTheCoverOrTheStatusOpensTheAudioPlayer() {
    view.findViewById(R.id.view_miniplayer_cover).performClick();
    nav.verify(() -> NavUtils.openAudioPlayer(activity));
    view.findViewById(R.id.view_miniplayer_status_container).performClick();
    nav.verify(() -> NavUtils.openAudioPlayer(activity), org.mockito.Mockito.times(2));
  }
}
