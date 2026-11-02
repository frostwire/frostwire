/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The album artwork decoder that replaced Apollo's image cache for the notification and shortcuts
 * (GitHub #1461).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class AlbumArtworkTest {

  @Test
  public void bigImagesAreScaledDownToAboutTheTarget() {
    assertEquals(4, AlbumArtwork.inSampleSize(2048, 2048, 512));
    assertEquals(2, AlbumArtwork.inSampleSize(1024, 1500, 512));
    assertEquals(1, AlbumArtwork.inSampleSize(1000, 1000, 512));
  }

  @Test
  public void noAlbumMeansNoArtwork() {
    assertNull(AlbumArtwork.decode(ApplicationProvider.getApplicationContext(), -1));
    assertNull(AlbumArtwork.decode(null, 5));
  }
}
