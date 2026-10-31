/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import org.junit.jupiter.api.Test;

/**
 * Every multi-line tooltip builds a SkinMultilineToolTipUI, whose JTextArea installed the look and
 * feel's text area UI in its constructor only to have it uninstalled and replaced by a
 * BasicTextAreaUI right after. That install and uninstall ran on the EDT for every tooltip and
 * stalled the first one on a slow machine (Icebase #02958c8a5b5c). The text area must get its
 * BasicTextAreaUI directly.
 */
class SkinMultilineToolTipUITextAreaTest {

  static final AtomicInteger LOOK_AND_FEEL_TEXT_AREA_UIS = new AtomicInteger();

  /** Stands in for the look and feel's text area UI and counts how often it is created. */
  public static class CountingTextAreaUI extends BasicTextAreaUI {
    public static ComponentUI createUI(JComponent c) {
      LOOK_AND_FEEL_TEXT_AREA_UIS.incrementAndGet();
      return new CountingTextAreaUI();
    }
  }

  @Test
  void theTooltipTextAreaNeverInstallsTheLookAndFeelTextAreaUi() throws Exception {
    Object previous = UIManager.get("TextAreaUI");
    UIManager.put("TextAreaUI", CountingTextAreaUI.class.getName());
    try {
      LOOK_AND_FEEL_TEXT_AREA_UIS.set(0);
      SwingUtilities.invokeAndWait(SkinMultilineToolTipUI::new);
      assertEquals(0, LOOK_AND_FEEL_TEXT_AREA_UIS.get());
    } finally {
      UIManager.put("TextAreaUI", previous);
    }
  }
}
