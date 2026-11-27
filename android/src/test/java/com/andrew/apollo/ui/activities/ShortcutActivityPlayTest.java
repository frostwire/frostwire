/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.provider.MediaStore;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.Config;
import com.andrew.apollo.ui.ApolloUiTestSupport;
import com.andrew.apollo.ui.FakeMediaProvider;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
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
 * Playing from a home screen shortcut or a voice command: the songs of the shortcut's artist,
 * album, genre or playlist are played and the player opens, or nothing happens when it is empty.
 */
@SuppressWarnings("deprecation") // MediaStore.Audio.Playlists, how a shortcut names a playlist
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class ShortcutActivityPlayTest {

  private MockedStatic<MusicUtils> music;
  private final List<String> calls = new CopyOnWriteArrayList<>();

  @Before
  public void setUp() {
    ApolloUiTestSupport.installAll();
    FakeMediaProvider.install();
    FakeMediaProvider.addSong(21, "Help", "Help!", "The Beatles");
    music = ApolloUiTestSupport.mockMusicUtils(calls);
  }

  @After
  public void tearDown() {
    music.close();
  }

  private ShortcutActivity launch(Intent intent) {
    ShortcutActivity activity =
        Robolectric.buildActivity(ShortcutActivity.class, intent).setup().get();
    ApolloUiTestSupport.settle();
    return activity;
  }

  private static Intent shortcut(String mimeType) {
    return new Intent()
        .putExtra(Config.MIME_TYPE, mimeType)
        .putExtra(Config.ID, 1L)
        .putExtra(Config.NAME, "A shortcut");
  }

  private void assertPlaysAndOpensThePlayer(ShortcutActivity activity) {
    ApolloUiTestSupport.waitUntil(
        "the songs play " + calls, () -> calls.stream().anyMatch(c -> c.startsWith("playFDs[[")));
    ApolloUiTestSupport.waitUntil("the screen closes", activity::isFinishing);
    Intent player = shadowOf(activity).getNextStartedActivity();
    assertEquals(AudioPlayerActivity.class.getName(), player.getComponent().getClassName());
  }

  @Test
  public void anArtistShortcutPlaysTheArtistAndOpensThePlayer() {
    assertPlaysAndOpensThePlayer(launch(shortcut(MediaStore.Audio.Artists.CONTENT_TYPE)));
  }

  @Test
  public void anAlbumShortcutPlaysTheAlbum() {
    assertPlaysAndOpensThePlayer(launch(shortcut(MediaStore.Audio.Albums.CONTENT_TYPE)));
  }

  @Test
  public void aGenreShortcutPlaysTheGenre() {
    assertPlaysAndOpensThePlayer(launch(shortcut(MediaStore.Audio.Genres.CONTENT_TYPE)));
  }

  @Test
  public void aPlaylistShortcutPlaysThePlaylist() {
    assertPlaysAndOpensThePlayer(launch(shortcut(MediaStore.Audio.Playlists.CONTENT_TYPE)));
  }

  @Test
  public void theLastAddedShortcutPlaysTheNewestSongs() {
    Context context = ApplicationProvider.getApplicationContext();
    assertPlaysAndOpensThePlayer(launch(shortcut(context.getString(R.string.playlist_last_added))));
  }

  @Test
  public void aShortcutThatShouldNotOpenThePlayerOnlyPlays() {
    Intent intent = shortcut(MediaStore.Audio.Albums.CONTENT_TYPE);
    intent.putExtra("open_audio_player", false);

    ShortcutActivity activity = launch(intent);

    ApolloUiTestSupport.waitUntil("the screen closes", activity::isFinishing);
    assertTrue(calls.toString(), calls.stream().anyMatch(c -> c.startsWith("playFDs[[")));
    assertEquals(null, shadowOf(activity).getNextStartedActivity());
  }

  @Test
  public void anEmptyShortcutPlaysNothingAndCloses() {
    FakeMediaProvider.clear();

    ShortcutActivity activity = launch(shortcut(MediaStore.Audio.Albums.CONTENT_TYPE));

    ApolloUiTestSupport.waitUntil("the screen closes", activity::isFinishing);
    assertFalse(calls.toString(), calls.stream().anyMatch(c -> c.startsWith("playFDs")));
  }

  @Test
  public void aVoiceSearchPlaysTheBestMatch() {
    Intent voice =
        new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, "help");

    ShortcutActivity activity = launch(voice);

    ApolloUiTestSupport.waitUntil(
        "the match plays " + calls, () -> calls.stream().anyMatch(c -> c.startsWith("playFDs[[")));
    ApolloUiTestSupport.waitUntil("the screen closes", activity::isFinishing);
  }

  @Test
  public void aVoiceSearchForTheFavoritesPlaysTheFavorites() {
    FakeMediaProvider.clear();
    Context context = ApplicationProvider.getApplicationContext();
    Intent voice =
        new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, context.getString(R.string.playlist_favorites));

    ShortcutActivity activity = launch(voice);

    ApolloUiTestSupport.waitUntil("the screen closes", activity::isFinishing);
  }
}
