/*
 * ShutdownPaneItem.java
 *
 * Created on March 11, 2002
 */

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.GUIUtils.SizePolicy;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.gui.SizedTextField;
import com.limegroup.gnutella.settings.URLHandlerSettings;

import javax.swing.*;

/**
 * This class defines the panel in the options
 * window that allows the user to select the
 * default browser behavior.
 */
public class BrowserPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Browser Options");
    private final static String LABEL = I18n.tr("You can choose which browser to use.");
    /**
     * Handle to the <tt>JTextField</tt> that displays the browser name
     */
    private final JTextField BROWSER;

    /**
     * Creates new BrowserOptionsPaneItem
     *
     */
    public BrowserPaneItem() {
        super(TITLE, LABEL);
        BROWSER = new SizedTextField(25, SizePolicy.RESTRICT_HEIGHT);
        /*
          Constant for the key of the locale-specific <code>String</code> for the
          label on the component that allows to user to change the setting for
          this <tt>PaneItem</tt>.
         */
        String OPTION_LABEL = I18n.tr("Browser");
        LabeledComponent comp = new LabeledComponent(OPTION_LABEL, BROWSER);
        add(comp.getComponent());
    }

    /**
     * Applies the options currently set in this <tt>PaneItem</tt>.
     *
     */
    public boolean applyOptions() {
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
