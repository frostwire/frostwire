/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PackagedJarExtractionTest {
  @TempDir Path extracted;

  @Test
  void applicationJarUnpacksWithoutCaseInsensitivePathCollisions() throws Exception {
    verify("frostwire.jar");
  }

  @Test
  void daemonJarUnpacksWithoutCaseInsensitivePathCollisions() throws Exception {
    verify("icebridge.jar");
  }

  private void verify(String filename) throws Exception {
    Path jar = Path.of(System.getProperty("user.dir"), "build", "libs", filename);
    assertTrue(Files.isRegularFile(jar), "test must run against the built artifact");
    try (ZipFile archive = new ZipFile(jar.toFile())) {
      Map<String, String> files = new HashMap<>();
      for (ZipEntry entry : archive.stream().toList()) {
        if (!entry.isDirectory()) {
          String previous =
              files.putIfAbsent(entry.getName().toLowerCase(Locale.ROOT), entry.getName());
          assertNull(
              previous, "case-insensitive file alias: " + previous + " / " + entry.getName());
        }
      }
      for (ZipEntry entry : archive.stream().toList()) {
        String name = entry.getName();
        for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
          String parent = name.substring(0, slash).toLowerCase(Locale.ROOT);
          assertFalse(
              files.containsKey(parent),
              "file/directory collision: " + files.get(parent) + " / " + name);
        }
        Path target = extracted.resolve(name);
        if (entry.isDirectory()) {
          Files.createDirectories(target);
        } else {
          Files.createDirectories(target.getParent());
          try (var content = archive.getInputStream(entry)) {
            Files.copy(content, target);
          }
        }
      }
      // Relocate the conflicting root license instead of dropping legal resources.
      assertTrue(Files.size(extracted.resolve("META-INF/licenses/LICENSE")) > 0);
      assertTrue(Files.size(extracted.resolve("META-INF/license/LICENSE.slf4j.txt")) > 0);
      assertTrue(Files.isRegularFile(extracted.resolve("META-INF/MANIFEST.MF")));
      assertTrue(
          archive.stream()
              .anyMatch(
                  entry ->
                      entry.getName().endsWith(".dylib")
                          || entry.getName().endsWith(".so")
                          || entry.getName().endsWith(".dll")),
          "packaging must retain native libraries for the build platform");
    }
  }
}
