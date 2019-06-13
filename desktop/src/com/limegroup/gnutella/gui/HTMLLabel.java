package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.awt.*;

public class HTMLLabel extends JEditorPane {
    /**
     *
     */
    private static final long serialVersionUID = -3000702789974804495L;

    public HTMLLabel(String html) {
        super("text/html", html);
        setMargin(new Insets(5, 5, 5, 5));
        setEditable(false);
        setCaretPosition(0);
        addHyperlinkListener(GUIUtils.getHyperlinkListener());
        // make it mimic a JLabel
        JLabel label = new JLabel();
        setBackground(label.getBackground());
        setFont(new Font(label.getFont().getName(),
                label.getFont().getStyle(),
                label.getFont().getSize()));
    }
}
