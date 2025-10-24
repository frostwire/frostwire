package com.frostwire.gui.searchfield;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicBorders.MarginBorder;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class BuddyLayoutAndBorder extends CompoundBorder implements LayoutManager, Border, PropertyChangeListener, UIResource {
    private static final long serialVersionUID = 2275551286621857718L;
    private JTextField textField;
    private Border borderDelegate;

    /**
     * Installs a {@link BuddyLayoutAndBorder} as a layout and border of the
     * given text field. Registers a {@link PropertyChangeListener} to wrap any
     * subsequently set border on the text field.
     */
    void install(JTextField textField) {
        uninstall();
        this.textField = textField;
        textField.setLayout(this);
        replaceBorderIfNecessary();
        textField.addPropertyChangeListener("border", this);
    }

    public Border getBorderDelegate() {
        return borderDelegate;
    }

    /**
     * Wraps and replaces the text fields default border with this object, to
     * honor the button margins and sizes of the search, clear and popup buttons
     * and the layout style.
     */
    void replaceBorderIfNecessary() {
        Border original = textField.getBorder();
        if (!(original instanceof BuddyLayoutAndBorder)) {
            borderDelegate = original;
            textField.setBorder(this);
        }
    }

    /**
     * Does nothing.
     *
     */
    public void addLayoutComponent(String name, Component comp) {
    }

    public Dimension minimumLayoutSize(Container parent) {
        return preferredLayoutSize(parent);
    }

    public Dimension preferredLayoutSize(Container parent) {
        Dimension d = new Dimension();
        // height of highest buddy.
        for (Component c : BuddySupport.getLeft(textField)) {
            d.height = Math.max(d.height, c.getPreferredSize().height);
        }
        for (Component c : BuddySupport.getRight(textField)) {
            d.height = Math.max(d.height, c.getPreferredSize().height);
        }
        Insets insets = getRealBorderInsets();
        d.height += insets.top + insets.bottom;
        d.width += insets.left + insets.right;
        Insets outerMargin = BuddySupport.getOuterMargin(textField);
        if (outerMargin != null) {
            d.width += outerMargin.left + outerMargin.right;
            d.height += outerMargin.bottom + outerMargin.top;
        }
        return d;
    }

    /**
     * Does nothing.
     *
     */
    public void removeLayoutComponent(Component comp) {
    }

    public void layoutContainer(Container parent) {
        Rectangle visibleRect = getVisibleRect();
        Dimension size;
        for (Component comp : BuddySupport.getLeft(textField)) {
            if (!comp.isVisible()) {
                continue;
            }
            size = comp.getPreferredSize();
            comp.setBounds(visibleRect.x, centerY(visibleRect, size), size.width, size.height);
            visibleRect.x += size.width;
            visibleRect.width -= size.width;
        }
        for (Component comp : BuddySupport.getRight(textField)) {
            if (!comp.isVisible()) {
                continue;
            }
            size = comp.getPreferredSize();
            comp.setBounds(visibleRect.x + visibleRect.width - size.width, centerY(visibleRect, size), size.width,
                    size.height);
            visibleRect.width -= size.width;
        }
    }

    private int centerY(Rectangle rect, Dimension size) {
        return (int) (rect.getCenterY() - (size.height / 2));
    }

    /**
     * @return the rectangle allocated by the text field, including the space
     * allocated by the child components left and right, the text fields
     * original border insets and the outer margin.
     */
    private Rectangle getVisibleRect() {
        Rectangle alloc = SwingUtilities.getLocalBounds(textField);
        substractInsets(alloc, getRealBorderInsets());
        substractInsets(alloc, BuddySupport.getOuterMargin(textField));
        return alloc;
    }

    private void substractInsets(Rectangle alloc, Insets insets) {
        if (insets != null) {
            alloc.x += insets.left;
            alloc.y += insets.top;
            alloc.width -= insets.left + insets.right;
            alloc.height -= insets.top + insets.bottom;
        }
    }

    /**
     * Returns the {@link Insets} of the original {@link Border} plus the space
     * required by the child components.
     *
     * @see javax.swing.border.Border#getBorderInsets(java.awt.Component)
     */
    public Insets getBorderInsets(Component c) {
        Insets insets = null;
        if (borderDelegate != null) {
            // Original insets are cloned to make it work in Mac OS X Aqua LnF.
            // Seems that this LnF uses a shared insets instance which should
            // not be modified.
            // Include margin here
            insets = (Insets) borderDelegate.getBorderInsets(textField).clone();
        } else {
            insets = new Insets(0, 0, 0, 0);
        }
        //somehow this happens sometimes
        if (textField == null) {
            return insets;
        }
        for (Component comp : BuddySupport.getLeft(textField)) {
            insets.left += comp.isVisible() ? comp.getPreferredSize().width : 0;
        }
        for (Component comp : BuddySupport.getRight(textField)) {
            insets.right += comp.isVisible() ? comp.getPreferredSize().width : 0;
        }
        Insets outerMargin = BuddySupport.getOuterMargin(textField);
        if (outerMargin != null) {
            insets.left += outerMargin.left;
            insets.right += outerMargin.right;
            insets.top += outerMargin.top;
            insets.bottom += outerMargin.bottom;
        }
        return insets;
    }

    /**
     * Returns the insets of the original border (without the margin! Beware of
     * {@link MarginBorder}!).
     *
     * @return the insets of the border delegate
     */
    public Insets getRealBorderInsets() {
        if (borderDelegate == null) {
            return new Insets(0, 0, 0, 0);
        }
        Insets insets = borderDelegate.getBorderInsets(textField);
        // for some reason, all LnFs add the margin to the insets.
        // we want the insets without the margin, so substract the margin here!!
        // TODO: consider checking, if the current border really includes the
        // margin. Consider:
        // 1. Not only MarginBorder adds margin
        // 2. Calling getBorderInsets(null) is not appropriate, since some
        // Borders can't handle null values.
        Insets margin = textField.getMargin();
        if (margin != null) {
            insets.left -= margin.left;
            insets.right -= margin.right;
            insets.top -= margin.top;
            insets.bottom -= margin.bottom;
        }
        return insets;
    }

    public boolean isBorderOpaque() {
        if (borderDelegate == null) {
            return false;
        }
        return borderDelegate.isBorderOpaque();
    }

    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        if (borderDelegate != null) {
            borderDelegate.paintBorder(c, g, x, y, width, height);
        }
    }

    public void propertyChange(PropertyChangeEvent evt) {
        replaceBorderIfNecessary();
    }

    public void uninstall() {
        if (textField != null) {
            textField.removePropertyChangeListener("border", this);
            if (textField.getBorder() == this) {
                textField.setBorder(borderDelegate);
            }
            textField.setLayout(null);
            textField = null;
        }
    }

    @Override
    public String toString() {
        return String.format("%s (%s): %s", getClass().getName(), getBorderInsets(null), borderDelegate);
    }

    @Override
    public Border getInsideBorder() {
        return borderDelegate;
    }
}