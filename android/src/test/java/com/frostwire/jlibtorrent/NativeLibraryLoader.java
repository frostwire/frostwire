/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.jlibtorrent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Unit-test replacement of jlibtorrent's own loader (this class file shadows the one in the desktop
 * jlibtorrent jar because test classes come first on the test class path).
 *
 * <p>A JNI library can only be loaded by one class loader, and Robolectric creates one class loader
 * per SDK configuration, each of which defines its own copy of the libtorrent classes. The original
 * loader always extracts the library to the same cache file, so the second sandbox fails with
 * "already loaded in another classloader". This one gives every loader its own copy of the library
 * file, which the JVM treats as a different library.
 */
public final class NativeLibraryLoader {
  private static String loadedLibrary = "";

  private NativeLibraryLoader() {}

  public static String loadedLibrary() {
    return loadedLibrary;
  }

  public static synchronized String load(String version, ClassLoader loader) {
    if (!loadedLibrary.isEmpty()) {
      return loadedLibrary;
    }
    try {
      // The platform description (resource path inside the native jar, file name) is the one the
      // real
      // loader uses; only the extraction differs.
      Class<?> platformClass =
          Class.forName("com.frostwire.jlibtorrent.NativeLibraryLoader$Platform");
      Method current = platformClass.getDeclaredMethod("current");
      current.setAccessible(true);
      Object platform = current.invoke(null);
      Method resourcePath = platformClass.getDeclaredMethod("resourcePath");
      resourcePath.setAccessible(true);
      Method fileName = platformClass.getDeclaredMethod("fileName");
      fileName.setAccessible(true);
      String resource = (String) resourcePath.invoke(platform);
      URL url = loader.getResource(resource);
      if (url == null) {
        throw new UnsatisfiedLinkError("no " + resource + " on the test class path");
      }
      Path copy = Files.createTempFile("jlibtorrent-unit-test-", "-" + fileName.invoke(platform));
      copy.toFile().deleteOnExit();
      try (InputStream in = url.openStream()) {
        Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
      }
      System.load(copy.toAbsolutePath().toString());
      loadedLibrary = copy.toString();
      return loadedLibrary;
    } catch (IOException | ReflectiveOperationException e) {
      throw new UnsatisfiedLinkError("cannot load jlibtorrent for the unit tests: " + e);
    }
  }

  public static void checkNativeVersion(String javaVersion, String nativeVersion) {
    if (!libtorrentPart(javaVersion).equals(libtorrentPart(nativeVersion))) {
      throw new LinkageError(
          "jlibtorrent Java "
              + javaVersion
              + " does not match native "
              + nativeVersion
              + " ("
              + loadedLibrary
              + ")");
    }
  }

  static String libtorrentPart(String version) {
    String[] parts = version.split("\\.");
    return parts.length < 3 ? version : parts[0] + "." + parts[1] + "." + parts[2];
  }
}
