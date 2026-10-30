/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.searchfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

class PromptTextUIIdempotenceTest {

  @Test
  void repeatedSizingDoesNotRewriteUnchangedPrompt() {
    JTextField field = new JTextField();
    PromptSupport.init("Search...", Color.GRAY, Color.WHITE, field);

    assertTrue(field.getUI() instanceof PromptTextUI, "prompt-aware UI must be installed");
    PromptTextUI ui = (PromptTextUI) field.getUI();
    JTextComponent prompt = ui.getPromptComponent(field);

    AtomicInteger documentEvents = new AtomicInteger();
    prompt
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void insertUpdate(DocumentEvent e) {
                documentEvents.incrementAndGet();
              }

              public void removeUpdate(DocumentEvent e) {
                documentEvents.incrementAndGet();
              }

              public void changedUpdate(DocumentEvent e) {
                documentEvents.incrementAndGet();
              }
            });

    ui.getPreferredSize(field);
    assertEquals(
        0, documentEvents.get(), "sizing an unchanged prompt must not rewrite its document");

    ui.getPreferredSize(field);
    assertEquals(0, documentEvents.get(), "repeated sizing must not rewrite the prompt document");
  }

  /**
   * frostwire/frostwire#1410: getPreferredSize copies the field's state onto the prompt on every
   * call, and setMargin invalidated it even with equal insets, throwing away the prompt view's
   * layout on every size query MigLayout makes.
   */
  @Test
  void syncingAnUnchangedFieldLeavesThePromptLaidOut() {
    AtomicInteger invalidations = new AtomicInteger();
    JTextField prompt =
        new JTextField() {
          @Override
          public void invalidate() {
            invalidations.incrementAndGet();
            super.invalidate();
          }
        };
    JTextField field = new JTextField();
    field.setBounds(0, 0, 200, 24);

    PromptTextUI.syncPromptProperties(field, prompt);
    invalidations.set(0);
    for (int i = 0; i < 3; i++) {
      PromptTextUI.syncPromptProperties(field, prompt);
    }

    assertEquals(0, invalidations.get(), "syncing an unchanged field invalidated its prompt");
  }

  /** The fix copies only what changed; changes must still reach the prompt. */
  @Test
  void syncingAChangedFieldStillUpdatesThePrompt() {
    JTextField prompt = new JTextField();
    JTextField field = new JTextField();
    PromptTextUI.syncPromptProperties(field, prompt);

    field.setEnabled(false);
    field.setEditable(false);
    field.setOpaque(false);
    field.setMargin(new java.awt.Insets(1, 2, 3, 4));
    field.setBounds(5, 6, 70, 8);
    field.setSelectionColor(Color.RED);
    field.setSelectedTextColor(Color.BLUE);
    PromptTextUI.syncPromptProperties(field, prompt);

    assertEquals(false, prompt.isEnabled());
    assertEquals(false, prompt.isEditable());
    assertEquals(false, prompt.isOpaque());
    assertEquals(new java.awt.Insets(1, 2, 3, 4), prompt.getMargin());
    assertEquals(new java.awt.Rectangle(5, 6, 70, 8), prompt.getBounds());
    assertEquals(Color.RED, prompt.getSelectionColor());
    assertEquals(Color.BLUE, prompt.getSelectedTextColor());
  }
}
