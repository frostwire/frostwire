/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The first-run wizard used to build and measure every page in the single EDT event that opens it,
 * stalling cold JVMs for seconds (fonts, glyphs, settings, image scaling). Pages must only be built
 * when they are shown.
 */
class SetupManagerLazyPagesTest {

  @Test
  void linkingPagesBuildsNoneOfThem() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          CountingPage first = new CountingPage("first");
          CountingPage middle = new CountingPage("middle");
          CountingPage last = new CountingPage("last");
          SetupWindowHolder holder = new SetupWindowHolder();

          SetupManager.linkPages(holder, List.of(first, middle, last));

          assertEquals(0, first.builds, "first page is built by show(), not while linking");
          assertEquals(0, middle.builds, "later pages wait until the user reaches them");
          assertEquals(0, last.builds, "later pages wait until the user reaches them");
          assertEquals(3, holder.getComponentCount());
        });
  }

  @Test
  void linkingPagesChainsThemInOrder() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          CountingPage first = new CountingPage("first");
          CountingPage middle = new CountingPage("middle");
          CountingPage last = new CountingPage("last");

          SetupManager.linkPages(new SetupWindowHolder(), List.of(first, middle, last));

          assertSame(first, first.getPrevious());
          assertSame(middle, first.getNext());
          assertSame(first, middle.getPrevious());
          assertSame(last, middle.getNext());
          assertSame(middle, last.getPrevious());
          assertSame(last, last.getNext());
        });
  }

  private static final class CountingPage extends SetupWindow {
    int builds;

    CountingPage(String key) {
      super(null, key, key);
    }

    @Override
    void createWindow() {
      builds++;
    }
  }
}
