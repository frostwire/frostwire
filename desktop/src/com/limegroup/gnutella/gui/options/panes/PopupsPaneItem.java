package com.limegroup.gnutella.gui.options.panes;

import java.io.IOException;

import javax.swing.JCheckBox;


import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.QuestionsHandler;

/**
 * This class defines the panel in the options window that allows the user
 * to redisplay all messages.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class PopupsPaneItem extends AbstractPaneItem {

    public final static String TITLE = I18n.tr("FrostWire Popups");
    
    public final static String LABEL = I18n.tr("Redisplay messages for which you have chosen \'Do not display this message again\' or \'Always use this answer\'.");

	/**
	 * Constant for the key of the locale-specific <tt>String</tt> for the 
	 * chat enabled check box label in the options window.
	 */
	private final String CHECK_BOX_LABEL = 
		I18n.tr("Revert to Default:");

	/**
	 * Constant for the check box that specifies whether or not downloads 
	 * should be automatically cleared.
	 */
	private final JCheckBox CHECK_BOX = new JCheckBox();

	/**
	 * The constructor constructs all of the elements of this
	 * <tt>AbstractPaneItem</tt>.
	 * 
	 * @param key the key for this <tt>AbstractPaneItem</tt> that the
	 *        superclass uses to generate locale-specific keys
	 */
	public PopupsPaneItem() {
	    super(TITLE, LABEL);
	    
		LabeledComponent comp = new LabeledComponent(CHECK_BOX_LABEL,
				CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
		add(comp.getComponent());
	}

	/**
	 * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
	 *
	 * Sets the options for the fields in this <tt>PaneItem</tt> when the 
	 * window is shown.
	 */
	public void initOptions() {
        // always display the checkbox as unchecked.
	}

	/**
	 * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
	 *
	 * Applies the options currently set in this window, displaying an
	 * error message to the user if a setting could not be applied.
	 *
	 * @throws IOException if the options could not be applied for some reason
	 */
	public boolean applyOptions() throws IOException {
	    if ( CHECK_BOX.isSelected() )
		    QuestionsHandler.instance().revertToDefault();
        CHECK_BOX.setSelected(false);
        return false;
	}
	
    public boolean isDirty() {
        return CHECK_BOX.isSelected();
    }
}
