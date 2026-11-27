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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import androidx.media3.common.MediaMetadata;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The lock screen and Bluetooth devices read the playing song's title, artist and artwork from the
 * media session; the service pushes them there when the song changes.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceSessionMetadataTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private ApolloServiceFixture fixture;
  private MusicPlaybackService service;

  @Before
  public void setUp() throws Exception {
    fixture = ApolloServiceFixture.start();
    FakeMediaStore.install();
    FakeConfig.install();
    service = fixture.service;
    service.handleCommandIntent(new Intent());
    idle();
  }

  @After
  public void tearDown() throws Exception {
    fixture.stop();
    FakeConfig.uninstall();
  }

  private static void idle() {
    shadowOf(Looper.getMainLooper()).idle();
  }

  private MediaMetadata sessionMetadata() {
    return service.getSessions().get(0).getPlayer().getMediaMetadata();
  }

  private void play(java.util.Map<String, Object> mediaRow) {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> u.getPath().contains("/albums") ? cursor(p) : cursor(p, mediaRow);
    service.open(new long[] {5}, 0);
    service.pause();
    idle();
    idle();
  }

  @Test
  public void theSessionCarriesTheSongsTitleArtistAlbumAndAlbumArtUri() throws Exception {
    File song = folder.newFile("song.mp3");

    play(
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
            song.getAbsolutePath(),
            "duration",
            61_000));

    MediaMetadata metadata = sessionMetadata();
    assertEquals("Hey", metadata.title.toString());
    assertEquals("Ann", metadata.artist.toString());
    assertEquals("LP", metadata.albumTitle.toString());
    assertEquals(Uri.parse("content://media/external/audio/albumart/9"), metadata.artworkUri);
  }

  @Test
  public void aSongWithoutAnAlbumUsesTheArtFileNextToIt() throws Exception {
    File song = folder.newFile("song.mp3");
    File art = folder.newFile("song.art");

    play(
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
            -1L,
            "artist_id",
            3L,
            "_data",
            song.getAbsolutePath(),
            "duration",
            61_000));

    assertEquals(Uri.fromFile(art), sessionMetadata().artworkUri);
  }

  @Test
  public void aSongWithNoArtAnywhereHasNoArtworkUri() throws Exception {
    File song = folder.newFile("plain.mp3");

    play(
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
            -1L,
            "artist_id",
            3L,
            "_data",
            song.getAbsolutePath(),
            "duration",
            61_000));

    assertEquals("Hey", sessionMetadata().title.toString());
    assertNull(sessionMetadata().artworkUri);
  }

  @Test
  public void aSongKeepsItsPlaceInTheQueueWhenItsMetadataIsPushed() throws Exception {
    File song = folder.newFile("song.mp3");

    play(
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
            song.getAbsolutePath(),
            "duration",
            61_000));

    assertNotNull(service.getSessions().get(0).getPlayer().getCurrentMediaItem());
    assertEquals(1, service.getQueue().length);
    assertEquals(5, service.getAudioId());
  }

  @Test
  public void aMalformedContentUriIsStillPlayedAsAFile() {
    for (String path :
        new String[] {
          "content://media/external_primary/audio/media/not-a-number",
          "content://com.google.android.apps.nbu.files.provider/1/file/not-a-number"
        }) {
      FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);

      service.playTransientFile(path);
      idle();

      assertEquals(path, service.getPath());
    }
  }

  @Test
  public void aDownloadsDocumentTheStoreFailsOnIsStillPlayedAsAFile() {
    String path =
        "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fa.mp3";
    FakeMediaStore.failure = new IllegalStateException("down");

    service.playTransientFile(path);
    idle();

    assertEquals(path, service.getPath());
  }
}
