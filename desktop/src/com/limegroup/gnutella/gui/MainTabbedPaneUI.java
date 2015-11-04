package com.limegroup.gnutella.gui;

import java.awt.Insets;

import javax.swing.plaf.metal.MetalTabbedPaneUI;

/**
 * This class tweaks the tabbed pane UI to provide a little bit more room for
 * the search status icon.
 */
final class MainTabbedPaneUI extends MetalTabbedPaneUI {

	/**
	 * Overrides installDefaults to add a little space above the tab,
	 * providing more room for the searching icon.
	 */
    protected void installDefaults() {
		super.installDefaults();
		tabAreaInsets = new Insets(tabAreaInsets.top+4, tabAreaInsets.left,
								   tabAreaInsets.bottom, tabAreaInsets.right);

    }
}
