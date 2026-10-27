/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.limegroup.gnutella.gui.CountingRepaintManager;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * The search media type buttons count results, and a busy search updates them once per result. A
 * count that keeps the button's size must not ask for a layout: each request invalidates the
 * hierarchy up to the main window (frostwire/frostwire#1342).
 */
class SchemaBoxCounterTest {

  @Test
  void countsOfTheSameWidthRequestNoLayout() throws Exception {
    int requests =
        CountingRepaintManager.layoutRequestsDuring(
            () -> {
              JPanel parent = new JPanel();
              SchemaBox.CounterButton button = new SchemaBox.CounterButton("0");
              button.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12)); // equal-width digits
              parent.add(button);
              button.setText("10");
              CountingRepaintManager.reset();
              for (int count = 11; count < 100; count++) {
                button.setText(String.valueOf(count));
              }
            });
    assertEquals(0, requests);
  }

  @Test
  void countGainingADigitRequestsALayout() throws Exception {
    int requests =
        CountingRepaintManager.layoutRequestsDuring(
            () -> {
              JPanel parent = new JPanel();
              SchemaBox.CounterButton button = new SchemaBox.CounterButton("0");
              button.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
              parent.add(button);
              button.setText("99");
              CountingRepaintManager.reset();
              button.setText("100");
            });
    assertEquals(1, requests);
  }

  @Test
  void mediaTypeButtonsAreCounterButtons() throws Exception {
    String relative = "src/main/java/com/limegroup/gnutella/gui/search/SchemaBox.java";
    Path path = Path.of(relative);
    if (!Files.exists(path)) {
      path = Path.of("desktop", relative);
    }
    String source = Files.readString(path);
    assertTrue(
        source.contains("JToggleButton button = new CounterButton("),
        "the counting media type buttons must be CounterButtons");
  }
}
