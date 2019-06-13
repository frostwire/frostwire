package com.limegroup.gnutella.gui.tables;

import javax.swing.*;

/**
 * Default implementation of IconAndNameHolder.
 * <p>
 * Stores an Icon and a String so that both can be displayed
 * in a single column.
 */
public final class IconAndNameHolderImpl implements IconAndNameHolder,
        Comparable<IconAndNameHolderImpl> {
    private final Icon _icon;
    private final String _name;

    public IconAndNameHolderImpl(Icon icon, String name) {
        _icon = icon;
        _name = name;
    }

    public int compareTo(IconAndNameHolderImpl o) {
        return AbstractTableMediator.compare(_name, o._name);
    }

    public Icon getIcon() {
        return _icon;
    }

    public String getName() {
        return _name;
    }

    public String toString() {
        return _name;
    }
}