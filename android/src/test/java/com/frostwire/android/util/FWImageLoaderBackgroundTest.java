/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertNotNull;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import coil3.disk.DiskCache;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * frostwire/frostwire#1389: every time the app went to the background, MainApplication cleared the
 * image disk cache, so artwork was downloaded again after each app switch. Going to the background
 * now frees the memory cache only; the disk cache is bounded by size instead.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 30, application = Application.class)
public class FWImageLoaderBackgroundTest {

  @Before
  @After
  public void resetSingleton() throws Exception {
    Field instance = FWImageLoader.class.getDeclaredField("instance");
    instance.setAccessible(true);
    FWImageLoader loader = (FWImageLoader) instance.get(null);
    if (loader != null) {
      coilLoader(loader).shutdown();
    }
    instance.set(null, null);
  }

  @Test
  public void goingToTheBackgroundKeepsTheDiskCache() throws Exception {
    FWImageLoader loader = FWImageLoader.getInstance(ApplicationProvider.getApplicationContext());
    DiskCache diskCache = coilLoader(loader).getDiskCache();
    assertNotNull("disk cache", diskCache);

    DiskCache.Editor editor = diskCache.openEditor("artwork");
    assertNotNull(editor);
    Files.write(Paths.get(editor.getData().toString()), "png".getBytes(StandardCharsets.US_ASCII));
    Files.write(Paths.get(editor.getMetadata().toString()), new byte[] {1});
    editor.commit();

    FWImageLoader.onAppBackgrounded();

    try (DiskCache.Snapshot snapshot = diskCache.openSnapshot("artwork")) {
      assertNotNull("the disk cache was cleared when the app went to the background", snapshot);
    }
  }

  private static coil3.ImageLoader coilLoader(FWImageLoader loader) throws Exception {
    Field field = FWImageLoader.class.getDeclaredField("coilImageLoader");
    field.setAccessible(true);
    return (coil3.ImageLoader) field.get(loader);
  }
}
