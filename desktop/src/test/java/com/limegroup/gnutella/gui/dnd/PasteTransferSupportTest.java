/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.dnd;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.datatransfer.StringSelection;
import java.lang.reflect.Field;
import javax.swing.JTable;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1575: Swing calls canImport for a paste (Ctrl+V, Edit &gt; Paste) too, and
 * the drop-only TransferSupport calls throw IllegalStateException ("Not a drop") then.
 */
class PasteTransferSupportTest {

  private static TransferHandler.TransferSupport paste() {
    return new TransferHandler.TransferSupport(new JTable(), new StringSelection("x"));
  }

  /** A TransferSupport that reports a drop (a real one needs a native drag event). */
  private static TransferHandler.TransferSupport drop() throws Exception {
    TransferHandler.TransferSupport support = paste();
    set(support, "isDrop", true);
    return support;
  }

  private static void set(Object o, String name, Object value) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    f.set(o, value);
  }

  private static boolean getBoolean(Object o, String name) throws Exception {
    Field f = o.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.getBoolean(o);
  }

  @Test
  void hidingTheDropLocationOfAPasteDoesNotThrow() {
    assertDoesNotThrow(() -> DNDUtils.hideDropLocation(paste()));
  }

  @Test
  void hidingTheDropLocationOfADropStillHidesIt() throws Exception {
    TransferHandler.TransferSupport support = drop();

    DNDUtils.hideDropLocation(support);

    assertTrue(getBoolean(support, "showDropLocationIsSet"));
    assertFalse(getBoolean(support, "showDropLocation"));
  }

  @Test
  void aPasteHasNoDropLocation() {
    assertNull(assertDoesNotThrow(() -> DNDUtils.dropLocationOrNull(paste())));
  }
}
