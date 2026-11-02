/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.relay.DisplayKarma;
import com.frostwire.search.relay.DistributedSearchPerformer;
import com.frostwire.search.relay.KarmaDisplayCache;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * frostwire/frostwire#1418: a distributed search result shows its peer's karma ({@link
 * PeerDirectory#displayKarma}) where torrents show seeds. Binding a row never walks the trust graph
 * on the main thread: the karma loads on a background thread (the MISC handler thread in the app)
 * and the list is redrawn after.
 *
 * <p>The rows read a cache built like {@link com.frostwire.android.gui.RelaySearchWiring}'s but
 * loading on a thread of the test's own: other tests' MainApplication start-up parks the shared
 * MISC thread for 30 s each in this JVM (Engine.getPythonInstance), which made the timing flaky.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchResultListAdapterKarmaTest {

  private static final String INFO_HASH = "0123456789abcdef0123456789abcdef01234567";

  private final RecordingKarmaCache karmaCache = new RecordingKarmaCache();
  private final PeerDirectory directory = new PeerDirectory(karmaCache);
  private final byte[] peer = new byte[32];
  private final AtomicInteger redraws = new AtomicInteger();
  private final Runnable redraw = redraws::incrementAndGet;
  private final ExecutorService loader =
      Executors.newSingleThreadExecutor(r -> new Thread(r, "karma-test-loader"));
  private final KarmaDisplayCache karmaDisplay =
      new KarmaDisplayCache(() -> directory, loader, SystemUtils::postToUIThread);
  private Context context;
  private SearchResultListAdapter adapter;

  @Before
  public void setUp() {
    // MainApplication would set the real platform; it isn't used here (application =
    // Application.class).
    Platforms.set(
        (Platform)
            Proxy.newProxyInstance(
                Platform.class.getClassLoader(),
                new Class<?>[] {Platform.class},
                (proxy, method, args) ->
                    "isUIThread".equals(method.getName())
                        ? Looper.myLooper() == Looper.getMainLooper()
                        : null));
    context = ApplicationProvider.getApplicationContext();
    adapter =
        new SearchResultListAdapter(context) {
          @Override
          protected void searchResultClicked(SearchResult sr) {}

          @Override
          protected KarmaDisplayCache karmaDisplay() {
            return karmaDisplay;
          }
        };
    peer[31] = 5;
    directory.upsert(peer, "peer.example", 6881);
    karmaCache.score = 2;
    karmaDisplay.addListener(redraw);
  }

  @After
  public void tearDown() {
    loader.shutdownNow();
  }

  @Test
  public void rowsReadTheDistributedSearchWiringsCache() {
    SearchResultListAdapter app =
        new SearchResultListAdapter(context) {
          @Override
          protected void searchResultClicked(SearchResult sr) {}
        };
    assertSame(SearchEngine.DISTRIBUTED_WIRING.karmaDisplay(), app.karmaDisplay());
  }

  @Test
  public void aDistributedRowShowsItsPeersKarmaLoadedOffTheMainThread() throws Exception {
    View row = View.inflate(context, R.layout.view_bittorrent_search_result_list_item, null);
    CompositeFileSearchResult result = result(DistributedSearchPerformer.SOURCE_NAME);

    adapter.populateView(row, result);

    assertEquals("the main thread must not wait for the karma", "", seeds(row));
    runMainLooperUntil(() -> redraws.get() > 0);
    adapter.populateView(row, result);
    assertEquals(
        context.getString(
            R.string.search_result_karma, DisplayKarma.of(directory.displayKarma(peer)).text()),
        seeds(row));
    assertEquals(context.getString(R.string.search_result_karma, "3.00"), seeds(row));
    synchronized (karmaCache.onMain) {
      assertFalse(karmaCache.onMain.isEmpty());
      // The test's own displayKarma call above ran on the main thread; the adapter's did not.
      assertFalse("karma read on the main thread", karmaCache.onMain.get(0));
    }
  }

  @Test
  public void otherResultsShowNoKarma() {
    View row = View.inflate(context, R.layout.view_bittorrent_search_result_list_item, null);

    adapter.populateView(row, result("1337x"));

    assertEquals("", seeds(row));
    assertTrue(karmaCache.onMain.isEmpty());
  }

  private static String seeds(View row) {
    return ((TextView) row.findViewById(R.id.view_bittorrent_search_result_list_item_text_seeds))
        .getText()
        .toString();
  }

  private CompositeFileSearchResult result(String source) {
    String magnet = "magnet:?xt=urn:btih:" + INFO_HASH + "&dn=ubuntu";
    return CompositeFileSearchResult.builder()
        .displayName("ubuntu")
        .filename("ubuntu.torrent")
        .size(1024)
        .detailsUrl(magnet)
        .source(source)
        .creationTime(1_700_000_000L)
        .torrent(magnet, INFO_HASH, 0, magnet)
        .distributedSourcePeerPub(peer)
        .build();
  }

  /** The karma loads on a real thread; the main looper is paused until idled here. */
  private static void runMainLooperUntil(BooleanSupplier done) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 10_000;
    while (!done.getAsBoolean() && System.currentTimeMillis() < deadline) {
      Thread.sleep(10);
      shadowOf(Looper.getMainLooper()).idle();
    }
    assertTrue("timed out", done.getAsBoolean());
  }

  /** Records whether each karma read through the directory ran on the main thread. */
  private static final class RecordingKarmaCache extends PeerKarmaCache {
    final List<Boolean> onMain = new ArrayList<>();
    volatile long score;

    RecordingKarmaCache() {
      super(new RemoteKarmaChainFetcher(peerPub -> null));
    }

    @Override
    public long getCachedKarma(byte[] peerPub) {
      synchronized (onMain) {
        onMain.add(Looper.myLooper() == Looper.getMainLooper());
      }
      return score;
    }
  }
}
