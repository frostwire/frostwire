/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * SearchResultDisplayer.addQueryResult retitles the search tab, "query (N results)", once per
 * result. The tab header needs the theme images to be built, which a headless test can't load, so
 * this checks the source: the title label must be a SizeStableLabel (see SizeStableLabelTest), or
 * each result re-lays out the whole window (frostwire/frostwire#1342).
 */
class SearchTabbedPaneTitleTest {

  @Test
  void searchTabTitleIsASizeStableLabel() throws Exception {
    String relative = "src/main/java/com/limegroup/gnutella/gui/search/SearchTabbedPane.java";
    Path path = Path.of(relative);
    if (!Files.exists(path)) {
      path = Path.of("desktop", relative);
    }
    String source = Files.readString(path);
    assertTrue(
        source.contains("labelText = new SizeStableLabel()"),
        "the per-result search tab title must be a SizeStableLabel");
  }
}
