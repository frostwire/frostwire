/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.BaseColumns;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.core.player.EphemeralPlaylist;
import com.frostwire.android.core.providers.TableFetcher;
import com.frostwire.android.core.providers.TableFetchers;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.platform.SystemPaths;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class LibrarianTest {
  private MockedStatic<SystemUtils> system;
  private Context context;
  private ContentResolver resolver;
  private final List<Runnable> posted = new ArrayList<>();
  private final Librarian librarian = Librarian.instance();

  @Before
  public void setUp() {
    context = mock(Context.class);
    resolver = mock(ContentResolver.class);
    when(context.getContentResolver()).thenReturn(resolver);
    system = mockStatic(SystemUtils.class);
    system.when(SystemUtils::getApplicationContext).thenReturn(context);
    system
        .when(() -> SystemUtils.postToHandler(any(), any(Runnable.class)))
        .thenAnswer(
            call -> {
              posted.add(call.getArgument(1));
              return null;
            });
  }

  @After
  public void tearDown() {
    system.close();
  }

  @Test
  public void theLibrarianIsASingletonWithABackgroundHandler() {
    assertSame(librarian, Librarian.instance());
    Handler handler = librarian.getHandler();
    assertNotNull(handler);
    assertSame(handler.getLooper().getThread(), librarian.getHandlerThread());
    librarian.shutdownHandler();
  }

  @Test
  public void numFilesCountsRowsOfTheTypeSpecificQuery() {
    MatrixCursor counted = new MatrixCursor(new String[] {"c"});
    counted.addRow(new Object[] {42});
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(counted);
    assertEquals(42, librarian.getNumFiles(context, Constants.FILE_TYPE_AUDIO));
  }

  @Test
  public void numFilesIsZeroForUnknownTypesNullCursorsAndFailures() {
    assertEquals(0, librarian.getNumFiles(context, (byte) 99));
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(null);
    assertEquals(0, librarian.getNumFiles(context, Constants.FILE_TYPE_AUDIO));
    when(resolver.query(any(Uri.class), any(), any(), any(), any()))
        .thenThrow(new SecurityException("denied"));
    assertEquals(0, librarian.getNumFiles(context, Constants.FILE_TYPE_AUDIO));
  }

  @Test
  public void filesAreReadFromTheCursorUpToThePageSize() {
    FWFileDescriptor first = descriptor(1, "/a");
    FWFileDescriptor second = descriptor(2, "/b");
    FWFileDescriptor third = descriptor(3, "/c");
    TableFetcher fetcher = fetcher();
    when(fetcher.fetchFWFileDescriptor(any(Cursor.class))).thenReturn(first, second, third);
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(rows(3));
    List<FWFileDescriptor> page =
        librarian.getFilesInAndroidMediaStore(context, 0, 2, fetcher, "w", new String[] {"a"});
    assertEquals(2, page.size());
    assertSame(first, page.get(0));
    assertSame(second, page.get(1));
    verify(fetcher).prepareColumnIds(any(Cursor.class));

    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(rows(3));
    when(fetcher.fetchFWFileDescriptor(any(Cursor.class))).thenReturn(third);
    List<FWFileDescriptor> skipped =
        librarian.getFilesInAndroidMediaStore(context, 2, 10, fetcher, "w", new String[] {"a"});
    assertEquals(1, skipped.size());
  }

  @Test
  public void aMissingWhereFallsBackToTheFetchersOwnFilter() {
    TableFetcher fetcher = fetcher();
    when(fetcher.where()).thenReturn("own = ?");
    when(fetcher.whereArgs()).thenReturn(new String[] {"x"});
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(rows(0));
    assertTrue(librarian.getFilesInAndroidMediaStore(context, 0, 5, fetcher, null, null).isEmpty());
    verify(resolver).query(any(Uri.class), any(), eq("own = ?"), eq(new String[] {"x"}), any());
  }

  @Test
  public void unusableInputsGiveEmptyResults() {
    TableFetcher fetcher = fetcher();
    assertTrue(librarian.getFilesInAndroidMediaStore(null, 0, 5, fetcher, null, null).isEmpty());
    assertTrue(librarian.getFilesInAndroidMediaStore(context, 0, 5, null, null, null).isEmpty());
    assertTrue(
        librarian
            .getFilesInAndroidMediaStore(
                context, 0, 5, TableFetchers.UNKNOWN_TABLE_FETCHER, null, null)
            .isEmpty());
    when(resolver.query(any(Uri.class), any(), any(), any(), any()))
        .thenThrow(new IllegalStateException("provider died"));
    assertTrue(librarian.getFilesInAndroidMediaStore(context, 0, 5, fetcher, "w", null).isEmpty());
    List<FWFileDescriptor> out = new ArrayList<>();
    librarian.getFilesInVolume(resolver, null, 0, 1, null, null, null, null, fetcher, out);
    assertTrue(out.isEmpty());
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(null);
    librarian.getFilesInVolume(
        resolver, Uri.parse("content://x/y"), 0, 1, null, null, null, null, fetcher, out);
    assertTrue(out.isEmpty());
  }

  @Test
  public void aFileDescriptorIsLookedUpById() {
    FWFileDescriptor fd = descriptor(7, "/seven");
    TableFetcher fetcher = fetcher();
    when(fetcher.fetchFWFileDescriptor(any(Cursor.class))).thenReturn(fd);
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(rows(1));
    // the real audio fetcher needs a real cursor layout, so go through a type whose query is empty
    assertNull(librarian.getFileDescriptor(context, Constants.FILE_TYPE_AUDIO, 7));
    ArgumentCaptor<String> where = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String[]> args = ArgumentCaptor.forClass(String[].class);
    verify(resolver).query(any(Uri.class), any(), where.capture(), args.capture(), any());
    assertEquals(BaseColumns._ID + "=?", where.getValue());
    assertEquals("7", args.getValue()[0]);
  }

  @Test
  public void filesInAFolderAreQueriedWithALikeFilterRelativeToTheDataDir() {
    Platform platform = mock(Platform.class);
    SystemPaths paths = mock(SystemPaths.class);
    when(platform.systemPaths()).thenReturn(paths);
    when(paths.data()).thenReturn(new File("/storage/data"));
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(rows(0));
    try (MockedStatic<Platforms> platforms = mockStatic(Platforms.class)) {
      platforms.when(Platforms::get).thenReturn(platform);
      assertTrue(
          librarian
              .getFilesInAndroidMediaStore(context, "/storage/data/Music/a.mp3", true)
              .isEmpty());
      verify(resolver)
          .query(
              any(Uri.class), any(), eq("_data LIKE ?"), eq(new String[] {"/Music/a.mp3"}), any());
      librarian.getFilesInAndroidMediaStore(
          context, Constants.FILE_TYPE_AUDIO, "/storage/data/Music/", false);
      verify(resolver)
          .query(any(Uri.class), any(), eq("_data LIKE ?"), eq(new String[] {"%/Music/%"}), any());
    }
  }

  @Test
  public void renamingOnTheUiThreadIsPostedAndReturnsNothing() {
    system.when(SystemUtils::isUIThread).thenReturn(true);
    FWFileDescriptor fd = descriptor(1, "/x/old.mp3");
    assertNull(librarian.renameFile(context, fd, "new"));
    assertEquals(1, posted.size());
  }

  @Test
  public void renamingMovesTheFileAndUpdatesTheMediaStore() throws Exception {
    File dir = Files.createTempDirectory("librarian").toFile();
    File old = new File(dir, "old.mp3");
    assertTrue(old.createNewFile());
    try {
      system.when(SystemUtils::isUIThread).thenReturn(false);
      FWFileDescriptor fd = descriptor(5, old.getAbsolutePath());
      fd.fileType = Constants.FILE_TYPE_AUDIO;
      String renamed = librarian.renameFile(context, fd, "fresh");
      assertEquals(new File(dir, "fresh.mp3").getAbsolutePath(), renamed);
      assertTrue(new File(dir, "fresh.mp3").exists());
      assertFalse(old.exists());
      ArgumentCaptor<ContentValues> values = ArgumentCaptor.forClass(ContentValues.class);
      verify(resolver)
          .update(
              any(Uri.class), values.capture(), eq(BaseColumns._ID + "=?"), eq(new String[] {"5"}));
      assertEquals("fresh", values.getValue().getAsString("title"));
    } finally {
      new File(dir, "fresh.mp3").delete();
      old.delete();
      dir.delete();
    }
  }

  @Test
  public void renamingSurvivesAFailingMediaStoreAndBadDescriptors() throws Exception {
    system.when(SystemUtils::isUIThread).thenReturn(false);
    when(resolver.update(any(Uri.class), any(), any(), any()))
        .thenThrow(new SecurityException("denied"));
    FWFileDescriptor fd = descriptor(5, "/nonexistent/old.mp3");
    fd.fileType = Constants.FILE_TYPE_AUDIO;
    assertEquals(
        new File("/nonexistent", "fresh.mp3").getAbsolutePath(),
        librarian.renameFile(context, fd, "fresh"));
    // unknown file type: no media store update
    FWFileDescriptor unknown = descriptor(6, "/nonexistent/old.xyz");
    unknown.fileType = (byte) 99;
    assertNotNull(librarian.renameFile(context, unknown, "n"));
    // null descriptor path blows up and is reported as null
    assertNull(librarian.renameFile(context, new FWFileDescriptor(), "n"));
  }

  @Test
  public void aDeletableFileGetsAPlaylistOfOne() {
    FWFileDescriptor fd = descriptor(1, "/x/one.mp3");
    fd.deletable = true;
    EphemeralPlaylist playlist = librarian.createEphemeralPlaylist(context, fd);
    assertEquals(1, playlist.getItems().size());
    assertSame(fd, playlist.getCurrentItem() == null ? fd : playlist.getCurrentItem().getFD());
  }

  @Test
  public void aMediaStoreFileGetsItsFolderAsPlaylistOrItselfIfTheFolderIsEmpty() {
    FWFileDescriptor fd = descriptor(1, "/x/one.mp3");
    fd.deletable = false;
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(null);
    Platform platform = mock(Platform.class);
    SystemPaths paths = mock(SystemPaths.class);
    when(platform.systemPaths()).thenReturn(paths);
    when(paths.data()).thenReturn(new File("/data"));
    try (MockedStatic<Platforms> platforms = mockStatic(Platforms.class)) {
      platforms.when(Platforms::get).thenReturn(platform);
      EphemeralPlaylist playlist = librarian.createEphemeralPlaylist(context, fd);
      assertEquals("the logic-error fallback adds the file itself", 1, playlist.getItems().size());
    }
  }

  @Test
  public void savingToDownloadsFailsWithoutAnApplicationContext() {
    system.when(SystemUtils::getApplicationContext).thenReturn(null);
    AtomicReference<Throwable> error = new AtomicReference<>();
    assertFalse(
        Librarian.mediaStoreSaveToDownloads(
            new File("/a/b.mp3"), new File("/Download/b.mp3"), false, error));
    assertTrue(error.get() instanceof IOException);
    assertFalse(Librarian.mediaStoreSaveToDownloads(new File("/a/b.mp3"), new File("/c"), false));
  }

  private static FWFileDescriptor descriptor(int id, String path) {
    FWFileDescriptor fd = new FWFileDescriptor();
    fd.id = id;
    fd.filePath = path;
    return fd;
  }

  private static TableFetcher fetcher() {
    TableFetcher fetcher = mock(TableFetcher.class);
    when(fetcher.getExternalContentUri()).thenReturn(Uri.parse("content://media/external/test"));
    when(fetcher.getColumns()).thenReturn(new String[] {"_id"});
    when(fetcher.getSortByExpression()).thenReturn("_id");
    return fetcher;
  }

  private static Cursor rows(int count) {
    MatrixCursor cursor = new MatrixCursor(new String[] {"_id"});
    for (int i = 0; i < count; i++) {
      cursor.addRow(new Object[] {i});
    }
    return cursor;
  }
}
