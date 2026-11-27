/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.MediaType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1616: the Library tree's media-type icons were decoded from the jar on the
 * EDT, the first time the tree was painted. The startup preload loads them.
 */
class NamedMediaTypeIconPreloadTest {

  @Test
  void preloadLoadsEveryLibraryTreeIcon() {
    NamedMediaType.preloadLibraryIcons();

    for (MediaType type : NamedMediaType.LIBRARY_TREE_MEDIA_TYPES.get()) {
      assertTrue(
          NamedMediaType.getFromMediaType(type).isIconLoaded(),
          type.getMimeType() + " icon was not preloaded");
    }
  }

  @Test
  void startupCallsThePreloadOffTheEdt() throws Exception {
    String initializer =
        Files.readString(
            Path.of("src/main/java/com/limegroup/gnutella/gui/Initializer.java"),
            StandardCharsets.UTF_8);
    assertTrue(initializer.contains("NamedMediaType.preloadLibraryIcons();"));
  }

  @Test
  void thePreloadCoversTheMediaTypesTheLibraryTreeShows() throws Exception {
    String explorer =
        Files.readString(
            Path.of("src/main/java/com/frostwire/gui/library/LibraryExplorer.java"),
            StandardCharsets.UTF_8);
    for (String type : new String[] {"Audio", "Video", "Image", "Program", "Document"}) {
      assertTrue(
          explorer.contains("MediaType.get" + type + "MediaType()"),
          "LibraryExplorer no longer shows " + type);
    }
    assertTrue(NamedMediaType.LIBRARY_TREE_MEDIA_TYPES.get().size() == 6);
  }
}
