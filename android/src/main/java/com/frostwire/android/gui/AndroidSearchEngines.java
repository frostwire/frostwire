/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import com.frostwire.android.core.TellurideCourierSearchPerformer;
import com.frostwire.search.engines.SearchEngine;
import com.frostwire.search.engines.SearchEngineID;
import com.frostwire.search.engines.SearchEngines;
import com.frostwire.search.relay.RelaySearchWiring;
import com.frostwire.util.HttpClientFactory;

/**
 * Android's search engine registry (frostwire/frostwire#1002): the shared {@link SearchEngines},
 * built once from {@link AndroidSearchEngineAdapter}, with the TPB mirror probe started.
 */
public final class AndroidSearchEngines {
  private AndroidSearchEngines() {}

  private static final class Holder {
    static final AndroidSearchEngineAdapter ADAPTER = new AndroidSearchEngineAdapter();
    static final SearchEngines REGISTRY = create();

    private static SearchEngines create() {
      // Telluride runs in the embedded Python runtime and reports like any engine.
      ADAPTER.telluride(TellurideCourierSearchPerformer::new);
      SearchEngines registry = new SearchEngines(ADAPTER);
      registry.startTpbMirrorProbe(
          HttpClientFactory.getInstance(HttpClientFactory.HttpContext.SEARCH));
      return registry;
    }
  }

  public static SearchEngines registry() {
    return Holder.REGISTRY;
  }

  public static AndroidSearchEngineAdapter adapter() {
    return Holder.ADAPTER;
  }

  public static SearchEngine byId(SearchEngineID id) {
    return registry().byId(id);
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
