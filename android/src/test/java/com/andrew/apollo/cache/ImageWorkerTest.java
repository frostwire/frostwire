/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo.cache;

import static com.andrew.apollo.FakeMediaStore.cursor;
import static com.andrew.apollo.FakeMediaStore.row;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.widget.ImageView;
import androidx.test.core.app.ApplicationProvider;
import com.andrew.apollo.ApolloServiceFixture;
import com.andrew.apollo.FakeMediaStore;
import com.frostwire.android.util.FWImageLoader;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Artwork requests: the Apollo helper decides which image URI to ask the image loader for. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ImageWorkerTest {

  private Context context;
  private FWImageLoader loader;
  private ImageFetcher fetcher;
  private ImageView view;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    ApolloServiceFixture.noService();
    FakeMediaStore.install();
    loader = mock(FWImageLoader.class);
    setStatic(FWImageLoader.class, "instance", loader);
    setStatic(ImageFetcher.class, "sInstance", null);
    fetcher = ImageFetcher.getInstance(context);
    view = new ImageView(context);
  }

  @After
  public void tearDown() throws Exception {
    setStatic(FWImageLoader.class, "instance", null);
    setStatic(ImageFetcher.class, "sInstance", null);
  }

  private static void setStatic(Class<?> c, String name, Object value) throws Exception {
    Field field = c.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, value);
  }

  @Test
  public void anAlbumImageIsRequestedByItsAlbumArtUriWithTheDefaultAsPlaceholder() {
    fetcher.loadAlbumImage("Ann", "LP", 9, view);

    verify(loader).load(eq(FWImageLoader.getAlbumArtUri(9)), eq(view), anyInt(), isNull());
  }

  @Test
  public void anAlbumImageFromAFileCarriesTheFilePathAsFallback() {
    fetcher.loadAlbumImage("Ann", "LP", -1, view, "/music/a.mp3");

    verify(loader)
        .load(eq(FWImageLoader.getAlbumArtUri(-1)), eq(view), anyInt(), eq("/music/a.mp3"));
  }

  @Test
  public void anArtistImageIsRequestedByTheArtistsName() {
    fetcher.loadArtistImage("Ann", view);

    Uri expected = FWImageLoader.getArtistArtUri("Ann");
    verify(loader).load(eq(expected), eq(view), anyInt());
  }

  @Test
  public void noViewMeansNoRequest() {
    fetcher.loadAlbumImage("Ann", "LP", 9, null);
    fetcher.loadArtistImage("Ann", null);

    verify(loader, never()).load(any(Uri.class), any(ImageView.class), anyInt());
    verify(loader, never()).load(any(Uri.class), any(ImageView.class), anyInt(), any());
  }

  @Test
  public void theCurrentSongsArtworkIsRequestedByItsAlbumId() throws Exception {
    ApolloServiceFixture fixture = ApolloServiceFixture.start();
    try {
      FakeMediaStore.onQuery =
          (u, p, s, a, o) ->
              u.getPath().contains("/albums")
                  ? cursor(p)
                  : cursor(
                      p,
                      row(
                          "_id",
                          5L,
                          "title",
                          "Hey",
                          "artist",
                          "Ann",
                          "album",
                          "LP",
                          "album_id",
                          9L,
                          "artist_id",
                          3L,
                          "_data",
                          "/music/a.mp3"));
      fixture.service.handleCommandIntent(new Intent());
      shadowOf(Looper.getMainLooper()).idle();
      fixture.service.open(new long[] {5}, 0);
      fixture.service.pause();
      shadowOf(Looper.getMainLooper()).idle();

      fetcher.loadCurrentArtwork(view);

      verify(loader)
          .load(eq(FWImageLoader.getAlbumArtUri(9)), eq(view), anyInt(), eq("/music/a.mp3"));
    } finally {
      fixture.stop();
    }
  }

  @Test
  public void aFileMediaStoreDoesNotKnowIsShownFromTheFileItself() throws Exception {
    ApolloServiceFixture fixture = ApolloServiceFixture.start();
    try {
      FakeMediaStore.onQuery = (u, p, s, a, o) -> cursor(p);
      fixture.service.handleCommandIntent(new Intent());
      shadowOf(Looper.getMainLooper()).idle();
      fixture.service.playTransientFile("/storage/emulated/0/Music/a.mp3");
      fixture.service.pause();
      shadowOf(Looper.getMainLooper()).idle();

      fetcher.loadCurrentArtwork(view);

      verify(loader)
          .load(
              eq(FWImageLoader.getAlbumArtUri(-1)),
              eq(view),
              anyInt(),
              eq("/storage/emulated/0/Music/a.mp3"));
    } finally {
      fixture.stop();
    }
  }
}
