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

package com.limegroup.gnutella.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * JOptionPane.createDialog packs the dialog, which realizes it; a component that still has its drop
 * target then registers it with the OS (OLE RegisterDragDrop on Windows), on the EDT. Removing the
 * drop targets from the dialog afterwards was too late (Icebase #feb36675ccc4,
 * frostwire/frostwire#1536, after #1471).
 */
class OptionPaneDropTargetOrderTest {
  private static String source(String name) throws Exception {
    return Files.readString(Path.of("src/main/java/com/limegroup/gnutella/gui/" + name))
        .replaceAll("//[^\\n]*", "")
        .replaceAll("\\s+", "");
  }

  @Test
  void theErrorDialogWithALinkDropsItsTargetsBeforeItIsCreated() throws Exception {
    assertTrue(
        source("MessageService.java")
            .contains("DropTargets.removeAll(optionPane);JDialogdialog=optionPane.createDialog("));
  }

  @Test
  void disposableMessagesDropTheirTargetsBeforeTheDialogIsCreated() throws Exception {
    assertTrue(
        source("MessageService.java")
            .contains("DropTargets.removeAll(pane);JDialogdialog=pane.createDialog("));
  }

  @Test
  void theInputDialogDropsItsTargetsBeforeItIsCreated() throws Exception {
    assertTrue(
        source("DropTargets.java").contains("removeAll(pane);JDialogdialog=pane.createDialog("));
  }
}
