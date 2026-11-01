/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.slides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Search tabs build the slideshow on the EDT, and it started a new thread there to download its
 * slides (Icebase #60bdaad42bb4, GitHub #1457). Creating a thread can stall the EDT; the load is
 * handed to an executor instead.
 */
class MultimediaSlideshowPanelLoaderTest {

  @Test
  void loadingIsHandedToTheExecutorNotStartedOnTheEdt() throws Exception {
    List<Runnable> submitted = new ArrayList<>();
    SwingUtilities.invokeAndWait(
        () -> new MultimediaSlideshowPanel("http://127.0.0.1:9/slides", List.of(), submitted::add));

    assertEquals(1, submitted.size(), "the slide download goes to the executor");
  }

  @Test
  void theSlideshowNeverStartsAThreadItself() throws Exception {
    String source =
        Files.readString(
                Path.of(System.getProperty("user.dir"))
                    .resolve(
                        "src/main/java/com/frostwire/gui/components/slides/MultimediaSlideshowPanel.java"),
                StandardCharsets.UTF_8)
            .replaceAll("\\s+", "");
    assertFalse(source.contains("newThread("), "a thread started on the EDT can stall it");
  }
}
