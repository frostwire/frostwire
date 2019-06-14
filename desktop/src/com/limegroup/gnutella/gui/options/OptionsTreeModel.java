package com.limegroup.gnutella.gui.options;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import java.io.IOException;

/**
 * This class creates the <tt>TreeModel</tt> used in the <tt>JTree</tt> of
 * the options pane.
 */
final class OptionsTreeModel extends DefaultTreeModel {
    /**
     *
     */
    private static final long serialVersionUID = -3443600214760041469L;
    /**
     * Constant handle to the root node of the tree.
     */
    private OptionsTreeNode ROOT = null;

    /**
     * The constructor constructs the <tt>MutableTreeNode</tt> instances
     * as well as the <tt>TreeModel</tt>.
     */
    OptionsTreeModel() {
        super(null);
        ROOT = new OptionsTreeNode(OptionsMediator.ROOT_NODE_KEY, "");
        setRoot(ROOT);
    }

    /**
     * Adds a new <tt>OptionsTreeNode</tt> to one of the root node's
     * children.  This should only be called during tree construction.
     * The first key cannot denote the root.
     *
     * @param parentKey   the unique identifying key of the node to add as
     *                    well as the key for the locale-specific name for
     *                    the node as it appears to the user
     * @param key         the unique identifying key of the node to add as well as
     *                    the key for the locale-specific name for the node as it
     *                    appears to the user
     * @param displayName the name of the node as it is displayed to the
     *                    user
     * @return the created node
     * @throws IllegalArgumentException if the parentKey does not
     *                                  correspond to any top-level node
     *                                  in the tree
     */
    final OptionsTreeNode addNode(
            final String parentKey,
            final String key,
            final String displayName) {
        OptionsTreeNode newNode = new OptionsTreeNode(key, displayName);
        MutableTreeNode parentNode;
        if (parentKey == OptionsMediator.ROOT_NODE_KEY) {
            parentNode = ROOT;
        } else {
            try {
                parentNode = getParentNode(ROOT, parentKey);
            } catch (IOException ioe) {
                //the parent node could not be found, so return
                return null;
            }
            if (parentNode == null)
                return null;
        }
        // insert the new node
        insertNodeInto(newNode, parentNode, parentNode.getChildCount());
        reload(parentNode);
        return newNode;
    }

    /**
     * This method performs a recursive depth-first search for the
     * parent node with the specified key.
     *
     * @param node      the current node to search through
     * @param parentKey the key that will match the key of the parent node
     *                  we are searching for
     * @return the <tt>MutableTreeNode</tt> instance corresponding to
     * the specified key, or <tt>null</tt> if it could not be found
     * @throws IOException if a corresponding key does not exist
     */
    private MutableTreeNode getParentNode(
            MutableTreeNode node,
            final String parentKey)
            throws IOException {
        // note that we use the key to denote equality, as each node may
        // have the same visual name, but it will not have the same key
        for (int i = 0, length = node.getChildCount(); i < length; i++) {
            OptionsTreeNode curNode =
                    (OptionsTreeNode) node.getChildAt(i);
            if (curNode.getTitleKey().equals((parentKey)))
                return curNode;
            getParentNode(curNode, parentKey);
            if (curNode.isRoot() && i == (length - 1)) {
                // this means we have looped through all of the nodes
                // without finding the parent key, so throw an exception
                String msg = "Parent node not in options tree.";
                throw new IOException(msg);
            }
        }
        // this will never happen -- the exception should always be thrown
        return null;
    }
}
