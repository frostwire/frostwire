/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.init;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * A wizard page taller than the dialog pushed the Back/Next/Cancel row below the window: after the
 * "Seeding &amp; Publishing" page got its third choice, the buttons only showed once the user
 * resized the window (frostwire/frostwire#1565). The page's header is a wrapped text area whose
 * minimum height, before it has a width, is thousands of pixels, and the vertical BoxLayout the
 * body used never shrank the page under that minimum.
 */
class SetupWizardButtonRowTest {

  @Test
  void buttonRowStaysInsideTheDialogWhenAPageIsTallerThanIt() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          // What the Seeding & Publishing page reported before it was laid out.
          JPanel page = new JPanel();
          page.setMinimumSize(new Dimension(158, 2904));
          page.setPreferredSize(new Dimension(659, 700));
          JPanel buttonRow = new JPanel();
          buttonRow.add(new JButton("Next >>"));
          int rowHeight = buttonRow.getPreferredSize().height;

          JPanel body = SetupManager.layOutBody(page, buttonRow);
          body.setSize(SetupWindow.SETUP_WIDTH, SetupWindow.SETUP_HEIGHT);
          layOut(body);

          Rectangle row =
              SwingUtilities.convertRectangle(buttonRow.getParent(), buttonRow.getBounds(), body);
          assertEquals(rowHeight, row.height, "the button row keeps its full height");
          assertTrue(
              row.y + row.height <= SetupWindow.SETUP_HEIGHT,
              "the button row ends at "
                  + (row.y + row.height)
                  + ", below the dialog's "
                  + SetupWindow.SETUP_HEIGHT
                  + " px");
          Rectangle pageBounds =
              SwingUtilities.convertRectangle(page.getParent(), page.getBounds(), body);
          assertTrue(pageBounds.y + pageBounds.height <= row.y, "the page stays above the buttons");
        });
  }

  /** Lays out the whole tree; without a native peer, {@code validate()} does nothing. */
  private static void layOut(Container container) {
    container.doLayout();
    for (Component child : container.getComponents()) {
      if (child instanceof Container c) {
        layOut(c);
      }
    }
  }
}
