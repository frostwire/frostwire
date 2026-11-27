/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.database.Cursor;
import android.os.Looper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * What the service reports about the song that is playing when MediaStore's row for it is thin: the
 * lock screen and the player must show something rather than crash.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceIncompleteMetadataTest {

  private ApolloServiceFixture fixture;
  private MusicPlaybackService service;

  @Before
  public void setUp() throws Exception {
    fixture = ApolloServiceFixture.start();
    FakeMediaStore.install();
    service = fixture.service;
    service.handleCommandIntent(new Intent());
    idle();
  }

  @After
  public void tearDown() throws Exception {
    fixture.stop();
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  private void openWithRow(java.util.Map<String, Object> mediaRow) {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> {
          if (u.getPath().contains("/albums")) {
            Cursor c = cursor(new String[] {"_id"}, row("_id", 9L));
            return c;
          }
          return cursor(p == null ? null : mediaRow.keySet().toArray(new String[0]), mediaRow);
        };
    service.open(new long[] {5}, 0);
    service.pause();
    idle();
  }

  @Test
  public void aRowWithoutTitleAlbumOrArtistColumnsShowsPlaceholders() {
    openWithRow(row("_id", 5L, "album_id", 9L, "artist_id", 3L, "_data", "/music/a.mp3"));

    assertEquals("---", service.getTrackName());
    assertEquals("---", service.getAlbumName());
    assertEquals("", service.getArtistName());
    assertEquals(9L, service.getAlbumId());
    assertEquals(3L, service.getArtistId());
    assertEquals("/music/a.mp3", service.getPath());
  }

  @Test
  public void aRowWithTheFullMetadataShowsIt() {
    openWithRow(
        row(
            "_id",
            5L,
            "title",
            "Hey",
            "artist",
            "Ann",
            "album",
            "LP",
            "album_id",
            9L,
            "artist_id",
            3L,
            "_data",
            "/music/a.mp3",
            "duration",
            61_000));

    assertEquals("Hey", service.getTrackName());
    assertEquals("Ann", service.getArtistName());
    assertEquals("LP", service.getAlbumName());
    assertEquals(61_000, service.getTrackMediaStoreDuration());
  }

  @Test
  public void withNoRowAtAllTheMetadataIsEmpty() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);
    service.open(new long[] {5}, 0);
    service.pause();
    idle();

    assertNull(service.getTrackName());
    assertNull(service.getAlbumName());
    assertNull(service.getArtistName());
    assertEquals(-1, service.getAlbumId());
    assertEquals(-1, service.getArtistId());
    assertEquals(0, service.getTrackMediaStoreDuration());
    assertTrue(service.getPath() == null || service.getPath().isEmpty());
  }
}
