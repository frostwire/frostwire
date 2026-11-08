/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import com.frostwire.BuildConfig;
import com.frostwire.search.engines.SearchEngine;
import com.frostwire.search.engines.SearchEngineID;
import com.frostwire.search.engines.SearchEngines;
import com.frostwire.search.relay.RelaySearchWiring;
import com.frostwire.util.HttpClientFactory;
import com.limegroup.gnutella.settings.SearchEnginesSettings;
import java.util.List;

/**
 * Desktop's search engine registry (frostwire/frostwire#1002): the shared {@link SearchEngines},
 * built once from {@link DesktopSearchEngineAdapter}, with the TPB mirror probe started.
 */
public final class DesktopSearchEngines {
  private DesktopSearchEngines() {}

  private static final class Holder {
    static final DesktopSearchEngineAdapter ADAPTER = new DesktopSearchEngineAdapter();
    static final SearchEngines REGISTRY = create();

    private static SearchEngines create() {
      if (!BuildConfig.DEBUG) {
        // The Local test engine is never offered in release builds; don't leave it switched on.
        SearchEnginesSettings.LOCAL_SEARCH_ENABLED.setValue(false);
      }
      SearchEngines registry = new SearchEngines(ADAPTER);
      registry.startTpbMirrorProbe(
          HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH));
      return registry;
    }
  }

  public static SearchEngines registry() {
    return Holder.REGISTRY;
  }

  public static DesktopSearchEngineAdapter adapter() {
    return Holder.ADAPTER;
  }

  /** The engines desktop offers, in listing order. */
  public static List<SearchEngine> all() {
    return registry().all();
  }

  public static SearchEngine byId(SearchEngineID id) {
    return registry().byId(id);
  }

  public static SearchEngine byName(String name) {
    return registry().byName(name);
  }

  /** The Distributed engine's relay wiring (index, peers, identity, transport, karma chain). */
  public static RelaySearchWiring distributed() {
    return registry().distributedWiring();
  }

  /** The Local engine's relay wiring. */
  public static RelaySearchWiring local() {
    return registry().localWiring();
  }
}
