/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.engines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.frostclick.UserAgent;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The search engines both clients share (frostwire/frostwire#1002), driven by a fake client
 * adapter. No test here touches the network.
 */
class SearchEnginesTest {

  /** A client whose settings live in a map; every engine is offered unless told otherwise. */
  static final class FakeAdapter implements SearchEngineAdapter {
    final Map<SearchEngineID, Boolean> settings = new EnumMap<>(SearchEngineID.class);
    final Set<SearchEngineID> notOffered = EnumSet.noneOf(SearchEngineID.class);
    TellurideSearchFactory telluride;

    @Override
    public boolean isEnabled(SearchEngineID id) {
      return settings.getOrDefault(id, true);
    }

    @Override
    public void setEnabled(SearchEngineID id, boolean enabled) {
      settings.put(id, enabled);
    }

    @Override
    public boolean isOffered(SearchEngineID id) {
      return !notOffered.contains(id);
    }

    @Override
    public UserAgent userAgent() {
      return new UserAgent("test-os", "7.1.4", "337");
    }

    @Override
    public String soundCloudClientId() {
      return "client";
    }

    @Override
    public String soundCloudAppVersion() {
      return "1";
    }

    @Override
    public TellurideSearchFactory telluride() {
      return telluride;
    }
  }

  @Test
  void bothClientsListTheSameEnginesInTheSameOrder() {
    List<SearchEngineID> ids =
        new SearchEngines(new FakeAdapter()).all().stream().map(SearchEngine::getId).toList();

    assertEquals(
        List.of(
            SearchEngineID.LOCAL_ID,
            SearchEngineID.DISTRIBUTED_ID,
            SearchEngineID.YT_ID,
            SearchEngineID.INTERNET_ARCHIVE_ID,
            SearchEngineID.BITSEARCH_ID,
            SearchEngineID.KNABEN_ID,
            SearchEngineID.MAGNETDL_ID,
            SearchEngineID.NYAA_ID,
            SearchEngineID.ONE337X_ID,
            SearchEngineID.TPB_ID,
            SearchEngineID.TORRENTZ2_ID,
            SearchEngineID.TORRENTSCSV_ID,
            SearchEngineID.SOUNDCLOUD_ID,
            SearchEngineID.FROSTCLICK_ID),
        ids);
  }

  @Test
  void anEngineTheClientDoesNotOfferIsNeitherListedNorEnabled() {
    FakeAdapter adapter = new FakeAdapter();
    adapter.notOffered.add(SearchEngineID.YT_ID);
    SearchEngines engines = new SearchEngines(adapter);

    assertTrue(engines.all().stream().noneMatch(e -> e.getId() == SearchEngineID.YT_ID));
    assertFalse(engines.byId(SearchEngineID.YT_ID).isEnabled(), "a hidden engine stays off");
    assertTrue(engines.enabled().stream().noneMatch(e -> e.getId() == SearchEngineID.YT_ID));
  }

  @Test
  void enabledFollowsTheClientsSettingAndReadiness() {
    FakeAdapter adapter = new FakeAdapter();
    SearchEngines engines = new SearchEngines(adapter);
    SearchEngine nyaa = engines.byId(SearchEngineID.NYAA_ID);

    assertTrue(engines.enabled().contains(nyaa));
    nyaa.setEnabled(false);
    assertFalse(adapter.settings.get(SearchEngineID.NYAA_ID), "the client's setting is written");
    assertFalse(engines.enabled().contains(nyaa));

    SearchEngine tpb = engines.byId(SearchEngineID.TPB_ID);
    assertTrue(tpb.isEnabled());
    assertFalse(engines.enabled().contains(tpb), "TPB isn't searched before a mirror is found");
    engines.tpbDomain("tpb.example");
    assertTrue(engines.enabled().contains(tpb));
  }

  @Test
  void anEngineThatIsNotReadyGivesNoPerformerInsteadOfThrowing() {
    SearchEngines engines = new SearchEngines(new FakeAdapter());

    assertNull(engines.byId(SearchEngineID.TPB_ID).getPerformer(1, "ubuntu"));
    assertNull(engines.byId(SearchEngineID.LOCAL_ID).getPerformer(1, "ubuntu"));
    assertNull(engines.byId(SearchEngineID.DISTRIBUTED_ID).getPerformer(1, "ubuntu"));
    assertNull(engines.telluride().getPerformer(1, "https://example.com/v", false));
  }

  @Test
  void webEnginesMakeAPerformerForTheSearch() {
    SearchEngines engines = new SearchEngines(new FakeAdapter());
    engines.tpbDomain("tpb.example");

    for (SearchEngine engine : engines.all()) {
      if (engine.getId() == SearchEngineID.LOCAL_ID
          || engine.getId() == SearchEngineID.DISTRIBUTED_ID) {
        continue;
      }
      ISearchPerformer performer = engine.getPerformer(7, "ubuntu");
      assertNotNull(performer, engine.getName());
      assertEquals(7, performer.getToken(), engine.getName());
    }
  }

  @Test
  void telluridePerformersComeFromTheClientsFactory() {
    FakeAdapter adapter = new FakeAdapter();
    SearchEngines engines = new SearchEngines(adapter);
    ISearchPerformer[] made = new ISearchPerformer[1];
    boolean[] playlist = new boolean[1];
    adapter.telluride =
        (token, url, playlistMode) -> {
          playlist[0] = playlistMode;
          made[0] = new SearchEnginesTestPerformer(token);
          return made[0];
        };

    ISearchPerformer performer =
        engines.telluride().getPerformer(3, "https://example.com/list", true);

    assertSame(made[0], performer);
    assertTrue(playlist[0]);
    assertFalse(
        engines.all().contains(engines.telluride()), "Telluride opens URLs; it isn't listed");
  }

  @Test
  void enginesAreFoundByIdAndByTheirResultsSourceName() {
    SearchEngines engines = new SearchEngines(new FakeAdapter());

    assertEquals(SearchEngineID.TPB_ID, engines.byName("TPB").getId());
    assertEquals(SearchEngineID.TPB_ID, engines.byName("tpb").getId());
    assertEquals(SearchEngineID.TPB_ID, engines.byName("TPB (mirror)").getId());
    assertEquals(SearchEngineID.TELLURIDE_ID, engines.byName("Cloud: youtube").getId());
    assertNull(engines.byName("Nope"));
    for (SearchEngineID id : SearchEngineID.values()) {
      assertNotNull(engines.byId(id), id.name());
    }
  }
}
