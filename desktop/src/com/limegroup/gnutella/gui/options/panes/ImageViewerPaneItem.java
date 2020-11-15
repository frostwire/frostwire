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
 * default image behavior.
 */
public class ImageViewerPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Image Options");
    private final static String LABEL = I18n.tr("You can choose which image viewer to use.");
    /**
     * Handle to the <tt>JTextField</tt> that displays the viewer name
     */
    private final JTextField _viewerField;

    /**
     * Creates new ImageViewerOptionsPaneItem
     *
     */
    public ImageViewerPaneItem() {
        super(TITLE, LABEL);
        _viewerField = new SizedTextField(25, SizePolicy.RESTRICT_HEIGHT);
        /*
          Constant for the key of the locale-specific <code>String</code> for the
          label on the component that allows to user to change the setting for
          this <tt>PaneItem</tt>.
         */
        String OPTION_LABEL = I18n.tr("Image Viewer");
        LabeledComponent comp = new LabeledComponent(OPTION_LABEL, _viewerField);
        add(comp.getComponent());
    }

    /**
     * Applies the options currently set in this <tt>PaneItem</tt>.
     *
     */
    public boolean applyOptions() {
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
