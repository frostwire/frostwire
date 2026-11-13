/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * The Library's folder tree, cover art panel and files table are built when the Library is first
 * shown or used, not in an EDT event at startup (frostwire/frostwire#1530).
 */
class OnDemandViewTest {
  @Test
  void nothingIsBuiltUntilItIsNeeded() {
    AtomicInteger builds = new AtomicInteger();
    JPanel panel = new JPanel();

    OnDemandView view = new OnDemandView(panel, builds::incrementAndGet);

    assertEquals(0, builds.get(), "creating the view builds nothing");
    assertFalse(view.isBuilt());
  }

  @Test
  void itIsBuiltOnceHoweverOftenItIsAskedFor() {
    AtomicInteger builds = new AtomicInteger();
    JPanel panel = new JPanel();
    OnDemandView[] view = new OnDemandView[1];
    view[0] =
        new OnDemandView(
            panel,
            () -> {
              builds.incrementAndGet();
              // Building adds children and may need the contents itself: neither builds again.
              panel.add(new JLabel("content"));
              view[0].ensureBuilt();
            });

    view[0].ensureBuilt();
    view[0].ensureBuilt();

    assertEquals(1, builds.get());
    assertTrue(view[0].isBuilt());
    assertEquals(1, panel.getComponentCount());
  }

  @Test
  void theLibraryIsNotBuiltInAStartupEvent() throws Exception {
    String source =
        Files.readString(Path.of("src/main/java/com/frostwire/gui/library/LibraryMediator.java"))
            .replaceAll("\\s+", "");

    assertFalse(
        source.contains("invokeLater(this::initializeSplitPane)"),
        "the constructor must not queue the split pane's construction at startup");
    assertTrue(
        source.contains("newOnDemandView(MAIN_PANEL,this::initializeSplitPane)"),
        "the split pane is built when the Library panel is first shown");
    assertTrue(
        source.contains("libraryView.ensureBuilt();rememberScrollbarsOnMediators();"),
        "showing the files table builds the view first when nothing has shown it yet");
  }
}
