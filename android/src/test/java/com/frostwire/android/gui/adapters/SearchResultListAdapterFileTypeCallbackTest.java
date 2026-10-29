/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.core.Constants;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.search.SearchResult;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * frostwire/frostwire#1384: setFileType's callbacks change views (the file type tabs, the search
 * view). Run on the search performer thread, they raced measure() and crashed with "did not set the
 * measured dimension" on DrawerLayout, DecorView and other ancestors.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchResultListAdapterFileTypeCallbackTest {

  @Before
  public void installPlatform() {
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
  }

  @Test
  public void theFileTypeCallbackRunsOnTheMainThread() throws Exception {
    SearchResultListAdapter adapter =
        new SearchResultListAdapter(ApplicationProvider.getApplicationContext()) {
          @Override
          protected void searchResultClicked(SearchResult sr) {}
        };
    AtomicReference<Thread> callbackThread = new AtomicReference<>();
    AtomicReference<SearchResultListAdapter.FilteredSearchResults> received =
        new AtomicReference<>();

    adapter.setFileType(
        Constants.FILE_TYPE_AUDIO,
        false,
        filtered -> {
          callbackThread.set(Thread.currentThread());
          received.set(filtered);
        });

    // The filtering runs on a real handler thread; the main looper is paused until idled here.
    long deadline = System.currentTimeMillis() + 10_000;
    while (callbackThread.get() == null && System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle();
      Thread.sleep(10);
    }
    assertNotNull("the callback never ran", callbackThread.get());
    assertSame(Looper.getMainLooper().getThread(), callbackThread.get());
    // The callback gets the results of its own call, not whatever the field holds when it runs.
    assertSame(adapter.getFilteredSearchResults(), received.get());
  }
}
