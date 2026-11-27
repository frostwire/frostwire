/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import com.andrew.apollo.FakeConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** The sort orders and list layouts the Apollo screens remember, and their defaults. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class PreferenceUtilsTest {

  private FakeConfig config;
  private PreferenceUtils prefs;

  @Before
  public void setUp() throws Exception {
    config = FakeConfig.install();
    prefs = PreferenceUtils.getInstance();
  }

  @After
  public void tearDown() throws Exception {
    FakeConfig.uninstall();
  }

  @Test
  public void isASingleton() {
    assertSame(prefs, PreferenceUtils.getInstance());
  }

  @Test
  public void everySortOrderStartsAtItsDefaultAndRemembersWhatItIsGiven() {
    assertEquals(SortOrder.ArtistSortOrder.ARTIST_A_Z, prefs.getArtistSortOrder());
    assertEquals(SortOrder.ArtistSongSortOrder.SONG_A_Z, prefs.getArtistSongSortOrder());
    assertEquals(SortOrder.ArtistAlbumSortOrder.ALBUM_A_Z, prefs.getArtistAlbumSortOrder());
    assertEquals(SortOrder.AlbumSortOrder.ALBUM_A_Z, prefs.getAlbumSortOrder());
    assertEquals(SortOrder.AlbumSongSortOrder.SONG_TRACK_LIST, prefs.getAlbumSongSortOrder());
    assertEquals(SortOrder.SongSortOrder.SONG_A_Z, prefs.getSongSortOrder());

    prefs.setArtistSortOrder(SortOrder.ArtistSortOrder.ARTIST_Z_A);
    prefs.setArtistSongSortOrder(SortOrder.ArtistSongSortOrder.SONG_Z_A);
    prefs.setArtistAlbumSortOrder(SortOrder.ArtistAlbumSortOrder.ALBUM_Z_A);
    prefs.setAlbumSortOrder(SortOrder.AlbumSortOrder.ALBUM_YEAR);
    prefs.setAlbumSongSortOrder(SortOrder.AlbumSongSortOrder.SONG_Z_A);
    prefs.setSongSortOrder(SortOrder.SongSortOrder.SONG_DATE);

    assertEquals(SortOrder.ArtistSortOrder.ARTIST_Z_A, prefs.getArtistSortOrder());
    assertEquals(SortOrder.ArtistSongSortOrder.SONG_Z_A, prefs.getArtistSongSortOrder());
    assertEquals(SortOrder.ArtistAlbumSortOrder.ALBUM_Z_A, prefs.getArtistAlbumSortOrder());
    assertEquals(SortOrder.AlbumSortOrder.ALBUM_YEAR, prefs.getAlbumSortOrder());
    assertEquals(SortOrder.AlbumSongSortOrder.SONG_Z_A, prefs.getAlbumSongSortOrder());
    assertEquals(SortOrder.SongSortOrder.SONG_DATE, prefs.getSongSortOrder());
  }

  @Test
  public void anArtistSortOrderThatIsNotAnArtistColumnFallsBackToTheDefault() {
    prefs.setArtistSortOrder(SortOrder.ArtistSongSortOrder.SONG_FILENAME);

    assertEquals(SortOrder.ArtistSortOrder.ARTIST_A_Z, prefs.getArtistSortOrder());
  }

  @Test
  public void theStartPageIsRememberedAndDefaultsToTheDefaultPage() {
    assertEquals(PreferenceUtils.DEFAULT_PAGE, prefs.getStartPage());

    prefs.setStartPage(3);

    assertEquals(3, prefs.getStartPage());
  }

  @Test
  public void layoutsAreGridUnlessSetToSimpleOrDetailed() {
    assertFalse(prefs.isSimpleLayout(PreferenceUtils.ARTIST_LAYOUT));
    assertFalse(prefs.isDetailedLayout(PreferenceUtils.ARTIST_LAYOUT));

    prefs.setArtistLayout("simple");
    prefs.setAlbumLayout("detailed");
    prefs.setRecentLayout("simple");

    assertTrue(prefs.isSimpleLayout(PreferenceUtils.ARTIST_LAYOUT));
    assertFalse(prefs.isDetailedLayout(PreferenceUtils.ARTIST_LAYOUT));
    assertTrue(prefs.isDetailedLayout(PreferenceUtils.ALBUM_LAYOUT));
    assertTrue(prefs.isSimpleLayout(PreferenceUtils.RECENT_LAYOUT));
    assertEquals("simple", config.values.get(PreferenceUtils.RECENT_LAYOUT));
  }
}
