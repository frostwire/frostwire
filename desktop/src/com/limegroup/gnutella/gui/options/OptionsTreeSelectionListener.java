package com.limegroup.gnutella.gui.options;

import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;

/**
 * This class handles the selection of nodes in the options tree constroller.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
final class OptionsTreeSelectionListener implements TreeSelectionListener {
	
	/**
	 * Handle to the <code>JTree</code> instance that utilizes this listener.
	 */
	private JTree _tree;

	/**
	 * Sets the <code>JTree</code> reference that utilizes this listener.
	 *
	 * @param tree the <code>JTree</code> instance that utilizes this listener
	 */
	OptionsTreeSelectionListener(final JTree tree) {
		_tree = tree;
	}

	/**
	 * Implements the <code>TreeSelectionListener</code> interface.
	 * Takes any action necessary for responding to the selection of a 
	 * node in the tree.
	 *
	 * @param e the <code>TreeSelectionEvent</code> object containing
	 *          information about the selection
	 */
	public void valueChanged(TreeSelectionEvent e) {
		Object obj = _tree.getLastSelectedPathComponent();
		if(obj instanceof OptionsTreeNode) {
			OptionsTreeNode node = (OptionsTreeNode)obj;
			
			// only leaf nodes have corresponding panes to display
			if(node.isLeaf())
				OptionsMediator.instance().handleSelection(node);
			else {
				_tree.expandPath(new TreePath(node.getPath()));
				OptionsMediator.instance().handleSelection((OptionsTreeNode) node.getFirstChild());
			}
		}
	}
}
