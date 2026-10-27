/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.tests;

import static org.junit.jupiter.api.Assertions.assertNull;

import com.limegroup.gnutella.gui.actions.FileMenuActions;
import java.awt.Component;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * The File menu creates its actions while the main window is built on the EDT at startup. The "Open
 * .Torrent, Magnet or Video URL" action built its dialog's text field there (Icebase
 * #73c1ccc3adaa); every component of the dialog must wait until the user opens it.
 */
class OpenMagnetActionLazyDialogTest {

  @Test
  void creatingTheActionBuildsNoComponents() throws Exception {
    FileMenuActions.OpenMagnetTorrentVideoUrlAction action =
        new FileMenuActions.OpenMagnetTorrentVideoUrlAction();

    for (Field field : action.getClass().getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers())
          && Component.class.isAssignableFrom(field.getType())) {
        field.setAccessible(true);
        assertNull(field.get(action), field.getName() + " built with the action");
      }
    }
  }
}
