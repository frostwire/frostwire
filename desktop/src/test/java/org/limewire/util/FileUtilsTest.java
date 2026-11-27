/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package org.limewire.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class FileUtilsTest {

  private static File write(File dir, String name, String content) throws IOException {
    File f = new File(dir, name);
    assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
    Files.writeString(f.toPath(), content);
    return f;
  }

  @Test
  void ancestorAndParentPathChecks(@TempDir File tmp) throws IOException {
    File child = new File(new File(tmp, "a"), "b.txt");
    assertTrue(FileUtils.isAncestor(tmp, child));
    assertTrue(FileUtils.isAncestor(child, child));
    assertFalse(FileUtils.isAncestor(child, tmp));
    assertFalse(FileUtils.isAncestor(new File(tmp, "other"), child));

    assertTrue(FileUtils.isReallyInParentPath(tmp, child));
    assertFalse(FileUtils.isReallyInParentPath(new File(tmp, "a/b"), new File(tmp, "x.txt")));
    // ".." segments are resolved before comparing
    assertFalse(
        FileUtils.isReallyInParentPath(new File(tmp, "a"), new File(tmp, "a/../outside.txt")));
    assertEquals(tmp.getCanonicalPath(), FileUtils.getCanonicalPath(tmp));
    assertEquals(tmp.getCanonicalFile(), FileUtils.getCanonicalFile(tmp));
  }

  @Test
  void recursiveListingWithAndWithoutFilter(@TempDir File tmp) throws IOException {
    write(tmp, "a.mp3", "1");
    write(tmp, "sub/b.MP3", "2");
    write(tmp, "sub/deep/c.txt", "3");
    write(tmp, "noext", "4");
    assertEquals(4, FileUtils.getFilesRecursive(tmp, null).length);
    TreeSet<String> names = new TreeSet<>();
    for (File f : FileUtils.getFilesRecursive(tmp, new String[] {"mp3"})) names.add(f.getName());
    assertEquals(new TreeSet<>(Arrays.asList("a.mp3", "b.MP3")), names);
    assertEquals(0, FileUtils.getFilesRecursive(tmp, new String[] {"zzz"}).length);
    assertEquals(0, FileUtils.getFilesRecursive(new File(tmp, "missing"), null).length);
    assertEquals(0, FileUtils.getFilesRecursive(new File(tmp, "a.mp3"), null).length);
  }

  @Test
  void deleteRemovesTreesAndReportsMissing(@TempDir File tmp) throws IOException {
    File root = new File(tmp, "root");
    write(root, "x/y/z.txt", "z");
    write(root, "top.txt", "t");
    assertTrue(FileUtils.delete(root, false));
    assertFalse(root.exists());
    assertFalse(FileUtils.delete(root, false));
    File single = write(tmp, "single.txt", "s");
    assertTrue(FileUtils.delete(single, false));
    assertFalse(single.exists());
  }

  @Test
  void copyDuplicatesContent(@TempDir File tmp) throws IOException {
    byte[] data = new byte[5000];
    for (int i = 0; i < data.length; i++) data[i] = (byte) (i * 31);
    File src = new File(tmp, "src.bin");
    Files.write(src.toPath(), data);
    File dst = new File(tmp, "dst.bin");
    assertTrue(FileUtils.copy(src, dst));
    assertArrayEquals(data, Files.readAllBytes(dst.toPath()));
    File empty = write(tmp, "empty", "");
    assertTrue(FileUtils.copy(empty, new File(tmp, "empty2")));
    assertEquals(0, new File(tmp, "empty2").length());
  }

  @Test
  void listFilesReturnsAbsoluteChildren(@TempDir File tmp) throws IOException {
    write(tmp, "one.txt", "1");
    write(tmp, "dir/two.txt", "2");
    File[] files = FileUtils.listFiles(tmp);
    assertEquals(2, files.length);
    for (File f : files) assertTrue(f.isAbsolute());
    assertEquals(0, FileUtils.listFiles(new File(tmp, "missing")).length);
  }

  @Test
  void extensionChecksAreCaseInsensitive() {
    assertTrue(FileUtils.hasExtension("song.MP3", "mp3"));
    assertTrue(FileUtils.hasExtension("song.mp3", "ogg", "MP3"));
    assertFalse(FileUtils.hasExtension("song.mp3", "ogg", "wav"));
    assertFalse(FileUtils.hasExtension("song", "mp3"));
    assertFalse(FileUtils.hasExtension("song.mp3"));
  }

  @Test
  void buildFileAvoidsOverwriting(@TempDir File tmp) throws IOException {
    assertEquals(new File(tmp, "file.txt"), FileUtils.buildFile(tmp, "file.txt"));
    write(tmp, "file.txt", "a");
    assertEquals(new File(tmp, "file (1).txt"), FileUtils.buildFile(tmp, "file.txt"));
    write(tmp, "file (1).txt", "b");
    assertEquals(new File(tmp, "file (2).txt"), FileUtils.buildFile(tmp, "file.txt"));
  }

  @Test
  void jarLookupFromClasspath() {
    assertThrows(IllegalArgumentException.class, () -> FileUtils.getJarFromClasspath(null, "x"));
    ClassLoader cl = FileUtilsTest.class.getClassLoader();
    assertNull(FileUtils.getJarFromClasspath(cl, "definitely/not/here.marker"));
    // test classes are loaded from a directory, not a jar
    assertNull(FileUtils.getJarFromClasspath(cl, "org/limewire/util/FileUtilsTest.class"));
  }
}
