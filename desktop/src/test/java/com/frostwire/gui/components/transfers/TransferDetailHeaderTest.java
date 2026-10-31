/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.transfers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * The "Transfer Detail hide" header is added with the same 10px left gap as the detail panels'
 * first label ("Total Pieces"), so its title must start at the header's left edge to line up.
 */
class TransferDetailHeaderTest {

  @Test
  void theTitleStartsAtTheLeftEdgeAndTheLinkFollowsIt() {
    JLabel title = new JLabel("Transfer Detail"), hide = new JLabel("hide");
    JPanel header = TransferDetailComponent.header(title, hide);
    header.setSize(600, header.getPreferredSize().height);
    header.doLayout();

    assertEquals(0, title.getX(), "the title must line up with the labels below it");
    assertTrue(hide.getX() >= title.getX() + title.getWidth(), "hide comes after the title");
    assertTrue(hide.getX() < 300, "hide stays next to the title, not centered or pushed right");
  }
}
