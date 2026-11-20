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
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.updates;

import com.limegroup.gnutella.gui.DropTargets;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * The "new update available" window: the update message (HTML, with the list of what is new) in a
 * resizable window whose text wraps to its width and scrolls when it is taller than the window. It
 * replaces a JOptionPane, which has a fixed size and one line per {@code <br>}.
 */
final class UpdateMessageDialog {
  /** The result when the window is closed without choosing a button, like JOptionPane's. */
  static final int CLOSED = JOptionPane.CLOSED_OPTION;

  private static final int WIDTH = 540;
  private static final int MIN_HEIGHT = 150;
  private static final int MAX_HEIGHT = 440;

  private UpdateMessageDialog() {}

  /**
   * Shows the window and waits for the choice. Call on the EDT.
   *
   * @param options the button labels, left to right; the last one is the default button and the
   *     first one is what Escape chooses
   * @return the index of the chosen option, or {@link #CLOSED}
   */
  static int show(String title, String html, String[] options) {
    int[] choice = {CLOSED};
    JDialog dialog = new JDialog((java.awt.Frame) null, title, true);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    dialog.setResizable(true);
    JPanel content =
        content(
            html,
            options,
            index -> {
              choice[0] = index;
              dialog.dispose();
            });
    dialog.setContentPane(content);
    // A window that takes no drops: the JDK's drag and drop setup is slow on first use.
    DropTargets.removeAll(dialog);
    JButton[] buttons = buttonsOf(content);
    if (buttons.length > 0) {
      dialog.getRootPane().setDefaultButton(buttons[buttons.length - 1]);
    }
    dialog
        .getRootPane()
        .registerKeyboardAction(
            e -> dialog.dispose(),
            KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW);
    dialog.pack();
    // Never narrower than the row of buttons, which does not wrap.
    dialog.setMinimumSize(new Dimension(buttonRowWidth(content) + 40, 220));
    dialog.setLocationRelativeTo(null);
    dialog.setAlwaysOnTop(true);
    dialog.setVisible(true);
    return choice[0];
  }

  /** The message above a row of buttons; {@code onChoice} gets the index of the clicked button. */
  static JPanel content(String html, String[] options, IntConsumer onChoice) {
    JEditorPane text = new JEditorPane("text/html", html);
    text.setEditable(false);
    // No caret blinking in the read-only text; the buttons take the keyboard focus.
    text.setFocusable(false);
    text.setOpaque(false);
    text.setBorder(new EmptyBorder(0, 0, 0, 0));
    // Use the theme's font instead of the HTML kit's default serif.
    text.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    Font font = UIManager.getFont("Label.font");
    if (font != null) {
      text.setFont(font);
    }
    JScrollPane scroll = new JScrollPane(text);
    scroll.setBorder(new EmptyBorder(0, 0, 0, 0));
    scroll.setOpaque(false);
    scroll.getViewport().setOpaque(false);
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setPreferredSize(new Dimension(WIDTH, heightForWidth(text, WIDTH)));

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    buttons.setOpaque(false);
    for (int i = 0; i < options.length; i++) {
      final int index = i;
      JButton button = new JButton(options[i]);
      button.addActionListener(
          new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
              onChoice.accept(index);
            }
          });
      buttons.add(button);
    }

    JPanel panel = new JPanel(new BorderLayout(0, 14));
    panel.setBorder(new EmptyBorder(16, 18, 14, 18));
    panel.add(scroll, BorderLayout.CENTER);
    panel.add(buttons, BorderLayout.SOUTH);
    return panel;
  }

  /** The height the text needs when wrapped to {@code width}, within the window's limits. */
  static int heightForWidth(JEditorPane text, int width) {
    return Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, textHeightForWidth(text, width) + 4));
  }

  /** The height of the text wrapped to {@code width}, whatever the window's limits. */
  static int textHeightForWidth(JEditorPane text, int width) {
    text.setSize(width, Short.MAX_VALUE);
    return text.getPreferredSize().height;
  }

  /** The width the row of buttons needs. */
  static int buttonRowWidth(JPanel content) {
    return ((BorderLayout) content.getLayout())
        .getLayoutComponent(BorderLayout.SOUTH)
        .getPreferredSize()
        .width;
  }

  private static JButton[] buttonsOf(JPanel content) {
    JPanel row =
        (JPanel) ((BorderLayout) content.getLayout()).getLayoutComponent(BorderLayout.SOUTH);
    return java.util.Arrays.stream(row.getComponents())
        .filter(JButton.class::isInstance)
        .toArray(JButton[]::new);
  }
}
