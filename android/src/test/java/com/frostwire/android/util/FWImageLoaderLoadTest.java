/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.ContentUris;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.ImageView;
import androidx.test.core.app.ApplicationProvider;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/**
 * FWImageLoader over a real Coil loader. Coil's own decoding doesn't run to completion under
 * Robolectric, so these tests check what FWImageLoader itself does: the instance lifecycle, the
 * guards, and the request it prepares on the UI thread (the placeholder goes in first).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class FWImageLoaderLoadTest {

  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private final Context context = ApplicationProvider.getApplicationContext();
  private FWImageLoader loader;
  private ImageView target;

  @Before
  public void setUp() {
    loader = FWImageLoader.getInstance(context);
    target = new ImageView(context);
  }

  @After
  public void tearDown() {
    loader.shutdown();
  }

  /** A filter that pins a Context, which the loader refuses (it would leak the Activity). */
  private static final class LeakyFilter implements FWImageLoader.Filter {
    final Context leaked;

    LeakyFilter(Context leaked) {
      this.leaked = leaked;
    }

    @Override
    public Bitmap filter(Bitmap source) {
      return source;
    }

    @Override
    public String params() {
      return "leaky";
    }
  }

  private static final class LeakyCallback implements FWImageLoader.Callback {
    final Context leaked;

    LeakyCallback(Context leaked) {
      this.leaked = leaked;
    }

    @Override
    public void onSuccess() {}

    @Override
    public void onError(Throwable e) {}
  }

  @Test
  public void aLoadShowsThePlaceholderBeforeTheImageArrives() {
    FWImageLoader.Params p = new FWImageLoader.Params();
    p.placeholderResId = android.R.drawable.ic_menu_gallery;
    p.targetWidth = 4;
    p.targetHeight = 4;
    p.noCache = true;
    loader.load(Uri.fromFile(new File(folder.getRoot(), "a.png")), target, p);
    ShadowLooper.idleMainLooper();
    assertNotNull(target.getDrawable());
  }

  @Test
  public void everyLoadOverloadPreparesItsRequest() {
    Uri uri = Uri.fromFile(new File(folder.getRoot(), "b.png"));
    int placeholder = android.R.drawable.ic_menu_gallery;
    ImageView[] views = new ImageView[6];
    for (int i = 0; i < views.length; i++) {
      views[i] = new ImageView(context);
    }
    loader.load(uri, views[0], 8, 8, placeholder);
    loader.load(placeholder, views[1], 8, 8, placeholder);
    loader.load(placeholder, views[2], placeholder);
    loader.load(uri, views[3], placeholder);
    loader.load(uri, views[4], 8, 8);
    loader.load(placeholder, views[5], 8, 8);
    loader.load(uri, new ImageView(context));
    loader.load(placeholder, new ImageView(context));
    loader.load(uri, uri, new ImageView(context), 8, 8);
    ShadowLooper.idleMainLooper();
    for (int i = 0; i < 4; i++) {
      assertNotNull("placeholder " + i, views[i].getDrawable());
    }
  }

  @Test
  public void aRetryUriMakesTheLoaderWatchTheFirstLoadForFailure() {
    ImageView view = new ImageView(context);
    Uri primary = Uri.fromFile(new File(folder.getRoot(), "primary.png"));
    Uri retry = Uri.fromFile(new File(folder.getRoot(), "retry.png"));
    loader.load(primary, retry, null, view, true, android.R.drawable.ic_menu_gallery);
    ShadowLooper.idleMainLooper();
    assertNotNull(view.getDrawable());
  }

  @Test
  public void aLoadWithNeitherAUriNorAResourceFailsLoudlyInDebugBuilds() {
    FWImageLoader.Params p = new FWImageLoader.Params();
    loader.load(-1, target, p);
    assertThrows(IllegalArgumentException.class, ShadowLooper::idleMainLooper);
  }

  @Test
  public void aFilterThatPinsAContextIsRejected() {
    RuntimeException e =
        assertThrows(
            RuntimeException.class,
            () ->
                loader.load(
                    Uri.parse("file:///x.png"),
                    null,
                    new LeakyFilter(new android.content.ContextWrapper(context)),
                    target,
                    false));
    assertTrue(e.getMessage().contains("context leak"));
  }

  @Test
  public void aCallbackThatPinsAContextIsNeverRun() {
    FWImageLoader.Params p = new FWImageLoader.Params();
    p.placeholderResId = android.R.drawable.ic_menu_gallery;
    p.callback = new LeakyCallback(new android.content.ContextWrapper(context));
    loader.load(Uri.parse("file:///leak.png"), target, p);
    ShadowLooper.idleMainLooper();
    assertNull("the load was abandoned before touching the view", target.getDrawable());
  }

  @Test
  public void aShutDownLoaderOnlyShowsThePlaceholder() {
    loader.shutdown();
    loader.shutdown(); // a second shutdown is a no-op
    FWImageLoader.Params p = new FWImageLoader.Params();
    p.placeholderResId = android.R.drawable.ic_menu_gallery;
    loader.load(Uri.parse("file:///nope.png"), target, p);
    assertNotNull(target.getDrawable());
    ImageView other = new ImageView(context);
    loader.load(Uri.parse("file:///nope.png"), other, new FWImageLoader.Params());
    assertNull(other.getDrawable());
  }

  @Test
  public void getInstanceReplacesAShutDownLoader() {
    loader.shutdown();
    FWImageLoader fresh = FWImageLoader.getInstance(context);
    assertNotSame(loader, fresh);
    assertSame(fresh, FWImageLoader.getInstance(context));
    assertSame(fresh, FWImageLoader.getInstanceIfExists());
    loader = fresh;
  }

  @Test
  public void ensureHealthyInstanceRebuildsAMissingLoader() {
    loader.shutdown();
    assertNull(FWImageLoader.getInstanceIfExists());
    FWImageLoader.ensureHealthyInstance(context);
    assertNotNull(FWImageLoader.getInstanceIfExists());
    loader = FWImageLoader.getInstanceIfExists();
    FWImageLoader.ensureHealthyInstance(context);
    assertSame(loader, FWImageLoader.getInstanceIfExists());
  }

  @Test
  public void backgroundingTheAppKeepsTheLoaderAndClearsMemory() {
    FWImageLoader.onAppBackgrounded();
    assertSame(loader, FWImageLoader.getInstanceIfExists());
    loader.clearMemoryCache();
    loader.clear();
    assertNull(
        "synchronous bitmap reads are not supported", loader.get(Uri.parse("file:///a.png")));
  }

  @Test
  public void aShutDownLoaderHasNoSynchronousBitmapsEither() {
    loader.shutdown();
    assertNull(loader.get(Uri.parse("file:///a.png")));
  }

  @Test
  public void albumAndArtistArtUrisAreBuiltFromTheirIds() {
    Uri album = FWImageLoader.getAlbumArtUri(42);
    assertEquals("content://media/external/audio/albumart/42", album.toString());
    assertEquals(42, ContentUris.parseId(album));
    Uri artist = FWImageLoader.getArtistArtUri("Some Artist");
    assertEquals("Some Artist", artist.getLastPathSegment());
    assertTrue(artist.toString().startsWith("content://media/external/audio/artists/"));
  }
}
