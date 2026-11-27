/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import java.nio.charset.StandardCharsets;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The search crawl cache sits on a size-limited disk cache in the app cache folder. Every call must
 * be safe whatever the disk does: the crawler treats the cache as optional.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class DiskCrawlCacheTest {
  private DiskCrawlCache cache;

  @Before
  public void setUp() {
    Application app = ApplicationProvider.getApplicationContext();
    cache = new DiskCrawlCache(app);
    cache.clear();
  }

  @Test
  public void aKeyThatWasNeverStoredIsAMiss() {
    assertNull(cache.get("missing"));
    cache.remove("missing");
  }

  @Test
  public void anEmptyCacheHasNoBytes() {
    assertEquals(0, cache.sizeInBytes());
  }

  @Test
  public void theEntryCountIsReportedAsUnknown() {
    assertEquals(-1, cache.numEntries());
  }

  @Test
  public void storingAndClearingNeverThrowAndLeaveTheCacheUsable() {
    cache.put("k", "value".getBytes(StandardCharsets.UTF_8));
    cache.remove("k");
    cache.clear();
    cache.clear();
    assertEquals(0, cache.sizeInBytes());
    assertNull(cache.get("k"));
  }

  /**
   * put failed at commit for every entry (the cache expected two values), so get was always a miss.
   */
  @Test
  public void aStoredEntryComesBack() {
    byte[] value = "value".getBytes(StandardCharsets.UTF_8);

    cache.put("k", value);

    assertArrayEquals(value, cache.get("k"));
    assertEquals(value.length, cache.sizeInBytes());
  }
}
