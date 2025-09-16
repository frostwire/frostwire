/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.options;

import com.limegroup.gnutella.gui.options.panes.AbstractPaneItem;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * This class acts as a proxy and as a "decorator" for an underlying instance
 * of a <tt>MutableTreeNode</tt> implementation.<p>
 * <p>
 * This class includes the most of the functionality of a
 * <tt>DefaultMutableTreeNode</tt>, which it simply wraps, without the
 * coupling that directly subclassing <tt>DefaultMutableTreeNode</tt>
 * would incur.
 */
public class OptionsTreeNode extends DefaultMutableTreeNode {
    /**
     *
     */
    private static final long serialVersionUID = 5889522847540513086L;
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
     * Defines the class' representation as a <tt>String</tt> object, used
     * in determining how it is displayed in the <tt>JTree</tt>.
     *
     * @return the <tt>String</tt> identifier for the display of this class
     */
    public String toString() {
        return _displayName;
    }

    /**
     * Returns the <tt>String</tt> denoting both the title of the node
     * as well as the unique identifying <tt>String</tt> for the node.
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
