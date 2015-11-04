/*
 * ShutdownPaneItem.java
 *
 * Created on March 11, 2002
 */

package com.limegroup.gnutella.gui.options.panes;

import java.io.IOException;

import javax.swing.JTextField;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.gui.SizedTextField;
import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.settings.URLHandlerSettings;

/**
 * This class defines the panel in the options
 * window that allows the user to select the
 * default browser behavior.
 */
public class BrowserPaneItem extends AbstractPaneItem { 

    public final static String TITLE = I18n.tr("Browser Options");
    
    public final static String LABEL = I18n.tr("You can choose which browser to use.");

	/**
	 * Constant for the key of the locale-specific <code>String</code> for the 
	 * label on the component that allows to user to change the setting for
	 * this <tt>PaneItem</tt>.
	 */
	private final String OPTION_LABEL = I18n.tr("Browser");
    
    /** 
     * Handle to the <tt>JTextField</tt> that displays the browser name
     */    
    private JTextField BROWSER;
    
    /**
	 * Creates new BrowserOptionsPaneItem
	 * 
	 * @param key
	 *            the key for this <tt>AbstractPaneItem</tt> that the
	 *            superclass uses to generate locale-specific keys
	 */
	public BrowserPaneItem() {
		super(TITLE, LABEL);
		
		BROWSER = new SizedTextField(25, SizePolicy.RESTRICT_HEIGHT);
		LabeledComponent comp = new LabeledComponent(OPTION_LABEL, BROWSER);
		add(comp.getComponent());
	}

    /**
	 * Applies the options currently set in this <tt>PaneItem</tt>.
	 * 
	 * @throws IOException
	 *             if the options could not be fully applied
	 */
    public boolean applyOptions() throws IOException {
        URLHandlerSettings.BROWSER.setValue(BROWSER.getText());
        return false;
    }

    public boolean isDirty() {
        return !URLHandlerSettings.BROWSER.getValue().equals(BROWSER.getText());
    }
    
    /**
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        BROWSER.setText(URLHandlerSettings.BROWSER.getValue());
    }
    
}
