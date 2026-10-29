/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.Constants;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchResult;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * frostwire/frostwire#1384: setFileType's callbacks change views (the file type tabs, the search
 * view). Run on the search performer thread, they raced measure() and crashed with "did not set the
 * measured dimension" on DrawerLayout, DecorView and other ancestors. The visible list and the
 * callbacks are now updated on the main thread, in call order, by the latest call only.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchResultListAdapterFileTypeCallbackTest {

  private SearchResultListAdapter adapter;

  @Before
  public void setUp() {
    // The filtering asserts it runs off the UI thread; MainApplication would set the real platform,
    // and it isn't used here (application = Application.class).
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
    adapter =
        new SearchResultListAdapter(ApplicationProvider.getApplicationContext()) {
          @Override
          protected void searchResultClicked(SearchResult sr) {}
        };
  }

  @Test
  public void theFileTypeCallbackRunsOnTheMainThread() throws Exception {
    AtomicReference<Thread> callbackThread = new AtomicReference<>();

    adapter.setFileType(
        Constants.FILE_TYPE_AUDIO, false, filtered -> callbackThread.set(Thread.currentThread()));

    runMainLooperUntil(() -> callbackThread.get() != null);
    assertSame(Looper.getMainLooper().getThread(), callbackThread.get());
  }

  @Test
  public void theLatestFileTypeWinsWhenCallsOverlap() throws Exception {
    FileSearchResult song = file("song.mp3");
    FileSearchResult movie = file("movie.mp4");
    adapter.addResults(Arrays.asList(song, movie));
    AtomicReference<SearchResultListAdapter.FilteredSearchResults> audio = new AtomicReference<>();
    AtomicReference<SearchResultListAdapter.FilteredSearchResults> video = new AtomicReference<>();

    // Both filters finish before the main thread runs: the user switched tabs quickly.
    adapter.setFileType(Constants.FILE_TYPE_AUDIO, false, audio::set);
    adapter.setFileType(Constants.FILE_TYPE_VIDEOS, false, video::set);

    runMainLooperUntil(() -> video.get() != null);
    assertNull("a superseded call still updated the screen", audio.get());
    assertEquals(List.of(movie), video.get().mediaTypeFiltered);
    assertEquals(1, adapter.getCount());
    assertSame(movie, adapter.getItem(0));
  }

  @Test
  public void appendingAFileTypeShowsEachResultOnce() throws Exception {
    // TellurideCourierCallback shows a yt-dlp page's audio and video formats this way.
    FileSearchResult song = file("song.mp3");
    FileSearchResult movie = file("movie.mp4");
    adapter.addResults(Arrays.asList(song, movie));
    AtomicReference<SearchResultListAdapter.FilteredSearchResults> both = new AtomicReference<>();

    adapter.setFileType(Constants.FILE_TYPE_AUDIO, false, null);
    adapter.setFileType(Constants.FILE_TYPE_VIDEOS, true, both::set);

    runMainLooperUntil(() -> both.get() != null);
    assertEquals(List.of(song, movie), both.get().mediaTypeFiltered);
    assertEquals(2, adapter.getCount());
  }

  /** The filtering runs on a real handler thread; the main looper is paused until idled here. */
  private static void runMainLooperUntil(BooleanSupplier done) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    while (!done.getAsBoolean() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
      shadowOf(Looper.getMainLooper()).idle();
    }
    assertNotNull("timed out", done.getAsBoolean() ? Boolean.TRUE : null);
  }

  private static FileSearchResult file(String name) {
    return (FileSearchResult)
        Proxy.newProxyInstance(
            FileSearchResult.class.getClassLoader(),
            new Class<?>[] {FileSearchResult.class},
            (proxy, method, args) -> {
              switch (method.getName()) {
                case "getFilename":
                case "getDisplayName":
                case "toString":
                  return name;
                case "equals":
                  return proxy == args[0];
                case "hashCode":
                  return System.identityHashCode(proxy);
                default:
                  return method.getReturnType().isPrimitive() ? defaultValue(method) : null;
              }
            });
  }

  private static Object defaultValue(java.lang.reflect.Method method) {
    Class<?> type = method.getReturnType();
    if (type == boolean.class) {
      return false;
    }
    if (type == long.class) {
      return 0L;
    }
    if (type == double.class) {
      return 0d;
    }
    if (type == float.class) {
      return 0f;
    }
    return 0;
  }
}
