package com.limegroup.gnutella.gui.options;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;

/** This class creates the `TreeModel` used in the `JTree` of the options pane. */
final class OptionsTreeModel extends DefaultTreeModel {
  /** Constant handle to the root node of the tree. */
  private OptionsTreeNode ROOT = null;

  /** The constructor constructs the `MutableTreeNode` instances as well as the `TreeModel`. */
  OptionsTreeModel() {
    super(null);
    ROOT = new OptionsTreeNode(OptionsMediator.ROOT_NODE_KEY, "");
    setRoot(ROOT);
  }

  /**
   * Adds a new `OptionsTreeNode` to one of the root node's children. This should only be called
   * during tree construction. The first key may denote the root or any node already in the tree.
   *
   * @param parentKey the unique identifying key of the node to add as well as the key for the
   *     locale-specific name for the node as it appears to the user
   * @param key the unique identifying key of the node to add as well as the key for the
   *     locale-specific name for the node as it appears to the user
   * @param displayName the name of the node as it is displayed to the user
   * @return the created node, or null if the parentKey does not correspond to any node in the tree
   */
  final OptionsTreeNode addNode(
      final String parentKey, final String key, final String displayName) {
    OptionsTreeNode newNode = new OptionsTreeNode(key, displayName);
    MutableTreeNode parentNode;
    if (OptionsMediator.ROOT_NODE_KEY.equals(parentKey)) {
      parentNode = ROOT;
    } else {
      parentNode = getParentNode(ROOT, parentKey);
      if (parentNode == null) return null;
    }
    // insert the new node
    insertNodeInto(newNode, parentNode, parentNode.getChildCount());
    reload(parentNode);
    return newNode;
  }

  /**
   * This method performs a recursive depth-first search for the parent node with the specified key.
   *
   * @param node the current node to search through
   * @param parentKey the key that will match the key of the parent node we are searching for
   * @return the `MutableTreeNode` instance corresponding to the specified key, or `null` if it
   *     could not be found
   */
  private MutableTreeNode getParentNode(MutableTreeNode node, final String parentKey) {
    // note that we use the key to denote equality, as each node may
    // have the same visual name, but it will not have the same key
    for (int i = 0, length = node.getChildCount(); i < length; i++) {
      OptionsTreeNode curNode = (OptionsTreeNode) node.getChildAt(i);
      if (curNode.getTitleKey().equals(parentKey)) return curNode;
      MutableTreeNode found = getParentNode(curNode, parentKey);
      if (found != null) return found;
    }
    return null;
  }
}
