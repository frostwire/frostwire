/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The favorites and recently played databases behind the Apollo playlists. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class FavoritesAndRecentStoresTest {

  private Context context;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    forget(FavoritesStore.class);
    forget(RecentStore.class);
  }

  @After
  public void tearDown() throws Exception {
    forget(FavoritesStore.class);
    forget(RecentStore.class);
  }

  /** The stores are singletons; a test must not use the database of the test before it. */
  private static void forget(Class<?> store) throws Exception {
    Field field = store.getDeclaredField("sInstance");
    field.setAccessible(true);
    field.set(null, null);
  }

  @Test
  public void aStoreIsCreatedOnceAndReused() {
    assertSame(FavoritesStore.getInstance(context), FavoritesStore.getInstance(context));
    assertSame(RecentStore.getInstance(context), RecentStore.getInstance(context));
  }

  @Test
  public void aFavoriteIsStoredAndFoundById() {
    FavoritesStore store = FavoritesStore.getInstance(context);

    assertNull(store.getSongId(5L));
    store.addSongId(5L, "Hey", "LP", "Ann");

    assertEquals(Long.valueOf(5), store.getSongId(5L));
    assertEquals(Long.valueOf(1), store.getPlayCount(5L));
  }

  @Test
  public void addingAFavoriteAgainCountsAnotherPlay() {
    FavoritesStore store = FavoritesStore.getInstance(context);

    store.addSongId(5L, "Hey", "LP", "Ann");
    store.addSongId(5L, "Hey", "LP", "Ann");

    assertEquals(Long.valueOf(2), store.getPlayCount(5L));
  }

  @Test
  public void incompleteFavoritesAreNotStored() {
    FavoritesStore store = FavoritesStore.getInstance(context);

    store.addSongId(null, "Hey", "LP", "Ann");
    store.addSongId(-1L, "Hey", "LP", "Ann");
    store.addSongId(5L, null, "LP", "Ann");
    store.addSongId(5L, "Hey", null, "Ann");
    store.addSongId(5L, "Hey", "LP", null);

    assertNull(store.getSongId(5L));
  }

  @Test
  public void aSongThatIsntSavedHasNoIdAndAZeroPlayCount() {
    FavoritesStore store = FavoritesStore.getInstance(context);

    assertNull(store.getSongId(-1L));
    assertNull(store.getPlayCount(-1L));
    assertEquals(Long.valueOf(0), store.getPlayCount(99L));
  }

  @Test
  public void togglingAFavoriteAddsItThenRemovesIt() {
    FavoritesStore store = FavoritesStore.getInstance(context);

    store.toggleSong(5L, "Hey", "LP", "Ann");
    assertEquals(Long.valueOf(5), store.getSongId(5L));

    store.toggleSong(5L, "Hey", "LP", "Ann");
    assertNull(store.getSongId(5L));
  }

  @Test
  public void favoritesAreRemovedOneOrManyAtATime() {
    FavoritesStore store = FavoritesStore.getInstance(context);
    store.addSongId(1L, "a", "b", "c");
    store.addSongId(2L, "a", "b", "c");
    store.addSongId(3L, "a", "b", "c");

    store.removeItem(1L);
    store.removeItems(List.of());
    store.removeItems(List.of(2L, 3L));

    assertNull(store.getSongId(1L));
    assertNull(store.getSongId(2L));
    assertNull(store.getSongId(3L));
  }

  @Test
  public void upgradingTheFavoritesDatabaseStartsItOver() {
    FavoritesStore store = FavoritesStore.getInstance(context);
    store.addSongId(1L, "a", "b", "c");

    store.onUpgrade(store.getWritableDatabase(), 1, 2);

    assertNull(store.getSongId(1L));
  }

  @Test
  public void aRecentSongIsRememberedByItsArtist() {
    RecentStore store = RecentStore.getInstance(context);

    assertNull(store.getSongName("Ann"));
    assertNull(store.getAlbumName("Ann"));
    store.addSongId(2L, "New", "Ann", "Second LP", 1000L, "unknown");

    assertEquals("New", store.getSongName("Ann"));
    assertEquals("Second LP", store.getAlbumName("Ann"));
    assertNull(store.getSongName("Bob"));
    assertNull(store.getAlbumName(null));
    assertNull(store.getSongName(""));
  }

  @Test
  public void missingMetadataIsStoredAsTheUnknownString() {
    RecentStore store = RecentStore.getInstance(context);

    store.addSongId(1L, null, null, null, 1000L, "unknown");

    assertEquals("unknown", store.getSongName("unknown"));
    assertEquals("unknown", store.getAlbumName("unknown"));
  }

  @Test
  public void songsWithoutAnIdOrDurationAreNotRemembered() {
    RecentStore store = RecentStore.getInstance(context);

    store.addSongId(null, "a", "Ann", "b", 1000L, "unknown");
    store.addSongId(1L, "a", "Ann", "b", 0L, "unknown");

    assertNull(store.getSongName("Ann"));
  }

  @Test
  public void recentSongsAreRemovedByIdByGroupOrAllAtOnce() {
    RecentStore store = RecentStore.getInstance(context);
    store.addSongId(1L, "a", "Ann", "x", 10L, "u");
    store.addSongId(2L, "b", "Bob", "y", 10L, "u");
    store.addSongId(3L, "c", "Cy", "z", 10L, "u");

    store.removeItem(1L);
    assertNull(store.getSongName("Ann"));
    store.removeItems(List.of());
    store.removeItems(List.of(2L));
    assertNull(store.getSongName("Bob"));
    assertNotNull(store.getSongName("Cy"));
    store.deleteDatabase();
    assertNull(store.getSongName("Cy"));
  }

  @Test
  public void upgradingTheRecentDatabaseStartsItOver() {
    RecentStore store = RecentStore.getInstance(context);
    store.addSongId(1L, "a", "Ann", "x", 10L, "u");

    store.onUpgrade(store.getWritableDatabase(), 1, 2);

    assertNull(store.getSongName("Ann"));
    assertTrue(store.getReadableDatabase().isOpen());
  }
}
