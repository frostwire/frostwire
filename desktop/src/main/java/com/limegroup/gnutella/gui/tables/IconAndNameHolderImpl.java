package com.limegroup.gnutella.gui.tables;

import javax.swing.*;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IconAndNameHolderImpl)) {
            return false;
        }
        IconAndNameHolderImpl other = (IconAndNameHolderImpl) o;
        // Icons have no value equality; shared icon instances compare by identity.
        return _icon == other._icon && Objects.equals(_name, other._name);
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(_icon);
        result = 31 * result + Objects.hashCode(_name);
        return result;
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