/*
 *  XNap Commons
 *
 *  Copyright (C) 2005  Felix Berger
 *  Copyright (C) 2005  Steffen Pingel
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.limegroup.gnutella.gui.trees;

import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

/**
 * AbstractTreeModel provides a base class for tree models that is not based on
 * TreeNode objects.
 * 
 * @author Steffen Pingel
 */
public abstract class AbstractTreeModel implements TreeModel {

    protected transient EventListenerList listenerList = new EventListenerList();

    protected StringBuilder root;

    public AbstractTreeModel(String root) {
        this.root = new StringBuilder(root);
    }
    
    public void changeRootText(String newText) {
        root.setLength(0);
        root.append(newText);
    }

    public void addTreeModelListener(TreeModelListener l) {
        listenerList.add(TreeModelListener.class, l);
    }

    protected void fireTreeNodesInserted(TreeModelEvent e) {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TreeModelListener.class)
                ((TreeModelListener) listeners[i + 1]).treeNodesInserted(e);
        }
    }

    protected void fireTreeNodesRemoved(TreeModelEvent e) {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TreeModelListener.class)
                ((TreeModelListener) listeners[i + 1]).treeNodesRemoved(e);

        }
    }

    protected void fireTreeStructureChanged(TreeModelEvent e) {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TreeModelListener.class)
                ((TreeModelListener) listeners[i + 1]).treeStructureChanged(e);

        }
    }

    protected void fireTreeNodesChanged(TreeModelEvent e) {
        // Guaranteed to return a non-null array
        Object[] listeners = listenerList.getListenerList();
        // Process the listeners last to first, notifying
        // those that are interested in this event
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == TreeModelListener.class)
                ((TreeModelListener) listeners[i + 1]).treeNodesChanged(e);

        }
    }

    public abstract Object getChild(Object parent, int index);

    public abstract int getChildCount(Object node);

    public abstract int getIndexOfChild(Object parent, Object child);

    public Object getRoot() {
        return root;
    }

    public abstract boolean isLeaf(Object node);

    public void reload() {
        Object[] path = new Object[] { getRoot() };
        fireTreeStructureChanged(new TreeModelEvent(this, path));
    }

    public void removeTreeModelListener(TreeModelListener l) {
        listenerList.remove(TreeModelListener.class, l);
    }

    public void valueForPathChanged(TreePath path, Object value) {

    }

}
