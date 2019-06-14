package com.frostwire.gui.searchfield;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * Non focusable, no border, no margin and insets button with no content area
 * filled.
 *
 * @author Peter Weishapl <petw@gmx.net>
 */
public class BuddyButton extends JButton {
    private static final long serialVersionUID = -2281078972367520220L;

    public BuddyButton() {
        this(null);
    }

    private BuddyButton(String text) {
        super(text);
        setFocusable(false);
        setMargin(SearchFieldUI.NO_INSETS);
        // Windows UI will add 1 pixel for width and height, if this is true
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setIconTextGap(0);
        setBorder(null);
        setOpaque(false);
        setCursor(Cursor.getDefaultCursor());
    }

    // Windows UI overrides Insets.
    // Who knows what other UIs are doing...
    public Insets getInsets() {
        return SearchFieldUI.NO_INSETS;
    }

    public Insets getInsets(Insets insets) {
        return getInsets();
    }

    public Insets getMargin() {
        return getInsets();
    }

    public void setBorder(Border border) {
        // Don't let Motif overwrite my Border
        super.setBorder(BorderFactory.createEmptyBorder());
    }
}