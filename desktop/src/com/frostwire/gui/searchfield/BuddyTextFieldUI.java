package com.frostwire.gui.searchfield;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.TextUI;
import java.awt.*;

/**
 * <p>
 * TODO: queries the text components layout manager for the preferred size.
 * </p>
 *
 * @author Peter Weishapl (petw@gmx.net)
 */
public class BuddyTextFieldUI extends PromptTextFieldUI {
    // Bad hacking: FIXME when know how to get the real margin.
    private static final Insets MAC_MARGIN = new Insets(0, 2, 1, 2);
    private BuddyLayoutAndBorder layoutAndBorder;

    /**
     * Creates a new {@link BuddyTextFieldUI} which delegates most work to
     * another {@link TextUI}.
     *
     * @param delegate
     */
    public BuddyTextFieldUI(TextUI delegate) {
        super(delegate);
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        // yet another dirty mac hack to prevent painting background outside of
        // border.
        if (hasMacTextFieldBorder(c)) {
            Insets borderInsets = layoutAndBorder.getRealBorderInsets();
            borderInsets.left -= MAC_MARGIN.left;
            int height = c.getHeight() - borderInsets.bottom - borderInsets.top + MAC_MARGIN.bottom + MAC_MARGIN.top;
            int width = c.getWidth() - borderInsets.left - borderInsets.right + MAC_MARGIN.right;
            g.clipRect(borderInsets.left, borderInsets.top, width, height);
        }
        super.paint(g, c);
    }

    private boolean hasMacTextFieldBorder(JComponent c) {
        Border border = c.getBorder();
        if (border == layoutAndBorder) {
            border = layoutAndBorder.getBorderDelegate();
        }
        return border != null && border.getClass().getName().equals("apple.laf.CUIAquaTextFieldBorder");
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        layoutAndBorder = createBuddyLayoutAndBorder();
        layoutAndBorder.install((JTextField) c);
    }

    BuddyLayoutAndBorder createBuddyLayoutAndBorder() {
        return new BuddyLayoutAndBorder();
    }

    @Override
    public void uninstallUI(JComponent c) {
        layoutAndBorder.uninstall();
        super.uninstallUI(c);
    }

    /**
     * TODO: comment
     *
     * @see javax.swing.plaf.ComponentUI#getPreferredSize(javax.swing.JComponent)
     */
    public Dimension getPreferredSize(JComponent c) {
        Dimension d = new Dimension();
        Dimension cd = super.getPreferredSize(c);
        Dimension ld = c.getLayout().preferredLayoutSize(c);
        d.height = Math.max(cd.height, ld.height);
        d.width = Math.max(cd.width, ld.width);
        return d;
    }
}