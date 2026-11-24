/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.searchfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.geom.Rectangle2D;
import javax.swing.JTextField;
import javax.swing.text.Position;
import org.junit.jupiter.api.Test;

/**
 * The input method (Windows 11, 48 s after start) asks where the search field's text is before the
 * field has a size; BasicTextUI answers null then, and PromptTextUI called getBounds() on it:
 * NullPointerException (Icebase #3f551650418a, #77a49f4595e1, #81d2da1eec70, #f473c4f738fc).
 */
class PromptTextUINullBoundsTest {

  @Test
  void aFieldWithNoSizeYetAnswersNullInsteadOfThrowing() throws Exception {
    JTextField field = new JTextField("query");
    PromptTextFieldUI ui = new PromptTextFieldUI(field.getUI());

    assertNull(ui.modelToView2D(field, 0, Position.Bias.Forward));
  }

  @Test
  void theBoundsOfAnAnswerAreIntegerBoundsAndNullStaysNull() {
    assertNull(PromptTextUI.bounds(null));
    Rectangle2D rounded = PromptTextUI.bounds(new Rectangle2D.Double(1.2, 2.2, 3.2, 4.2));
    assertEquals(new java.awt.Rectangle(1, 2, 4, 5), rounded);
  }
}
