/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.MatrixCursor;
import android.net.Uri;
import com.frostwire.android.AndroidPaths;
import com.frostwire.android.util.SystemUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Saving finished downloads into the public Downloads folder through the MediaStore (Android 10+).
 * AndroidPaths reads the BitTorrent engine's context, which a unit test cannot create, so its
 * folder arithmetic is stubbed; the MediaStore protocol is what is under test.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class LibrarianMediaStoreTest {
  private static final File DESTINATION_DIR = new File("/storage/emulated/0/Download/FrostWire");

  private MockedStatic<SystemUtils> system;
  private MockedStatic<AndroidPaths> paths;
  private Context context;
  private ContentResolver resolver;

  @Before
  public void setUp() {
    context = mock(Context.class);
    resolver = mock(ContentResolver.class);
    when(context.getContentResolver()).thenReturn(resolver);
    system = mockStatic(SystemUtils.class);
    system.when(SystemUtils::getApplicationContext).thenReturn(context);
    paths = mockStatic(AndroidPaths.class);
    paths
        .when(() -> AndroidPaths.getRelativeFolderPathFromFileInDownloads(any(File.class)))
        .thenReturn("Download/FrostWire");
    paths
        .when(
            () ->
                AndroidPaths.getFileType(
                    any(String.class), org.mockito.ArgumentMatchers.anyBoolean()))
        .thenCallRealMethod();
    paths
        .when(() -> AndroidPaths.getDestinationFileFromInternalFileInAndroid10(any(File.class)))
        .thenAnswer(call -> new File(DESTINATION_DIR, ((File) call.getArgument(0)).getName()));
  }

  @After
  public void tearDown() {
    paths.close();
    system.close();
  }

  /** The rows given to insert(), copied at call time because Librarian reuses the object. */
  private final java.util.List<ContentValues> inserted = new java.util.ArrayList<>();

  private void insertReturns(Uri uri) {
    when(resolver.insert(any(Uri.class), any(ContentValues.class)))
        .thenAnswer(
            call -> {
              inserted.add(new ContentValues((ContentValues) call.getArgument(1)));
              return uri;
            });
  }

  private void mediaStoreEmpty() {
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(null);
  }

  @Test
  public void savingFailsWithoutAnApplicationContext() {
    system.when(SystemUtils::getApplicationContext).thenReturn(null);
    AtomicReference<Throwable> error = new AtomicReference<>();
    assertFalse(
        Librarian.mediaStoreSaveToDownloads(
            new File("/a/b.mp3"), new File(DESTINATION_DIR, "b.mp3"), false, error));
    assertTrue(error.get() instanceof IOException);
    assertFalse(
        Librarian.mediaStoreSaveToDownloads(
            new File("/a/b.mp3"), new File(DESTINATION_DIR, "b.mp3"), false));
  }

  @Test
  public void savingRefusesToOverwriteAFileTheMediaStoreAlreadyHas() {
    MatrixCursor existing = new MatrixCursor(new String[] {"_id"});
    existing.addRow(new Object[] {1});
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(existing);
    AtomicReference<Throwable> error = new AtomicReference<>();
    assertFalse(
        Librarian.mediaStoreSaveToDownloads(
            new File("/a/b.txt"), new File(DESTINATION_DIR, "b.txt"), true, error));
    assertTrue(error.get().getMessage().contains("already exists"));
    verify(resolver, never()).insert(any(Uri.class), any(ContentValues.class));
  }

  @Test
  public void aFileThatIsNotInTheMediaStoreIsReportedMissing() {
    mediaStoreEmpty();
    assertFalse(Librarian.mediaStoreFileExists(new File(DESTINATION_DIR, "n.txt")));
    when(resolver.query(any(Uri.class), any(), any(), any(), any()))
        .thenReturn(new MatrixCursor(new String[] {"_id"}));
    assertFalse(Librarian.mediaStoreFileExists(new File(DESTINATION_DIR, "n.txt")));
    MatrixCursor found = new MatrixCursor(new String[] {"_id"});
    found.addRow(new Object[] {3});
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(found);
    assertTrue(Librarian.mediaStoreFileExists(new File(DESTINATION_DIR, "n.txt")));
  }

  @Test
  public void savingInsertsAPendingRowCopiesTheBytesAndPublishesIt() throws Exception {
    File src = File.createTempFile("librarian-src", ".txt");
    Files.write(src.toPath(), new byte[] {1, 2, 3});
    try {
      mediaStoreEmpty();
      Uri uri = Uri.parse("content://media/external/downloads/9");
      insertReturns(uri);
      ByteArrayOutputStream sink = new ByteArrayOutputStream();
      when(resolver.openOutputStream(uri)).thenReturn(sink);
      assertTrue(
          Librarian.mediaStoreSaveToDownloads(src, new File(DESTINATION_DIR, src.getName()), true));
      assertEquals(3, sink.size());
      assertEquals(1, inserted.size());
      assertEquals(src.getName(), inserted.get(0).getAsString("_display_name"));
      assertEquals("Download/FrostWire", inserted.get(0).getAsString("relative_path"));
      assertEquals(Integer.valueOf(1), inserted.get(0).getAsInteger("is_pending"));
      ArgumentCaptor<ContentValues> published = ArgumentCaptor.forClass(ContentValues.class);
      verify(resolver).update(eq(uri), published.capture(), any(), any());
      assertEquals(Integer.valueOf(0), published.getValue().getAsInteger("is_pending"));
    } finally {
      src.delete();
    }
  }

  @Test
  public void savingWithoutCopyingJustRegistersTheRowAsNotPending() throws Exception {
    File src = File.createTempFile("librarian-nocopy", ".txt");
    try {
      mediaStoreEmpty();
      insertReturns(Uri.parse("content://media/external/downloads/10"));
      assertTrue(
          Librarian.mediaStoreSaveToDownloads(
              src, new File(DESTINATION_DIR, src.getName()), false));
      verify(resolver, never()).openOutputStream(any(Uri.class));
      assertEquals(Integer.valueOf(0), inserted.get(0).getAsInteger("is_pending"));
      assertEquals(src.getName(), inserted.get(0).getAsString("title"));
    } finally {
      src.delete();
    }
  }

  @Test
  public void everyWayTheInsertCanFailIsReportedThroughTheErrorReference() throws Exception {
    File src = File.createTempFile("librarian-fail", ".txt");
    File dir = Files.createTempDirectory("librarian-dir").toFile();
    try {
      mediaStoreEmpty();
      File destination = new File(DESTINATION_DIR, src.getName());
      AtomicReference<Throwable> error = new AtomicReference<>();
      assertFalse(Librarian.mediaStoreSaveToDownloads(dir, destination, true, error));
      assertTrue(error.get().getMessage().contains("directory"));

      when(resolver.insert(any(Uri.class), any(ContentValues.class))).thenReturn(null);
      assertFalse(Librarian.mediaStoreSaveToDownloads(src, destination, true, error));
      assertTrue(error.get().getMessage().contains("returned null"));

      when(resolver.insert(any(Uri.class), any(ContentValues.class)))
          .thenThrow(new IllegalStateException("insert boom"));
      assertFalse(Librarian.mediaStoreSaveToDownloads(src, destination, true, error));
      assertEquals("insert boom", error.get().getMessage());

      Uri uri = Uri.parse("content://media/external/downloads/11");
      when(resolver.insert(any(Uri.class), any(ContentValues.class))).thenReturn(uri);
      when(resolver.openOutputStream(uri)).thenReturn(null);
      assertFalse(Librarian.mediaStoreSaveToDownloads(src, destination, true, error));

      when(resolver.openOutputStream(uri)).thenThrow(new java.io.FileNotFoundException("disk"));
      assertFalse(Librarian.mediaStoreSaveToDownloads(src, destination, true, error));
      assertEquals("disk", error.get().getMessage());
    } finally {
      src.delete();
      dir.delete();
    }
  }

  @Test
  public void audioAndVideoRowsCarryTheirNameAndMimeTypeWhateverTheMetadataSays() throws Exception {
    File song = File.createTempFile("librarian-song", ".mp3");
    File video = File.createTempFile("librarian-video", ".mp4");
    try {
      mediaStoreEmpty();
      insertReturns(Uri.parse("content://media/external/downloads/12"));
      assertTrue(
          Librarian.mediaStoreSaveToDownloads(
              song, new File(DESTINATION_DIR, song.getName()), false));
      assertTrue(
          Librarian.mediaStoreSaveToDownloads(
              video, new File(DESTINATION_DIR, video.getName()), false));
      assertEquals(2, inserted.size());
      assertEquals(song.getName(), inserted.get(0).getAsString("_display_name"));
      assertEquals("audio/mpeg", inserted.get(0).getAsString("mime_type"));
      assertEquals(video.getName(), inserted.get(1).getAsString("_display_name"));
      assertEquals("video/mp4", inserted.get(1).getAsString("mime_type"));
    } finally {
      song.delete();
      video.delete();
    }
  }

  @Test
  public void deletingRemovesTheMatchingMediaStoreRow() {
    MatrixCursor found = new MatrixCursor(new String[] {"_id"});
    found.addRow(new Object[] {77L});
    when(resolver.query(any(Uri.class), any(), any(), any(), any())).thenReturn(found);
    when(resolver.delete(any(Uri.class), any(), any())).thenReturn(1);
    Librarian.mediaStoreDeleteFromDownloads(
        new File("/storage/emulated/0/Android/data/com.frostwire.android/files/x.txt"));
    ArgumentCaptor<Uri> deleted = ArgumentCaptor.forClass(Uri.class);
    verify(resolver).delete(deleted.capture(), any(), any());
    assertTrue(deleted.getValue().toString().endsWith("/77"));
  }

  @Test
  public void deletingAFileTheMediaStoreDoesNotKnowDoesNothing() {
    File internal = new File("/storage/emulated/0/Android/data/com.frostwire.android/files/y.txt");
    mediaStoreEmpty();
    Librarian.mediaStoreDeleteFromDownloads(internal);
    when(resolver.query(any(Uri.class), any(), any(), any(), any()))
        .thenReturn(new MatrixCursor(new String[] {"_id"}));
    Librarian.mediaStoreDeleteFromDownloads(internal);
    verify(resolver, never()).delete(any(Uri.class), any(), any());
  }
}
