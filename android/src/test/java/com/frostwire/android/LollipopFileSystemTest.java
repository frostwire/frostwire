/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.platform.AppSettings;
import com.frostwire.platform.FileFilter;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Storage Access Framework file system falls back to plain java.io whenever the file is not on
 * an external SD card, which is the part exercised here. Real SAF documents need a device.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class LollipopFileSystemTest {

  /** Accepts names with a suffix and records every file the walk visits. */
  private static final class NameFilter implements FileFilter {
    final String suffix;
    final List<String> visited = new ArrayList<>();

    NameFilter(String suffix) {
      this.suffix = suffix;
    }

    @Override
    public boolean accept(File file) {
      return file.getName().endsWith(suffix);
    }

    @Override
    public void file(File file) {
      visited.add(file.getName());
    }
  }

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final Application app = ApplicationProvider.getApplicationContext();
  private final LollipopFileSystem fs = new LollipopFileSystem(app);

  private File file(String name, String content) throws IOException {
    File f = folder.newFile(name);
    Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
    return f;
  }

  @Test
  public void realFilesAndFoldersAreAnsweredByThePlainFileApi() throws IOException {
    File f = file("a.txt", "hello");
    File dir = folder.newFolder("dir");
    assertTrue(fs.isFile(f));
    assertFalse(fs.isDirectory(f));
    assertTrue(fs.isDirectory(dir));
    assertFalse(fs.isFile(dir));
    assertTrue(fs.exists(f));
    assertTrue(fs.canRead(f));
    assertTrue(fs.canWrite(f));
    assertEquals(5, fs.length(f));
    assertEquals(f.lastModified(), fs.lastModified(f));
  }

  @Test
  public void missingFilesAreReportedAsMissing() {
    File missing = new File(folder.getRoot(), "missing.txt");
    assertFalse(fs.exists(missing));
    assertFalse(fs.isFile(missing));
    assertFalse(fs.isDirectory(missing));
    assertFalse(fs.canRead(missing));
    assertFalse(fs.canWrite(missing));
    assertEquals(0, fs.length(missing));
    assertEquals(0, fs.lastModified(missing));
    assertFalse(fs.delete(missing));
    assertNull(fs.getDocument(missing));
  }

  @Test
  public void emptyFilesFallBackToTheDocumentAndStillReportZero() throws IOException {
    File empty = folder.newFile("empty.bin");
    assertEquals(0, fs.length(empty));
    assertNotNull(fs.getDocument(empty));
    assertEquals(0, fs.getDocument(empty).length());
  }

  @Test
  public void mkdirsCreatesNestedFoldersAndReportsExistingOnesAsNotCreated() {
    File nested = new File(folder.getRoot(), "a/b/c");
    assertTrue(fs.mkdirs(nested));
    assertTrue(nested.isDirectory());
    assertFalse("already there", fs.mkdirs(nested));
  }

  @Test
  public void deleteRemovesAFile() throws IOException {
    File f = file("gone.txt", "x");
    assertTrue(fs.delete(f));
    assertFalse(f.exists());
  }

  @Test
  public void listFilesFiltersTheFolderContents() throws IOException {
    file("a.mp3", "1");
    file("b.mp3", "2");
    file("c.txt", "3");
    FileFilter mp3 = new NameFilter(".mp3");
    File[] found = fs.listFiles(folder.getRoot(), mp3);
    List<String> names = new ArrayList<>();
    for (File f : found) {
      names.add(f.getName());
    }
    java.util.Collections.sort(names);
    assertEquals(Arrays.asList("a.mp3", "b.mp3"), names);
    assertEquals(3, fs.listFiles(folder.getRoot(), null).length);
  }

  @Test
  public void listFilesOfAMissingFolderIsNull() {
    assertNull(fs.listFiles(new File(folder.getRoot(), "nope"), null));
  }

  @Test
  public void walkVisitsEveryFileUnderTheFolder() throws IOException {
    file("top.txt", "1");
    File sub = folder.newFolder("sub");
    Files.write(new File(sub, "inner.txt").toPath(), new byte[] {1});
    List<String> seen = new ArrayList<>();
    NameFilter all = new NameFilter("");
    fs.walk(folder.getRoot(), all);
    seen.addAll(all.visited);
    java.util.Collections.sort(seen);
    assertTrue(seen.contains("top.txt"));
    assertTrue(seen.contains("inner.txt"));
  }

  @Test
  public void copyDuplicatesTheContent() throws IOException {
    File src = file("src.txt", "payload");
    File dest = new File(folder.getRoot(), "out/dest.txt");
    assertTrue(fs.copy(src, dest));
    assertEquals("payload", new String(Files.readAllBytes(dest.toPath()), StandardCharsets.UTF_8));
    assertNull(fs.lastCopyError());
  }

  @Test
  public void copyOfAMissingSourceFailsAndRemembersWhy() {
    File missing = new File(folder.getRoot(), "missing.txt");
    File dest = new File(folder.getRoot(), "dest.txt");
    assertFalse(fs.copy(missing, dest));
    assertNotNull(fs.lastCopyError());
  }

  @Test
  public void aSuccessfulCopyClearsThePreviousError() throws IOException {
    assertFalse(fs.copy(new File(folder.getRoot(), "missing"), new File(folder.getRoot(), "d")));
    assertNotNull(fs.lastCopyError());
    assertTrue(fs.copy(file("ok.txt", "1"), new File(folder.getRoot(), "ok-copy.txt")));
    assertNull(fs.lastCopyError());
  }

  @Test
  public void writeStoresTheBytes() throws IOException {
    File target = new File(folder.getRoot(), "w/data.bin");
    assertTrue(fs.write(target, new byte[] {1, 2, 3}));
    assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(target.toPath()));
  }

  @Test
  public void openFdOnlyAcceptsKnownModes() throws IOException {
    File f = file("fd.txt", "x");
    assertEquals(-1, fs.openFD(f, "x"));
    assertEquals(-1, fs.openFD(f, null));
    assertEquals(-1, fs.openFD(f, "rwa"));
  }

  @Test
  public void treeAndDocumentPathsAreResolvedFromTheUri() {
    assertNull(fs.getTreePath(null));
    assertNull(fs.getDocumentPath(null));
    Uri tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic");
    // Robolectric has no storage volumes, so the volume root falls back to "/".
    assertEquals("/", fs.getTreePath(tree));
    assertThrows(
        IllegalArgumentException.class,
        () -> fs.getTreePath(Uri.parse("content://com.android.externalstorage.documents/x")));
  }

  @Test
  public void filesOutsideAnySdCardHaveNoExtSdCardFolder() {
    assertNull(fs.getExtSdCardFolder(new File(folder.getRoot(), "a.txt")));
    assertNull(
        fs.getExtSdCardFolder(
            new File("/storage/emulated/0/Android/data/com.frostwire.android/x")));
  }

  @Test
  public void aFileOnAFixedSdCardPathIsRecognisedEvenWithoutMountedVolumes() {
    assertEquals(
        "/storage/extsdcard", fs.getExtSdCardFolder(new File("/storage/extsdcard/Music/a.mp3")));
    assertEquals("/mnt/external_sd", fs.getExtSdCardFolder(new File("/mnt/external_sd/a.mp3")));
  }

  @Test
  public void missingDocumentsOnASdCardWithoutAGrantedTreeAreNull() {
    AppSettings settings = mock(AppSettings.class);
    Platform platform = mock(Platform.class);
    when(platform.appSettings()).thenReturn(settings);
    try (MockedStatic<Platforms> platforms = Mockito.mockStatic(Platforms.class)) {
      platforms.when(Platforms::appSettings).thenReturn(settings);
      File onCard = new File("/storage/extsdcard/Music/a.mp3");
      // No volume UUID is resolvable, so the tree URI can't be built and SAF lookups fail safely.
      assertNull(fs.getDocument(onCard));
      assertFalse(fs.exists(onCard));
      assertFalse(fs.isFile(onCard));
      assertFalse(fs.mkdirs(new File("/storage/extsdcard/Music/new")));
      assertNull(fs.listFiles(new File("/storage/extsdcard/Music"), null));
      assertFalse(fs.copy(onCard, new File(folder.getRoot(), "copy.mp3")));
      assertNotNull(fs.lastCopyError());
      assertFalse(fs.write(new File("/storage/extsdcard/Music/w.bin"), new byte[] {1}));
      assertEquals(-1, fs.openFD(new File("/storage/extsdcard/Music/fd.bin"), "rw"));
    }
  }
}
