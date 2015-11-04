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
 * default image behavior.
 */
public class ImageViewerPaneItem extends AbstractPaneItem { 
    
    public final static String TITLE = I18n.tr("Image Options");
    
    public final static String LABEL = I18n.tr("You can choose which image viewer to use.");

	/**
	 * Constant for the key of the locale-specific <code>String</code> for the 
	 * label on the component that allows to user to change the setting for
	 * this <tt>PaneItem</tt>.
	 */
	private final String OPTION_LABEL = I18n.tr("Image Viewer");
    
    /** 
     * Handle to the <tt>JTextField</tt> that displays the viewer name
     */    
    private JTextField _viewerField;
    
    /**
	 * Creates new ImageViewerOptionsPaneItem
	 * 
	 * @param key the key for this <tt>AbstractPaneItem</tt> that the
	 *        superclass uses to generate locale-specific keys
	 */
	public ImageViewerPaneItem() {
	    super(TITLE, LABEL);
	    
		_viewerField = new SizedTextField(25, SizePolicy.RESTRICT_HEIGHT);
		LabeledComponent comp = new LabeledComponent(OPTION_LABEL, _viewerField);
		add(comp.getComponent());
	}
    
    /**
     * Applies the options currently set in this <tt>PaneItem</tt>.
     *
     * @throws IOException if the options could not be fully applied
     */
    public boolean applyOptions() throws IOException {
        URLHandlerSettings.IMAGE_VIEWER.setValue(_viewerField.getText());
        return false;
    }
    
    public boolean isDirty() {
        return !URLHandlerSettings.IMAGE_VIEWER.getValue().equals(_viewerField.getText());
    }

    /**
     * Sets the options for the fields in this <tt>PaneItem</tt> when the
     * window is shown.
     */
    public void initOptions() {
        _viewerField.setText(URLHandlerSettings.IMAGE_VIEWER.getValue());
    }
    
}
