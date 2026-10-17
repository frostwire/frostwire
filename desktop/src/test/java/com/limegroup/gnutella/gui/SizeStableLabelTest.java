/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Font;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SizeStableLabelTest {

  /** Counts invalidations; JComponent.revalidate() invalidates on the EDT when it lays out. */
  private static final class CountingLabel extends SizeStableLabel {
    final AtomicInteger invalidations = new AtomicInteger();

    CountingLabel() {
      super(null);
      setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); // equal-width characters
    }

    @Override
    public void invalidate() {
      invalidations.incrementAndGet();
      super.invalidate();
    }
  }

  private static int invalidationsAfter(String initialText, String newText) throws Exception {
    int[] result = new int[1];
    SwingUtilities.invokeAndWait(
        () -> {
          CountingLabel label = new CountingLabel();
          new JPanel().add(label); // revalidate() is a no-op without a parent
          label.setText(initialText);
          label.invalidations.set(0);
          label.setText(newText);
          result[0] = label.invalidations.get();
        });
    return result[0];
  }

  @Test
  void sameSizeTextChangeDoesNotRequestLayout() throws Exception {
    assertEquals(0, invalidationsAfter("3 @ 1.2 MB/s", "4 @ 1.3 MB/s"));
  }

  @Test
  void textChangeThatResizesTheLabelStillRequestsLayout() throws Exception {
    assertEquals(1, invalidationsAfter("3 @ 1.2 MB/s", "123 @ 1,234.5 MB/s"));
  }
}
