/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.theme;

import com.frostwire.util.SafeText;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.JToolTip;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.plaf.synth.ColorType;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthToolTipUI;

/**
 * The UI of every tooltip (the {@code ToolTipUI} key, see {@link ThemeMediator}). It never builds
 * an HTML view: Synth's own tooltip UI sends any text starting with {@code <html>} through {@code
 * BasicHTML}, which parses it and lays out a CSS view tree on the EDT each time the tooltip is
 * shown, and would load remote images from text that came from a peer (frostwire/frostwire#1331,
 * #1580). One choke point instead of one fix per tooltip, so a new or translated tooltip cannot
 * bring the stall back.
 *
 * <ul>
 *   <li>A short plain tip is painted by Synth exactly as before.
 *   <li>HTML text, or text with line breaks, is shown as plain multi-line text ({@link
 *       ToolTipText#toPlain}), wrapped at {@value #MAX_WIDTH} px, in a text area created only when
 *       needed.
 * </ul>
 *
 * <p>Not thread-safe: Swing, EDT only.
 */
public final class SafeToolTipUI extends SynthToolTipUI {
  static final int MAX_WIDTH = 400;
  private static final String HTML_DISABLE = "html.disable";

  private JTextArea textArea;
  private CellRendererPane rendererPane;
  private String shown;

  public static ComponentUI createUI(JComponent c) {
    return new SafeToolTipUI();
  }

  @Override
  public void installUI(JComponent c) {
    // BasicHTML.updateRenderer() checks this before it builds a view, at install time and on
    // every "tiptext", "font" and "foreground" change
    c.putClientProperty(HTML_DISABLE, Boolean.TRUE);
    super.installUI(c);
  }

  private static boolean multiline(JToolTip tip) {
    return ToolTipText.needsMultiline(tip.getTipText());
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    JToolTip tip = (JToolTip) c;
    if (!multiline(tip)) {
      return super.getPreferredSize(c);
    }
    String plain = plain(tip);
    if (plain.isEmpty()) {
      return new Dimension(0, 0);
    }
    Insets insets = tip.getInsets();
    int width = Math.min(MAX_WIDTH, naturalWidth(tip, plain) + 6);
    JTextArea area = area(tip, plain);
    area.setSize(width, Short.MAX_VALUE);
    int height = area.getPreferredSize().height;
    return new Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom);
  }

  @Override
  protected void paint(SynthContext context, Graphics g) {
    JToolTip tip = (JToolTip) context.getComponent();
    if (!multiline(tip)) {
      super.paint(context, g);
      return;
    }
    Insets insets = tip.getInsets();
    String plain = plain(tip);
    JTextArea area = area(tip, plain);
    g.setColor(context.getStyle().getColor(context, ColorType.TEXT_FOREGROUND));
    g.setFont(tip.getFont());
    area.setForeground(context.getStyle().getColor(context, ColorType.TEXT_FOREGROUND));
    rendererPane.paintComponent(
        g,
        area,
        tip,
        insets.left,
        insets.top,
        tip.getWidth() - (insets.left + insets.right),
        tip.getHeight() - (insets.top + insets.bottom),
        true);
  }

  private static String plain(JToolTip tip) {
    String text = tip.getTipText();
    String plain = ToolTipText.toPlain(text);
    plain = SafeText.sanitize(plain);
    return plain == null ? "" : plain;
  }

  private static int naturalWidth(JToolTip tip, String plain) {
    FontMetrics fm = tip.getFontMetrics(tip.getFont());
    int widest = 0;
    int start = 0;
    while (start <= plain.length()) {
      int end = plain.indexOf('\n', start);
      if (end < 0) {
        end = plain.length();
      }
      widest = Math.max(widest, fm.stringWidth(plain.substring(start, end)));
      start = end + 1;
    }
    return widest;
  }

  private JTextArea area(JToolTip tip, String plain) {
    if (textArea == null) {
      // A default JTextArea installs the look and feel's UI in its constructor and replacing it
      // uninstalled that again on the EDT (Icebase #02958c8a5b5c): install the plain one directly
      textArea =
          new JTextArea() {
            @Override
            public void updateUI() {
              setUI(new BasicTextAreaUI());
            }
          };
      rendererPane = new CellRendererPane();
      textArea.setLineWrap(true);
      textArea.setWrapStyleWord(true);
      textArea.setEditable(false);
      textArea.setOpaque(false);
      textArea.setBorder(null);
    }
    textArea.setFont(tip.getFont());
    if (!plain.equals(shown)) {
      textArea.setText(plain);
      shown = plain;
    }
    return textArea;
  }
}
