/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.core.providers;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.database.Cursor;
import android.database.MatrixCursor;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class TableFetchersTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  /** A one-row cursor with the fetcher's own columns, in its own order. */
  private static Cursor rowFor(TableFetcher fetcher, Object... values) {
    MatrixCursor cursor = new MatrixCursor(fetcher.getColumns());
    cursor.addRow(values);
    cursor.moveToFirst();
    return cursor;
  }

  @Test
  public void getFetcherPicksTheFetcherForEachFileType() {
    assertEquals(
        Constants.FILE_TYPE_AUDIO, TableFetchers.getFetcher(Constants.FILE_TYPE_AUDIO).getType());
    assertEquals(
        Constants.FILE_TYPE_PICTURES,
        TableFetchers.getFetcher(Constants.FILE_TYPE_PICTURES).getType());
    assertEquals(
        Constants.FILE_TYPE_VIDEOS, TableFetchers.getFetcher(Constants.FILE_TYPE_VIDEOS).getType());
    assertEquals(
        Constants.FILE_TYPE_DOCUMENTS,
        TableFetchers.getFetcher(Constants.FILE_TYPE_DOCUMENTS).getType());
    assertEquals(
        Constants.FILE_TYPE_RINGTONES,
        TableFetchers.getFetcher(Constants.FILE_TYPE_RINGTONES).getType());
    assertEquals(
        Constants.FILE_TYPE_TORRENTS,
        TableFetchers.getFetcher(Constants.FILE_TYPE_TORRENTS).getType());
    assertSame(TableFetchers.UNKNOWN_TABLE_FETCHER, TableFetchers.getFetcher((byte) 99));
    assertSame(
        TableFetchers.getFetcher(Constants.FILE_TYPE_AUDIO),
        TableFetchers.getFetcher(Constants.FILE_TYPE_AUDIO));
  }

  @Test
  public void unknownFetcherIsInert() {
    TableFetcher unknown = TableFetchers.UNKNOWN_TABLE_FETCHER;
    assertEquals(0, unknown.getColumns().length);
    assertNull(unknown.getSortByExpression());
    assertNull(unknown.getExternalContentUri());
    assertNull(unknown.fetchFWFileDescriptor(null));
    unknown.prepareColumnIds(null);
    assertEquals(0, unknown.getFileType());
    assertEquals(0, unknown.getType());
    assertNull(unknown.where());
    assertEquals(0, unknown.whereArgs().length);
  }

  @Test
  public void audioFetcherReadsADescriptorFromItsColumns() {
    TableFetcher fetcher = TableFetchers.getFetcher(Constants.FILE_TYPE_AUDIO);
    assertEquals(Constants.FILE_TYPE_AUDIO, fetcher.getFileType());
    assertTrue(fetcher.getSortByExpression().endsWith("DESC"));
    assertTrue(fetcher.getExternalContentUri().toString().startsWith("content://media/"));
    assertNull(fetcher.where());
    assertEquals(0, fetcher.whereArgs().length);

    Cursor cur =
        rowFor(
            fetcher,
            11,
            "Artist",
            "Song",
            "Album",
            "/m/song.mp3",
            "2019",
            "audio/mpeg",
            4096,
            100L,
            200L,
            55L);
    fetcher.prepareColumnIds(cur);
    FWFileDescriptor fd = fetcher.fetchFWFileDescriptor(cur);
    assertEquals(11, fd.id);
    assertEquals("Artist", fd.artist);
    assertEquals("Song", fd.title);
    assertEquals("Album", fd.album);
    assertEquals("2019", fd.year);
    assertEquals("/m/song.mp3", fd.filePath);
    assertEquals("audio/mpeg", fd.mime);
    assertEquals(4096, fd.fileSize);
    assertEquals(100L, fd.dateAdded);
    assertEquals(200L, fd.dateModified);
    assertEquals(55L, fd.albumId);
    assertEquals(Constants.FILE_TYPE_AUDIO, fd.fileType);
    // On Android 11+ apps can no longer delete media files directly.
    assertFalse(fd.deletable);
  }

  @Test
  public void picturesFetcherReadsADescriptor() {
    TableFetcher fetcher = TableFetchers.getFetcher(Constants.FILE_TYPE_PICTURES);
    assertEquals(Constants.FILE_TYPE_PICTURES, fetcher.getFileType());
    assertTrue(fetcher.getSortByExpression().endsWith("DESC"));
    assertTrue(fetcher.getExternalContentUri().toString().contains("images"));
    Cursor cur = rowFor(fetcher, 3, "Pic", "/p/a.jpg", "image/jpeg", 0L, 2048, 5L, 6L);
    fetcher.prepareColumnIds(cur);
    FWFileDescriptor fd = fetcher.fetchFWFileDescriptor(cur);
    assertEquals(3, fd.id);
    assertEquals("Pic", fd.title);
    assertEquals("/p/a.jpg", fd.filePath);
    assertEquals("image/jpeg", fd.mime);
    assertEquals(2048, fd.fileSize);
    assertEquals(Constants.FILE_TYPE_PICTURES, fd.fileType);
    assertNull(fd.artist);
  }

  @Test
  public void videosFetcherReadsADescriptor() {
    TableFetcher fetcher = TableFetchers.getFetcher(Constants.FILE_TYPE_VIDEOS);
    assertEquals(Constants.FILE_TYPE_VIDEOS, fetcher.getFileType());
    assertTrue(fetcher.getSortByExpression().endsWith("DESC"));
    assertTrue(fetcher.getExternalContentUri().toString().contains("video"));
    Cursor cur =
        rowFor(fetcher, 8, "Dir", "Movie", "Collection", "/v/m.mp4", "video/mp4", 0L, 9000, 7L, 8L);
    fetcher.prepareColumnIds(cur);
    FWFileDescriptor fd = fetcher.fetchFWFileDescriptor(cur);
    assertEquals(8, fd.id);
    assertEquals("Dir", fd.artist);
    assertEquals("Movie", fd.title);
    assertEquals("Collection", fd.album);
    assertEquals("/v/m.mp4", fd.filePath);
    assertEquals(9000, fd.fileSize);
    assertEquals(Constants.FILE_TYPE_VIDEOS, fd.fileType);
  }

  @Test
  public void documentsFetcherFiltersByExtensionAndExcludesNoise() {
    TableFetcher fetcher = TableFetchers.getFetcher(Constants.FILE_TYPE_DOCUMENTS);
    assertEquals(Constants.FILE_TYPE_DOCUMENTS, fetcher.getFileType());
    assertTrue(fetcher.getSortByExpression().endsWith("DESC"));
    assertTrue(fetcher.getExternalContentUri().toString().startsWith("content://media/"));
    String where = fetcher.where();
    assertTrue(where.contains("LIKE '%.pdf'"));
    assertTrue(where.contains("NOT LIKE ?"));
    assertArrayEquals(
        new String[] {"%cache%", "%/.%", "%/libtorrent/%", "%com.google.%"}, fetcher.whereArgs());
    // Every ? in the clause has an argument.
    assertEquals(fetcher.whereArgs().length, where.length() - where.replace("?", "").length());

    Cursor cur = rowFor(fetcher, 5, "/d/a.pdf", 300, "Doc", "application/pdf", 1L, 2L);
    fetcher.prepareColumnIds(cur);
    FWFileDescriptor fd = fetcher.fetchFWFileDescriptor(cur);
    assertEquals(5, fd.id);
    assertEquals("Doc", fd.title);
    assertEquals("/d/a.pdf", fd.filePath);
    assertEquals(300, fd.fileSize);
    assertEquals(Constants.FILE_TYPE_DOCUMENTS, fd.fileType);
  }

  @Test
  public void torrentsFetcherOnlySelectsTorrentFiles() {
    TableFetcher fetcher = TableFetchers.getFetcher(Constants.FILE_TYPE_TORRENTS);
    assertEquals(Constants.FILE_TYPE_DOCUMENTS, fetcher.getFileType());
    assertEquals(Constants.FILE_TYPE_TORRENTS, fetcher.getType());
    String where = fetcher.where();
    assertEquals(fetcher.whereArgs().length, where.length() - where.replace("?", "").length());
    assertEquals("%.torrent", fetcher.whereArgs()[3]);
  }

  @Test
  public void ringtonesFetcherReadsFromTheInternalStore() {
    TableFetchers.RingtonesTableFetcher fetcher =
        (TableFetchers.RingtonesTableFetcher)
            TableFetchers.getFetcher(Constants.FILE_TYPE_RINGTONES);
    assertNull(fetcher.getExternalContentUri());
    assertTrue(fetcher.getInternalContentUri().toString().contains("internal"));
    assertEquals(Constants.FILE_TYPE_RINGTONES, fetcher.getFileType());
    assertTrue(fetcher.getSortByExpression().endsWith("DESC"));
    assertTrue(fetcher.externalFolderFWFileDescriptors(null).isEmpty());
    Cursor cur =
        rowFor(fetcher, 2, "A", "Ring", "Al", "/r/ring.mp3", "2001", "audio/mpeg", 77, 1L, 2L);
    fetcher.prepareColumnIds(cur);
    FWFileDescriptor fd = fetcher.fetchFWFileDescriptor(cur);
    assertEquals("Ring", fd.title);
    assertEquals("A", fd.artist);
    assertEquals("2001", fd.year);
    assertEquals(Constants.FILE_TYPE_RINGTONES, fd.fileType);
  }

  @Test
  public void documentsFetcherInternalUriIsTheInternalVolume() {
    TableFetchers.AbstractFilesTableFetcher fetcher =
        (TableFetchers.AbstractFilesTableFetcher)
            TableFetchers.getFetcher(Constants.FILE_TYPE_DOCUMENTS);
    assertTrue(fetcher.getInternalContentUri().toString().contains("internal"));
  }

  @Test
  public void recursiveFilesCollectsOnlyFilesOfTheRequestedType() throws IOException {
    File sub = folder.newFolder("sub", "deeper");
    File song = folder.newFile("a.mp3");
    File nested = new File(sub, "b.mp3");
    assertTrue(nested.createNewFile());
    File pic = new File(sub, "c.jpg");
    assertTrue(pic.createNewFile());
    File unknown = new File(sub, "d.zzzzqq");
    assertTrue(unknown.createNewFile());

    List<File> found = new ArrayList<>();
    TableFetchers.getRecursiveFiles(folder.getRoot(), Constants.FILE_TYPE_AUDIO, found);
    assertEquals(2, found.size());
    assertTrue(found.contains(song));
    assertTrue(found.contains(nested));
    assertFalse(found.contains(pic));
    assertFalse(found.contains(unknown));
  }

  @Test
  public void recursiveFilesOnAFileDoesNothing() throws IOException {
    List<File> found = new ArrayList<>();
    TableFetchers.getRecursiveFiles(folder.newFile("x.mp3"), Constants.FILE_TYPE_AUDIO, found);
    assertTrue(found.isEmpty());
  }
}
