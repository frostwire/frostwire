/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.adapters;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

import android.app.Application;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.R;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.relay.DistributedSearchPerformer;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RemoteKarmaChainFetcher;
import java.lang.reflect.Proxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * A peer can still inflate its own karma (frostwire/frostwire#1417), so the search list doesn't
 * show it: a distributed result keeps the seeds slot empty, as before #1418, even when its peer has
 * karma in the wired Peer Directory.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SearchResultListAdapterHidesKarmaTest {

  private static final String INFO_HASH = "0123456789abcdef0123456789abcdef01234567";

  private final byte[] peer = new byte[32];
  private PeerDirectory previousDirectory;
  private Context context;
  private SearchResultListAdapter adapter;

  @Before
  public void setUp() {
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
        };
    peer[31] = 5;
    PeerDirectory directory =
        new PeerDirectory(
            new PeerKarmaCache(new RemoteKarmaChainFetcher(peerPub -> null)) {
              @Override
              public long getCachedKarma(byte[] peerPub) {
                return 2;
              }
            });
    directory.upsert(peer, "peer.example", 6881);
    previousDirectory = SearchEngine.DISTRIBUTED_WIRING.peerDirectory();
    SearchEngine.DISTRIBUTED_WIRING.peerDirectory(directory);
  }

  @After
  public void tearDown() {
    SearchEngine.DISTRIBUTED_WIRING.peerDirectory(previousDirectory);
  }

  @Test
  public void aDistributedRowShowsNoKarma() throws Exception {
    View row = View.inflate(context, R.layout.view_bittorrent_search_result_list_item, null);
    CompositeFileSearchResult result = result();

    // Give a background karma load, if any, time to land, rebinding the row as a redraw would.
    long deadline = System.currentTimeMillis() + 2_000;
    do {
      adapter.populateView(row, result);
      assertEquals("", seeds(row));
      Thread.sleep(50);
      shadowOf(Looper.getMainLooper()).idle();
    } while (System.currentTimeMillis() < deadline);
  }

  private static String seeds(View row) {
    return ((TextView) row.findViewById(R.id.view_bittorrent_search_result_list_item_text_seeds))
        .getText()
        .toString();
  }

  private CompositeFileSearchResult result() {
    String magnet = "magnet:?xt=urn:btih:" + INFO_HASH + "&dn=ubuntu";
    return CompositeFileSearchResult.builder()
        .displayName("ubuntu")
        .filename("ubuntu.torrent")
        .size(1024)
        .detailsUrl(magnet)
        .source(DistributedSearchPerformer.SOURCE_NAME)
        .creationTime(1_700_000_000L)
        .torrent(magnet, INFO_HASH, 0, magnet)
        .distributedSourcePeerPub(peer)
        .build();
  }
}
