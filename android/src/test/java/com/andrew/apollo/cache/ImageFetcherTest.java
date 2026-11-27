/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.cache;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.widget.ImageView;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.Config;
import com.andrew.apollo.FakeMediaStore;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** The Apollo artwork helper: cache keys, the default artwork and the current-song art. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk = 34, application = Application.class)
public class ImageFetcherTest {

  private Context context;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    ApolloServiceFixture.noService();
    FakeMediaStore.install();
    reset();
  }

  @After
  public void tearDown() throws Exception {
    reset();
  }

  private static void reset() throws Exception {
    Field field = ImageFetcher.class.getDeclaredField("sInstance");
    field.setAccessible(true);
    field.set(null, null);
  }

  @Test
  public void cacheKeysNameTheAlbumAndArtistWhicheverAreKnown() {
    String suffix = Config.ALBUM_ART_SUFFIX;

    assertEquals("LP_Ann_" + suffix, ImageFetcher.generateAlbumCacheKey("LP", "Ann"));
    assertEquals("LP_" + suffix, ImageFetcher.generateAlbumCacheKey("LP", null));
    assertEquals("Ann_" + suffix, ImageFetcher.generateAlbumCacheKey(null, "Ann"));
    assertEquals("unknown_" + suffix, ImageFetcher.generateAlbumCacheKey(null, null));
  }

  @Test
  public void theFetcherIsASingletonWithADefaultArtwork() {
    ImageFetcher fetcher = ImageFetcher.getInstance(context);

    assertSame(fetcher, ImageFetcher.getInstance(context));
    assertNotNull(fetcher.getDefaultArtwork());
  }

  @Test
  public void anAlbumWithoutArtGetsTheDefaultArtwork() {
    FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);
    ImageFetcher fetcher = ImageFetcher.getInstance(context);

    assertSame(fetcher.getDefaultArtwork(), fetcher.getArtwork("LP", 5, "Ann"));
  }

  @Test
  public void withNothingPlayingTheCurrentArtworkIsTheDefaultOne() {
    ImageFetcher fetcher = ImageFetcher.getInstance(context);
    ImageView view = new ImageView(context);

    fetcher.loadCurrentArtwork(view);

    Bitmap shown = ((BitmapDrawable) view.getDrawable()).getBitmap();
    assertSame(fetcher.getDefaultArtwork(), shown);
  }
}
