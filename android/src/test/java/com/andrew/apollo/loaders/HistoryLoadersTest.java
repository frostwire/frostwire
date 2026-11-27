/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.loaders;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeConfig;
import com.andrew.apollo.FakeMediaStore;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.RecentStore;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The Recent and Favorites lists, read from the databases Apollo keeps. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class HistoryLoadersTest {

  private Context context;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    ApolloServiceFixture.installPlatform(false);
    FakeMediaStore.install();
    FakeConfig.install();
    FakeConfig.resetStores();
  }

  @After
  public void tearDown() throws Exception {
    FakeConfig.resetStores();
    FakeConfig.uninstall();
  }

  @Test
  public void favoritesAreListedWithTheirNamesAndNoDuration() {
    FavoritesStore.getInstance(context).addSongId(5L, "Hey", "LP", "Ann");

    List<Song> songs = new FavoritesLoader(context).loadInBackground();

    assertEquals(1, songs.size());
    assertEquals(5, songs.get(0).mSongId);
    assertEquals("Hey", songs.get(0).mSongName);
    assertEquals("Ann", songs.get(0).mArtistName);
    assertEquals("LP", songs.get(0).mAlbumName);
    assertEquals(-1, songs.get(0).mDuration);
  }

  @Test
  public void favoritesWithNothingSavedAreEmptyAndNeedAContext() {
    assertTrue(new FavoritesLoader(context).loadInBackground().isEmpty());
    assertNull(FavoritesLoader.makeFavoritesCursor(null));
    Cursor cursor = FavoritesLoader.makeFavoritesCursor(context);
    assertEquals(0, cursor.getCount());
    cursor.close();
  }

  @Test
  public void theMostPlayedFavoriteComesFirst() {
    FavoritesStore store = FavoritesStore.getInstance(context);
    store.addSongId(1L, "Once", "LP", "Ann");
    store.addSongId(2L, "Twice", "LP", "Ann");
    store.addSongId(2L, "Twice", "LP", "Ann");

    List<Song> songs = new FavoritesLoader(context).loadInBackground();

    assertEquals(2, songs.size());
    assertEquals(2, songs.get(0).mSongId);
    assertEquals(1, songs.get(1).mSongId);
  }

  @Test
  public void recentSongsKeepOnlyThoseMediaStoreStillHas() {
    RecentStore store = RecentStore.getInstance(context);
    store.addSongId(1L, "Kept", "Ann", "LP", 125_000L, "unknown");
    store.addSongId(2L, "Gone", "Ann", "LP", 125_000L, "unknown");
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p, row("_id", 1L));

    List<Song> songs = new RecentLoader(context).loadInBackground();

    assertEquals(1, songs.size());
    assertEquals("Kept", songs.get(0).mSongName);
    assertEquals(125, songs.get(0).mDuration);
    assertNull("the forgotten song left the database too", store.getSongName("Gone"));
  }

  @Test
  public void aFailingMediaStoreLeavesTheRecentListAlone() {
    RecentStore store = RecentStore.getInstance(context);
    store.addSongId(1L, "Kept", "Ann", "LP", 125_000L, "unknown");
    FakeMediaStore.failure = new IllegalStateException("provider not ready");

    List<Song> songs = new RecentLoader(context).loadInBackground();

    assertEquals(1, songs.size());
    assertNotNull(store.getSongName("Ann"));
  }

  @Test
  public void anEmptyRecentListIsEmpty() {
    assertTrue(new RecentLoader(context).loadInBackground().isEmpty());
  }
}
