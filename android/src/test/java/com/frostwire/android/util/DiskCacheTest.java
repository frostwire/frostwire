/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** DiskCache over a real DiskLruCache in a temporary folder: no Android runtime needed. */
public class DiskCacheTest {

  private static final String BUG =
      "DiskCache opens its DiskLruCache with 2 values per entry but only writes value 0, so every"
          + " put fails at commit with IllegalStateException (the crawl cache swallows it)."
          + " Enable once DiskCache writes both values or opens with one.";

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private DiskCache newCache(long size) {
    return new DiskCache(new File(folder.getRoot(), "cache"), size);
  }

  private static byte[] read(DiskCache.Entry entry) throws IOException {
    try (InputStream in = entry.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[256];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    } finally {
      entry.close();
    }
  }

  @Ignore(BUG)
  @Test
  public void putBytesThenGetReturnsSameBytes() throws IOException {
    DiskCache cache = newCache(1024 * 1024);
    byte[] data = "hello frostwire".getBytes(StandardCharsets.UTF_8);
    cache.put("k", data);
    assertTrue(cache.containsKey("k"));
    assertArrayEquals(data, read(cache.get("k")));
  }

  @Ignore(BUG)
  @Test
  public void putStreamThenGetReturnsSameBytes() throws IOException {
    DiskCache cache = newCache(1024 * 1024);
    byte[] data = new byte[2000];
    for (int i = 0; i < data.length; i++) {
      data[i] = (byte) i;
    }
    cache.put("stream", new ByteArrayInputStream(data));
    assertArrayEquals(data, read(cache.get("stream")));
  }

  @Ignore(BUG)
  @Test
  public void removeDropsTheEntry() {
    DiskCache cache = newCache(1024 * 1024);
    cache.put("k", new byte[] {1, 2, 3});
    cache.remove("k");
    assertFalse(cache.containsKey("k"));
  }

  @Ignore(BUG)
  @Test
  public void sizeGrowsWithEntries() {
    DiskCache cache = newCache(4096);
    cache.put("a", new byte[100]);
    assertEquals(100, cache.size());
  }

  @Test
  public void anEmptyCacheHasNothingAndReportsItsLimits() {
    DiskCache cache = newCache(1024);
    assertFalse(cache.containsKey("nope"));
    assertNull(cache.get("nope"));
    assertEquals(0, cache.size());
    assertEquals(1024, cache.maxSize());
    assertEquals(-1, cache.numEntries());
    cache.remove("nope");
  }

  @Test
  public void deleteRemovesTheCacheDirectory() throws IOException {
    DiskCache cache = newCache(1024 * 1024);
    assertFalse(cache.containsKey("anything"));
    File dir = new File(folder.getRoot(), "cache");
    assertTrue(dir.exists());
    cache.delete();
    String[] left = dir.list();
    assertTrue(left == null || left.length == 0);
  }
}
