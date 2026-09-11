/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class SearchIceBridgeStatusHintTest {

  @Test
  public void searchProgressShowsIceBridgeHintWhileSearching() throws Exception {
    String layout = projectFile("res/layout/view_search_progress.xml");
    assertTrue(layout.contains("view_search_progress_text_icebridge"));
    assertTrue(layout.contains("@string/search_icebridge_starting"));

    String view = projectFile("src/main/java/com/frostwire/android/gui/views/SearchProgressView.java");
    assertTrue(view.contains("setIceBridgeHint("));
    assertTrue(view.contains("textIceBridge"));

    String fragment = projectFile("src/main/java/com/frostwire/android/gui/fragments/SearchFragment.java");
    assertTrue(fragment.contains("refreshIceBridgeHint()"));
    assertTrue(fragment.contains("search_icebridge_starting"));
    assertTrue(fragment.contains("search_icebridge_not_running"));
    assertTrue(fragment.contains("search_icebridge_peers_contacted"));
    assertTrue(fragment.contains("distributedPeersContacted()"));
  }

  @Test
  public void distributedSearchCountsContactedPeers() throws Exception {
    String performer =
        projectFile("../common/src/main/java/com/frostwire/search/relay/DistributedSearchPerformer.java");
    assertTrue(performer.contains("getPeersContacted()"));
    assertTrue(performer.contains("peersContacted.incrementAndGet()"));

    String mediator = projectFile("src/main/java/com/frostwire/android/gui/SearchMediator.java");
    assertTrue(mediator.contains("distributedPeersContacted()"));
  }

  private static String projectFile(String relative) throws Exception {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relative);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relative);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
