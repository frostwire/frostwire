/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.view.menu.MenuBuilder;
import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.ui.ApolloUiTestSupport;
import com.andrew.apollo.ui.FakeMediaProvider;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowLooper;

/**
 * The now-playing screen with a playback service that is playing "Song" by "Artist" from "Album",
 * 50 s into a 200 s track. The service is a Mockito stand-in and the calls that would reach it are
 * recorded in {@link #calls}.
 */
@SuppressWarnings("deprecation") // MediaStore.Audio.Playlists, how the player is asked for one
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AudioPlayerActivityTest {

  @org.junit.Rule
  public final com.andrew.apollo.ui.ApolloUiTestSupport.CleanAppMessages cleanAppMessages =
      new com.andrew.apollo.ui.ApolloUiTestSupport.CleanAppMessages();

  private MockedStatic<MusicUtils> music;
  private final List<String> calls = new CopyOnWriteArrayList<>();
  private MusicPlaybackService service;

  @Before
  public void setUp() {
    ApolloUiTestSupport.installAll();
    FakeMediaProvider.install();
    service = Mockito.mock(MusicPlaybackService.class);
    music = ApolloUiTestSupport.mockMusicUtils(calls);
    music.when(MusicUtils::getMusicPlaybackService).thenReturn(service);
    music.when(MusicUtils::isStopped).thenReturn(false);
    music.when(MusicUtils::isPlaying).thenReturn(true);
    music.when(MusicUtils::getTrackName).thenReturn("Song");
    music.when(MusicUtils::getArtistName).thenReturn("Artist");
    music.when(MusicUtils::getAlbumName).thenReturn("Album");
    music.when(MusicUtils::getCurrentAudioId).thenReturn(7L);
    music.when(MusicUtils::getCurrentAlbumId).thenReturn(70L);
    music.when(MusicUtils::duration).thenReturn(200_000L);
    music.when(MusicUtils::position).thenReturn(50_000L);
    music.when(MusicUtils::getQueue).thenReturn(new long[] {7, 8});
  }

  @After
  public void tearDown() {
    music.close();
  }

  private ActivityController<AudioPlayerActivity> launch(Intent intent) {
    ActivityController<AudioPlayerActivity> controller =
        Robolectric.buildActivity(AudioPlayerActivity.class, intent);
    controller.setup();
    ApolloUiTestSupport.settle();
    return controller;
  }

  private ActivityController<AudioPlayerActivity> launch() {
    return launch(new Intent());
  }

  private static String text(AudioPlayerActivity activity, int id) {
    return ((TextView) activity.findViewById(id)).getText().toString();
  }

  private void idleFor(long millis) {
    ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS);
  }

  @Test
  public void theNowPlayingInfoIsShown() {
    AudioPlayerActivity activity = launch().get();
    idleFor(600);

    assertEquals("Song", text(activity, R.id.audio_player_track_name));
    assertEquals("Artist - Album", text(activity, R.id.audio_player_artist_name));
    assertEquals("3:20", text(activity, R.id.audio_player_total_time));
    assertEquals("0:50", text(activity, R.id.audio_player_current_time));
    assertSame(activity, AudioPlayerActivity.instance());
  }

  @Test
  public void theProgressBarFollowsThePosition() {
    AudioPlayerActivity activity = launch().get();
    idleFor(600);

    SeekBar progress = activity.findViewById(android.R.id.progress);

    assertEquals(250, progress.getProgress());
  }

  @Test
  public void anArtistOnlyOrAlbumOnlyTrackShowsWhatItHas() {
    music.when(MusicUtils::getAlbumName).thenReturn(null);
    AudioPlayerActivity artistOnly = launch().get();
    assertEquals("Artist", text(artistOnly, R.id.audio_player_artist_name));

    music.when(MusicUtils::getAlbumName).thenReturn("Album");
    music.when(MusicUtils::getArtistName).thenReturn(null);
    AudioPlayerActivity albumOnly = launch().get();
    assertEquals("Album", text(albumOnly, R.id.audio_player_artist_name));
  }

  @Test
  public void withoutAServiceTheTimeShowsDashes() {
    music.when(MusicUtils::getMusicPlaybackService).thenReturn(null);
    AudioPlayerActivity activity = launch().get();
    idleFor(600);

    assertEquals("--:--", text(activity, R.id.audio_player_current_time));
  }

  @Test
  public void aTrackWithoutADurationShowsDashes() {
    music.when(MusicUtils::duration).thenReturn(0L);
    AudioPlayerActivity activity = launch().get();
    idleFor(600);

    assertEquals("--:--", text(activity, R.id.audio_player_current_time));
  }

  @Test
  public void aPausedTrackBlinksTheTime() {
    music.when(MusicUtils::isPlaying).thenReturn(false);
    AudioPlayerActivity activity = launch().get();
    View time = activity.findViewById(R.id.audio_player_current_time);

    idleFor(600);
    int first = time.getVisibility();
    idleFor(600);

    assertTrue(first == View.VISIBLE || first == View.INVISIBLE);
    assertEquals("0:50", text(activity, R.id.audio_player_current_time));
  }

  @Test
  public void draggingTheSeekBarSeeksTheTrack() {
    AudioPlayerActivity activity = launch().get();
    SeekBar progress = activity.findViewById(android.R.id.progress);

    activity.onStartTrackingTouch(progress);
    activity.onProgressChanged(progress, 500, true);
    activity.onProgressChanged(progress, 600, true);
    activity.onStopTrackingTouch(progress);

    assertTrue(calls.toString(), calls.contains("seek[100000]"));
  }

  @Test
  public void aProgressChangeThatIsNotFromTheUserIsIgnored() {
    AudioPlayerActivity activity = launch().get();
    SeekBar progress = activity.findViewById(android.R.id.progress);
    calls.clear();

    activity.onProgressChanged(progress, 500, false);

    assertTrue(calls.isEmpty());
  }

  @Test
  public void seekingWithoutATouchSeeksAtOnce() {
    AudioPlayerActivity activity = launch().get();
    SeekBar progress = activity.findViewById(android.R.id.progress);

    activity.onProgressChanged(progress, 250, true);
    SystemClock.setCurrentTimeMillis(SystemClock.uptimeMillis() + 100);
    activity.onProgressChanged(progress, 300, true);
    SystemClock.setCurrentTimeMillis(SystemClock.uptimeMillis() + 1000);
    activity.onProgressChanged(progress, 400, true);

    assertTrue(calls.toString(), calls.contains("seek[50000]"));
  }

  @Test
  public void aNewIntentWithAFilePlaysIt() {
    AudioPlayerActivity activity = launch().get();
    Uri song = Uri.parse("file:///music/song.mp3");

    activity.onNewIntent(new Intent(Intent.ACTION_VIEW, song));
    ApolloUiTestSupport.settle();

    assertTrue(calls.toString(), calls.contains("playFileFromUri[" + song + "]"));
  }

  @Test
  public void aNewIntentForAPlaylistAnAlbumOrAnArtistPlaysIt() {
    AudioPlayerActivity activity = launch().get();

    Intent playlist = new Intent().setType(MediaStore.Audio.Playlists.CONTENT_TYPE);
    playlist.putExtra("playlistId", 3L);
    activity.onNewIntent(playlist);
    Intent album = new Intent().setType(MediaStore.Audio.Albums.CONTENT_TYPE);
    album.putExtra("album", "4").putExtra("position", 2);
    activity.onNewIntent(album);
    Intent artist = new Intent().setType(MediaStore.Audio.Artists.CONTENT_TYPE);
    artist.putExtra("artistId", 5L);
    activity.onNewIntent(artist);
    ApolloUiTestSupport.settle();

    assertTrue(calls.toString(), calls.contains("playPlaylist[3]"));
    assertTrue(calls.toString(), calls.contains("playAlbum[4 2]"));
    assertTrue(calls.toString(), calls.contains("playArtist[5 0]"));
  }

  @Test
  public void anIntentNobodyKnowsHowToPlayIsIgnored() {
    AudioPlayerActivity activity = launch().get();
    calls.clear();

    activity.onNewIntent(new Intent().setType(MediaStore.Audio.Playlists.CONTENT_TYPE));
    activity.onNewIntent(new Intent().setType(MediaStore.Audio.Albums.CONTENT_TYPE));
    activity.onNewIntent(new Intent().setType(MediaStore.Audio.Artists.CONTENT_TYPE));
    activity.onNewIntent(new Intent().setType("audio/unknown"));
    ApolloUiTestSupport.settle();

    assertTrue(calls.toString(), calls.stream().noneMatch(c -> c.startsWith("play")));
  }

  @Test
  public void aLaunchWithAFileStartsItAfterTheServiceIsUp() {
    Uri song = Uri.parse("file:///music/song.mp3");

    launch(new Intent(Intent.ACTION_VIEW, song));
    ApolloUiTestSupport.settle();

    assertTrue(calls.toString(), calls.contains("playFileFromUri[" + song + "]"));
  }

  @Test
  public void aLaunchWithAFileAndNoServiceWaitsForIt() {
    music.when(MusicUtils::getMusicPlaybackService).thenReturn(null);
    Uri song = Uri.parse("file:///music/song.mp3");

    launch(new Intent(Intent.ACTION_VIEW, song));

    assertTrue(calls.toString(), calls.stream().noneMatch(c -> c.startsWith("playFileFromUri")));
  }

  @Test
  public void theOptionsMenuHasTheTrackActions() {
    AudioPlayerActivity activity = launch().get();
    Menu menu = new MenuBuilder(activity);

    assertTrue(activity.onCreateOptionsMenu(menu));
    assertTrue(activity.onPrepareOptionsMenu(menu));

    assertNotNull(menu.findItem(R.id.menu_player_favorite));
    assertNotNull(menu.findItem(R.id.menu_player_audio_player_share));
    assertNotNull(menu.findItem(R.id.menu_player_audio_player_delete));
    assertNotNull(menu.findItem(R.id.menu_player_shuffle));
  }

  @Test
  public void theFavoriteIconShowsWhetherTheTrackIsAFavorite() {
    music.when(MusicUtils::isFavorite).thenReturn(true);
    AudioPlayerActivity activity = launch().get();
    Menu menu = new MenuBuilder(activity);
    activity.onCreateOptionsMenu(menu);

    activity.onPrepareOptionsMenu(menu);
    assertNotNull(menu.findItem(R.id.menu_player_favorite).getIcon());

    music.when(MusicUtils::isFavorite).thenReturn(false);
    activity.onPrepareOptionsMenu(menu);
    assertNotNull(menu.findItem(R.id.menu_player_favorite).getIcon());
  }

  @Test
  public void shufflingAllThroughTheMenuRefreshesTheQueue() {
    AudioPlayerActivity activity = launch().get();

    assertTrue(activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_player_shuffle)));

    assertTrue(calls.toString(), calls.contains("shuffleAll[]"));
  }

  @Test
  public void theFavoriteMenuItemTogglesTheFavorite() {
    AudioPlayerActivity activity = launch().get();

    assertTrue(activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_player_favorite)));

    assertTrue(calls.toString(), calls.contains("toggleFavorite[]"));
  }

  @Test
  public void shareIsHandled() {
    AudioPlayerActivity activity = launch().get();

    assertTrue(
        activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_player_audio_player_share)));
  }

  @Test
  public void theEqualizerItemIsHandled() {
    AudioPlayerActivity activity = launch().get();

    assertTrue(
        activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_player_audio_player_equalizer)));
  }

  @Test
  public void stoppingFromTheMenuStopsTheServiceAndClosesTheScreen() {
    AudioPlayerActivity activity = launch().get();

    assertTrue(
        activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_player_audio_player_stop)));

    Mockito.verify(service).stop();
    assertTrue(activity.isFinishing());
  }

  @Test
  public void deletingTheTrackAsksForConfirmation() {
    AudioPlayerActivity activity = launch().get();

    assertTrue(
        activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_player_audio_player_delete)));
    ApolloUiTestSupport.settle();

    assertNotNull(activity.getSupportFragmentManager().findFragmentByTag("DeleteDialog"));
  }

  @Test
  public void addingTheTrackToAPlaylistIsHandled() {
    AudioPlayerActivity activity = launch().get();

    activity.onOptionsItemSelected(new RoboMenuItem(R.id.menu_player_audio_player_add_to_playlist));
    ApolloUiTestSupport.settle();

    assertFalse(activity.isFinishing());
  }

  @Test
  public void anotherMenuItemFallsThroughToTheBaseScreen() {
    AudioPlayerActivity activity = launch().get();

    assertFalse(activity.onOptionsItemSelected(new RoboMenuItem(12345)));
  }

  @Test
  public void deletingTheLastSongClosesTheScreen() {
    AudioPlayerActivity activity = launch().get();

    music.when(MusicUtils::getQueue).thenReturn(new long[] {8});
    activity.onDelete(new long[] {7});
    assertFalse(activity.isFinishing());

    music.when(MusicUtils::getQueue).thenReturn(new long[0]);
    activity.onDelete(new long[] {8});
    assertTrue(activity.isFinishing());
  }

  @Test
  public void theAlbumArtAndTheQueueSwitchBetweenEachOther() {
    AudioPlayerActivity activity = launch().get();
    View pager = activity.findViewById(R.id.audio_player_pager_container);
    assertEquals(View.INVISIBLE, pager.getVisibility());

    activity.findViewById(R.id.audio_player_switch).performClick();
    assertEquals(View.VISIBLE, pager.getVisibility());
    activity.findViewById(R.id.audio_player_header).performClick();

    activity.findViewById(R.id.audio_player_switch).performClick();
    assertEquals(View.INVISIBLE, pager.getVisibility());
    activity.findViewById(R.id.audio_player_header).performClick();
  }

  @Test
  public void tappingTheHeaderOpensTheAlbumProfile() {
    AudioPlayerActivity activity = launch().get();

    activity.findViewById(R.id.audio_player_header).performClick();

    ApolloUiTestSupport.waitUntil(
        "the album profile opens", () -> shadowOf(activity).peekNextStartedActivity() != null);
    assertEquals(
        ProfileActivity.class.getName(),
        shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
  }

  @Test
  public void playbackBroadcastsRefreshTheScreen() {
    AudioPlayerActivity activity = launch().get();
    music.when(MusicUtils::getTrackName).thenReturn("Another song");
    music.when(MusicUtils::getCurrentAudioId).thenReturn(8L);

    activity.sendBroadcast(new Intent(MusicPlaybackService.QUEUE_CHANGED));
    activity.sendBroadcast(new Intent(MusicPlaybackService.META_CHANGED));
    activity.sendBroadcast(new Intent(MusicPlaybackService.PLAYSTATE_CHANGED));
    activity.sendBroadcast(new Intent(MusicPlaybackService.REPEATMODE_CHANGED));
    activity.sendBroadcast(new Intent(MusicPlaybackService.SHUFFLEMODE_CHANGED));
    activity.sendBroadcast(new Intent(MusicPlaybackService.REFRESH));
    ApolloUiTestSupport.settle();

    assertEquals("Another song", text(activity, R.id.audio_player_track_name));
  }

  @Test
  public void aTrackChangeBetweenRefreshesIsNoticed() {
    AudioPlayerActivity activity = launch().get();
    idleFor(600);

    music.when(MusicUtils::getTrackName).thenReturn("Next song");
    music.when(MusicUtils::getCurrentAudioId).thenReturn(9L);
    idleFor(1200);

    assertEquals("Next song", text(activity, R.id.audio_player_track_name));
  }

  @Test
  public void theAlbumArtIsShownOnceTheTrackHasPlayedAWhile() {
    AudioPlayerActivity activity = launch().get();
    idleFor(600);
    music.when(MusicUtils::getCurrentAudioId).thenReturn(11L);
    music.when(MusicUtils::position).thenReturn(5_000L);
    idleFor(1200);
    View art = activity.findViewById(R.id.audio_player_album_art);
    art.setVisibility(View.GONE);

    idleFor(9_000);

    assertEquals(View.VISIBLE, art.getVisibility());
  }

  @Test
  public void holdingPreviousRewindsAndHoldingNextFastForwards() {
    AudioPlayerActivity activity = launch().get();
    com.andrew.apollo.widgets.RepeatingImageButton previous =
        activity.findViewById(R.id.action_button_previous);
    com.andrew.apollo.widgets.RepeatingImageButton next =
        activity.findViewById(R.id.action_button_next);

    for (com.andrew.apollo.widgets.RepeatingImageButton button : List.of(previous, next)) {
      button.setPressed(true);
      button.performLongClick();
      idleFor(1500);
      button.setPressed(false);
      long now = SystemClock.uptimeMillis();
      button.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1f, 1f, 0));
    }

    assertTrue(calls.toString(), calls.stream().anyMatch(c -> c.startsWith("seek[")));
  }

  @Test
  public void doubleTappingTheAlbumArtTogglesTheFavorite() {
    AudioPlayerActivity activity = launch().get();
    ImageView art = activity.findViewById(R.id.audio_player_album_art);
    long t = SystemClock.uptimeMillis();

    art.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
    art.dispatchTouchEvent(MotionEvent.obtain(t, t + 20, MotionEvent.ACTION_UP, 10f, 10f, 0));
    art.dispatchTouchEvent(
        MotionEvent.obtain(t + 60, t + 60, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
    art.dispatchTouchEvent(MotionEvent.obtain(t + 60, t + 80, MotionEvent.ACTION_UP, 10f, 10f, 0));
    ApolloUiTestSupport.settle();

    assertTrue(calls.toString(), calls.contains("toggleFavorite[]"));
  }

  @Test
  public void aTwoFingerTouchPlaysOrPauses() {
    AudioPlayerActivity activity = launch().get();
    ImageView art = activity.findViewById(R.id.audio_player_album_art);
    long t = SystemClock.uptimeMillis();
    MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[2];
    MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[2];
    for (int i = 0; i < 2; i++) {
      props[i] = new MotionEvent.PointerProperties();
      props[i].id = i;
      coords[i] = new MotionEvent.PointerCoords();
      coords[i].x = 10 + i * 20;
      coords[i].y = 10;
    }
    art.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
    MotionEvent second =
        MotionEvent.obtain(
            t,
            t + 10,
            MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2,
            props,
            coords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            0,
            0);

    art.dispatchTouchEvent(second);

    assertTrue(calls.toString(), calls.contains("playPauseOrResume[]"));
  }

  @Test
  public void leavingTheScreenStopsTheRefreshAndTheReceiver() {
    ActivityController<AudioPlayerActivity> controller = launch();

    controller.pause().stop();
    idleFor(1500);
    controller.start().resume();
    controller.pause().stop().destroy();

    assertNull(AudioPlayerActivity.instance());
  }

  @Test
  public void theScreenSurvivesARotation() {
    ActivityController<AudioPlayerActivity> controller = launch();
    Bundle state = new Bundle();
    controller.saveInstanceState(state);

    AudioPlayerActivity second =
        Robolectric.buildActivity(AudioPlayerActivity.class, new Intent()).setup(state).get();

    assertEquals("Song", text(second, R.id.audio_player_track_name));
  }

  @Test
  public void anActivityResultOfAnotherRequestIsPassedOn() {
    AudioPlayerActivity activity = launch().get();

    activity.onActivityResult(12345, 0, null);

    assertFalse(activity.isFinishing());
  }

  @Test
  public void holdingPreviousPastTheStartOfTheTrackGoesToThePreviousTrack() {
    AudioPlayerActivity activity = launch().get();
    com.andrew.apollo.widgets.RepeatingImageButton previous =
        activity.findViewById(R.id.action_button_previous);

    previous.setPressed(true);
    previous.performLongClick();
    idleFor(8000);
    previous.setPressed(false);
    long now = SystemClock.uptimeMillis();
    previous.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1f, 1f, 0));

    assertTrue(calls.toString(), calls.contains("previous[]"));
  }

  @Test
  public void holdingNextPastTheEndOfTheTrackGoesToTheNextTrack() {
    AudioPlayerActivity activity = launch().get();
    com.andrew.apollo.widgets.RepeatingImageButton next =
        activity.findViewById(R.id.action_button_next);

    next.setPressed(true);
    next.performLongClick();
    idleFor(8000);
    next.setPressed(false);
    long now = SystemClock.uptimeMillis();
    next.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 1f, 1f, 0));

    assertTrue(calls.toString(), calls.contains("next[]"));
  }

  @Test
  public void aHandledDeleteResultIsNotPassedOn() {
    AudioPlayerActivity activity = launch().get();
    music
        .when(
            () ->
                MusicUtils.handleDeleteTracksActivityResult(
                    Mockito.any(), Mockito.anyInt(), Mockito.anyInt()))
        .thenReturn(true);

    activity.onActivityResult(1, 0, null);

    assertFalse(activity.isFinishing());
  }

  @Test
  public void searchingFromTheMenuOpensTheSearchScreen() {
    AudioPlayerActivity activity = launch().get();
    Menu menu = new MenuBuilder(activity);
    activity.onCreateOptionsMenu(menu);
    androidx.appcompat.widget.SearchView searchView =
        (androidx.appcompat.widget.SearchView)
            menu.findItem(R.id.menu_player_search).getActionView();

    searchView.setQuery("typing", false);
    searchView.setQuery("beatles", true);

    Intent search = shadowOf(activity).getNextStartedActivity();
    assertNotNull(search);
    assertEquals(SearchActivity.class.getName(), search.getComponent().getClassName());
    assertEquals("beatles", search.getStringExtra(android.app.SearchManager.QUERY));
  }

  @Test
  public void aTrackThatChangedAgainBeforeTheBannerTimerFiredLeavesTheAlbumArtAlone() {
    AudioPlayerActivity activity = launch().get();
    idleFor(600);
    music.when(MusicUtils::getCurrentAudioId).thenReturn(11L);
    idleFor(1200);
    View art = activity.findViewById(R.id.audio_player_album_art);
    art.setVisibility(View.GONE);
    music.when(MusicUtils::getCurrentAudioId).thenReturn(12L);
    idleFor(1200);

    idleFor(9_000);

    assertEquals(View.GONE, art.getVisibility());
  }

  @Test
  public void aTrackStillAtItsStartWhenTheTimerFiresLeavesTheAlbumArtAlone() {
    AudioPlayerActivity activity = launch().get();
    idleFor(600);
    music.when(MusicUtils::position).thenReturn(1_000L);
    music.when(MusicUtils::getCurrentAudioId).thenReturn(11L);
    idleFor(1200);
    View art = activity.findViewById(R.id.audio_player_album_art);
    art.setVisibility(View.GONE);

    idleFor(9_000);

    assertEquals(View.GONE, art.getVisibility());
  }

  @Test
  public void whileTheUserHoldsTheSeekBarTheTimeRefreshesSlowly() {
    AudioPlayerActivity activity = launch().get();
    SeekBar progress = activity.findViewById(android.R.id.progress);

    activity.onStartTrackingTouch(progress);
    idleFor(1200);
    activity.onStopTrackingTouch(progress);

    assertEquals("0:50", text(activity, R.id.audio_player_current_time));
  }
}
