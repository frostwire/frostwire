/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.options;

import com.limegroup.gnutella.gui.options.panes.AbstractPaneItem;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * This class acts as a proxy and as a "decorator" for an underlying instance
 * of a `MutableTreeNode` implementation.<p>
 * <p>
 * This class includes the most of the functionality of a
 * `DefaultMutableTreeNode`, which it simply wraps, without the
 * coupling that directly subclassing `DefaultMutableTreeNode`
 * would incur.
 */
public class OptionsTreeNode extends DefaultMutableTreeNode {
    /**
     *
     */
    /**
     * The key for uniquely identifying this node.
     */
    private final String _titleKey;
    /**
     * The name of this node as it is displayed to the user.
     */
    private final String _displayName;
    private Class<? extends AbstractPaneItem>[] clazzes;

    /**
     * This constructor sets the values for the name of the node to display
     * to the user as well as the constant key to use for uniquely
     * identifying this node.
     *
     * @param titleKey    the key for the name of the node to display to the
     *                    user and the unique identifier key for this node
     * @param displayName the name of the node as it is displayed to the
     *                    user
     */
    OptionsTreeNode(final String titleKey, final String displayName) {
        _titleKey = titleKey;
        _displayName = displayName;
    }

    /**
     * Defines the class' representation as a `String` object, used
     * in determining how it is displayed in the `JTree`.
     *
     * @return the `String` identifier for the display of this class
     */
    public String toString() {
        return _displayName;
    }

    /**
     * Returns the `String` denoting both the title of the node
     * as well as the unique identifying `String` for the node.
     */
    public String getTitleKey() {
        return _titleKey;
    }

    public Class<? extends AbstractPaneItem>[] getClasses() {
        return clazzes;
    }

    public void setClasses(Class<? extends AbstractPaneItem>[] clazzes) {
        this.clazzes = clazzes;
    }
}
