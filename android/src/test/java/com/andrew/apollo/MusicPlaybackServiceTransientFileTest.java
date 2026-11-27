/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Intent;
import android.os.Looper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Files other apps and the downloads screen hand to the player by path or content URI: whichever
 * way the path is spelled, a file MediaStore knows plays as its MediaStore song.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServiceTransientFileTest {

  private ApolloServiceFixture fixture;
  private MusicPlaybackService service;

  @Before
  public void setUp() throws Exception {
    fixture = ApolloServiceFixture.start();
    FakeMediaStore.install();
    service = fixture.service;
    // The first command initializes the service, which restores the (empty) saved queue.
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

  private void playTransient(String path) {
    assertTrue(service.playTransientFile(path));
    idle();
  }

  @Test
  public void withoutAPathThereIsNothingToPlay() {
    assertFalse(service.playTransientFile(null));
  }

  @Test
  public void aMediaStoreContentUriPlaysAsItsSong() {
    playTransient("content://media/external_primary/audio/media/77");

    assertArrayEquals(new long[] {77}, service.getQueue());
    assertTrue(service.isPlaying());
  }

  @Test
  public void aGoogleFilesUriPlaysAsItsSong() {
    playTransient("content://com.google.android.apps.nbu.files.provider/1/file/88");

    assertArrayEquals(new long[] {88}, service.getQueue());
  }

  @Test
  public void aDocumentsUriWithAnMsfIdPlaysAsItsSong() {
    playTransient("content://com.android.providers.downloads.documents/document/msf%3A99");

    assertEquals(99, service.getIdFromContextUri("content://x/msf:99"));
    assertEquals(-1, service.getIdFromContextUri("content://x/msf:abc"));
  }

  @Test
  public void aDownloadsDocumentPathIsLookedUpByItsFilePath() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            "/storage/emulated/0/Download/a.mp3".equals(a[0])
                ? cursor(p, row("_id", 66L))
                : cursor(p);

    playTransient(
        "content://com.android.providers.downloads.documents/document/raw%3A%2Fstorage%2Femulated%2F0%2FDownload%2Fa.mp3");

    assertArrayEquals(new long[] {66}, service.getQueue());
  }

  @Test
  public void aFileManagersContentUriIsReducedToTheFilePathAndLookedUpInDownloads() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            u.toString().contains("external/downloads") && a[0].contains("My Song.mp3")
                ? cursor(p, row("_id", 55L))
                : cursor(p);

    playTransient("content://com.oneplus.filemanager/root/storage/emulated/0/Music/My%20Song.mp3");

    assertArrayEquals(new long[] {55}, service.getQueue());
    assertTrue(FakeMediaStore.queries.stream().anyMatch(q -> q.contains("external/downloads")));
  }

  @Test
  public void aFileManagersFileInTheInternalDownloadsIsFoundThere() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) ->
            u.toString().contains("internal/downloads") ? cursor(p, row("_id", 54L)) : cursor(p);

    playTransient("content://com.oneplus.filemanager/root/storage/emulated/0/Music/a.mp3");

    assertArrayEquals(new long[] {54}, service.getQueue());
  }

  @Test
  public void aPlainRelativePathIsLookedUpInMediaStoreByPath() {
    FakeMediaStore.onQuery =
        (u, p, s, a, o) -> "Music/a.mp3".equals(a[0]) ? cursor(p, row("_id", 31L)) : cursor(p);

    playTransient("Music/a.mp3");

    assertArrayEquals(new long[] {31}, service.getQueue());
  }

  @Test
  public void aFileMediaStoreDoesNotKnowPlaysAsAQueueOfOneFileWithItsDecodedPath() {
    playTransient("/storage/emulated/0/Music/My%20Song.mp3");

    assertEquals("/storage/emulated/0/Music/My Song.mp3", service.getPath());
    assertTrue(service.isPlaying());
  }

  @Test
  public void aFileProviderUriIsMappedToTheExternalStorageRoot() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);

    playTransient("content://com.frostwire.android.fileprovider/external/Download/FrostWire/a.mp3");

    assertEquals("/storage/emulated/0/Download/FrostWire/a.mp3", service.getPath());
  }
}
