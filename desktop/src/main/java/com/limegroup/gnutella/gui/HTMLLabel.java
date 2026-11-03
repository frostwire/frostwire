package com.limegroup.gnutella.gui;

import java.awt.*;
import javax.swing.*;

public class HTMLLabel extends JEditorPane {
  /** */
  public HTMLLabel(String html) {
    super("text/html", html);
    setMargin(new Insets(5, 5, 5, 5));
    setEditable(false);
    setCaretPosition(0);
    addHyperlinkListener(GUIUtils.getHyperlinkListener());
    // make it mimic a JLabel
    JLabel label = new JLabel();
    setBackground(label.getBackground());
    setFont(
        new Font(label.getFont().getName(), label.getFont().getStyle(), label.getFont().getSize()));
  }

  /**
   * A read-only label takes no drops. The look and feel gives editor panes a drop target when it
   * installs their UI, at construction and on a theme change, and it would make the window register
   * with the OS for drag-and-drop (frostwire/frostwire#1471).
   */
  @Override
  public void updateUI() {
    super.updateUI();
    setDropTarget(null);
  }
}
