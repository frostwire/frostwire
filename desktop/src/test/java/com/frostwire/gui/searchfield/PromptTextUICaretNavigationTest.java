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
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import org.junit.jupiter.api.Test;

/**
 * frostwire/frostwire#1560: while a prompt field is empty, PromptTextUI paints the prompt instead
 * of the wrapped UI, so BasicTextUI never marks itself painted and answers -1 for every caret move.
 * Shift+Left then moved the caret's dot to -1 and DefaultCaret threw StateInvariantError.
 */
class PromptTextUICaretNavigationTest {

  @Test
  void shiftArrowInAnEmptyPromptFieldKeepsTheCaretInTheDocument() throws Exception {
    onEdt(
        () -> {
          JTextField field = promptField();
          paint(field);

          select(field, DefaultEditorKit.selectionBackwardAction);
          select(field, DefaultEditorKit.selectionForwardAction);

          assertEquals(0, field.getCaret().getDot());
          assertEquals(0, field.getCaret().getMark());
        });
  }

  @Test
  void shiftArrowBeforeTheTextIsFirstPaintedSelectsACharacter() throws Exception {
    onEdt(
        () -> {
          JTextField field = promptField();
          paint(field); // only the prompt is painted
          field.setText("abc");
          field.setCaretPosition(3);

          select(field, DefaultEditorKit.selectionBackwardAction);

          assertEquals("c", field.getSelectedText());
        });
  }

  private static JTextField promptField() {
    JTextField field = new JTextField();
    PromptSupport.init("Search...", Color.GRAY, Color.WHITE, field);
    assertTrue(field.getUI() instanceof PromptTextUI, "prompt-aware UI must be installed");
    field.setBounds(0, 0, 200, 24);
    // A focused field shows its selection; that is when DefaultCaret highlights the bad range.
    field.getCaret().setSelectionVisible(true);
    return field;
  }

  private static void paint(JTextField field) {
    BufferedImage image = new BufferedImage(200, 24, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    try {
      field.paint(g);
    } finally {
      g.dispose();
    }
  }

  private static void select(JTextField field, String actionName) {
    Action action = field.getActionMap().get(actionName);
    action.actionPerformed(new ActionEvent(field, ActionEvent.ACTION_PERFORMED, actionName));
  }

  private static void onEdt(Runnable body) throws Exception {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            body.run();
          } catch (Throwable t) {
            failure.set(t);
          }
        });
    if (failure.get() instanceof Error error) {
      throw error;
    }
    if (failure.get() instanceof Exception exception) {
      throw exception;
    }
  }
}
