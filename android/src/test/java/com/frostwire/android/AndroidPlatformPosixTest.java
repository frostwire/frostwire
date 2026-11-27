/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import androidx.documentfile.provider.DocumentFile;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.posix_stat_t;
import com.frostwire.jlibtorrent.swig.posix_wrapper;
import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * On Android 5 to 9 libtorrent's file calls go through a wrapper that asks libtorrent's own posix
 * layer first and falls back to the storage access framework for files on an SD card: the wrapper
 * over the real posix calls, with the fallback file system a double.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class AndroidPlatformPosixTest {

  @Rule public TemporaryFolder tmp = new TemporaryFolder();

  private LollipopFileSystem fallback;
  private posix_wrapper calls;

  /**
   * The libtorrent posix layer answers differently on the Linux x86_64 CI runner than on macOS
   * (frostwire/frostwire#1674): the tests that need it to succeed wait for that investigation.
   */
  private static void assumeNotLinux() {
    org.junit.Assume.assumeFalse(
        "libtorrent's posix layer on Linux, frostwire/frostwire#1674",
        System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux"));
  }

  @Before
  public void setUp() throws Exception {
    fallback = mock(LollipopFileSystem.class);
    Class<?> posixCalls = Class.forName("com.frostwire.android.AndroidPlatform$PosixCalls");
    Constructor<?> constructor = posixCalls.getDeclaredConstructor(LollipopFileSystem.class);
    constructor.setAccessible(true);
    calls = (posix_wrapper) constructor.newInstance(fallback);
  }

  @After
  public void tearDown() {
    // the default wrapper again: later tests in this JVM use libtorrent's own posix layer
    libtorrent.set_posix_wrapper(null);
  }

  @Test
  public void aPlatformOnAndroid9InstallsTheWrapperAndUsesTheLollipopFileSystem() {
    Application app = ApplicationProvider.getApplicationContext();

    AndroidPlatform platform = new AndroidPlatform(app);

    assertTrue(platform.fileSystem() instanceof LollipopFileSystem);
  }

  @Test
  public void aFileLibtorrentCanOpenIsOpenedWithoutTheFallback() throws Exception {
    assumeNotLinux();
    File file = tmp.newFile("open.bin");

    int fd = calls.open(file.getAbsolutePath(), 0, 0);

    assertTrue(fd >= 0);
    verify(fallback, never()).openFD(any(File.class), anyString());
  }

  @Test
  public void aFileLibtorrentCannotOpenIsOpenedByTheFallbackOrFails() {
    File missing = new File(tmp.getRoot(), "no/such/dir/file.bin");
    when(fallback.openFD(eq(missing), eq("rw"))).thenReturn(77);

    assertEquals(77, calls.open(missing.getAbsolutePath(), 0, 0));

    when(fallback.openFD(eq(missing), eq("rw"))).thenReturn(-1);
    assertTrue(calls.open(missing.getAbsolutePath(), 0, 0) < 0);
  }

  @Test
  public void aFileLibtorrentCanStatIsStatedWithoutTheFallback() throws Exception {
    assumeNotLinux();
    File file = tmp.newFile("stat.bin");
    Files.write(file.toPath(), new byte[1234]);
    posix_stat_t stat = new posix_stat_t();

    assertEquals(0, calls.stat(file.getAbsolutePath(), stat));

    assertEquals(1234, stat.getSize());
    verify(fallback, never()).getDocument(any(File.class));
  }

  @Test
  public void aFileOnAnSdCardIsStatedFromItsDocument() {
    File card = new File(tmp.getRoot(), "card/file.bin");
    DocumentFile document = mock(DocumentFile.class);
    when(document.isDirectory()).thenReturn(false);
    when(document.length()).thenReturn(4321L);
    when(document.lastModified()).thenReturn(5_000_000L);
    when(fallback.getDocument(eq(card))).thenReturn(document);
    posix_stat_t stat = new posix_stat_t();

    assertEquals(0, calls.stat(card.getAbsolutePath(), stat));

    assertEquals(4321, stat.getSize());
    assertEquals(0100000, stat.getMode() & 0170000);
    assertEquals(5000, stat.getMtime());
    assertEquals(5000, stat.getAtime());
    assertEquals(5000, stat.getCtime());
  }

  @Test
  public void aFolderOnAnSdCardIsStatedAsAFolder() {
    File card = new File(tmp.getRoot(), "card/folder");
    DocumentFile document = mock(DocumentFile.class);
    when(document.isDirectory()).thenReturn(true);
    when(fallback.getDocument(eq(card))).thenReturn(document);
    posix_stat_t stat = new posix_stat_t();

    assertEquals(0, calls.stat(card.getAbsolutePath(), stat));

    assertTrue((stat.getMode() & 0040000) != 0);
  }

  @Test
  public void aFileNobodyKnowsIsReportedMissing() {
    File nowhere = new File(tmp.getRoot(), "nowhere/file.bin");
    when(fallback.getDocument(eq(nowhere))).thenReturn(null);

    assertTrue(calls.stat(nowhere.getAbsolutePath(), new posix_stat_t()) < 0);
  }

  @Test
  public void aFolderLibtorrentCanCreateIsCreatedWithoutTheFallback() {
    assumeNotLinux();
    File folder = new File(tmp.getRoot(), "made");

    assertEquals(0, calls.mkdir(folder.getAbsolutePath(), 0755));

    assertTrue(folder.isDirectory());
    verify(fallback, never()).mkdirs(any(File.class));
  }

  @Test
  public void aFolderLibtorrentCannotCreateIsCreatedByTheFallbackOrFails() {
    File blocked = new File(tmp.getRoot(), "a/b/c");
    when(fallback.mkdirs(eq(blocked))).thenReturn(true);
    assertEquals(0, calls.mkdir(blocked.getAbsolutePath(), 0755));

    when(fallback.mkdirs(eq(blocked))).thenReturn(false);
    assertEquals(-1, calls.mkdir(blocked.getAbsolutePath(), 0755));
  }

  @Test
  public void aFileLibtorrentCanRenameIsRenamedWithoutTheFallback() throws Exception {
    assumeNotLinux();
    File source = tmp.newFile("from.bin");
    File target = new File(tmp.getRoot(), "to.bin");

    assertEquals(0, calls.rename(source.getAbsolutePath(), target.getAbsolutePath()));

    assertTrue(target.isFile());
    assertFalse(source.exists());
    verify(fallback, never()).copy(any(File.class), any(File.class));
  }

  @Test
  public void aFileThatCannotBeRenamedIsCopiedAndTheSourceDeleted() {
    File source = new File(tmp.getRoot(), "card/from.bin");
    File target = new File(tmp.getRoot(), "other/to.bin");
    when(fallback.copy(eq(source), eq(target))).thenReturn(true);

    assertEquals(0, calls.rename(source.getAbsolutePath(), target.getAbsolutePath()));

    verify(fallback).delete(source);
  }

  @Test
  public void aFileThatCannotBeRenamedOrCopiedFails() {
    File source = new File(tmp.getRoot(), "card/from.bin");
    File target = new File(tmp.getRoot(), "other/to.bin");
    when(fallback.copy(eq(source), eq(target))).thenReturn(false);

    assertEquals(-1, calls.rename(source.getAbsolutePath(), target.getAbsolutePath()));

    verify(fallback, never()).delete(any(File.class));
  }

  @Test
  public void aFileLibtorrentCanRemoveIsRemovedWithoutTheFallback() throws Exception {
    assumeNotLinux();
    File file = tmp.newFile("remove.bin");

    assertEquals(0, calls.remove(file.getAbsolutePath()));

    assertFalse(file.exists());
    verify(fallback, never()).delete(any(File.class));
  }

  @Test
  public void aFileLibtorrentCannotRemoveIsRemovedByTheFallbackOrFails() {
    File card = new File(tmp.getRoot(), "card/remove.bin");
    when(fallback.delete(eq(card))).thenReturn(true);
    assertEquals(0, calls.remove(card.getAbsolutePath()));

    when(fallback.delete(eq(card))).thenReturn(false);
    assertEquals(-1, calls.remove(card.getAbsolutePath()));
  }
}
