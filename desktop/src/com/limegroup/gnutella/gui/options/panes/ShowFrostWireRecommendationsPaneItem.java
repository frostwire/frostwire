/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.options.panes;

import java.io.IOException;

import javax.swing.JCheckBox;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.LabeledComponent;
import com.limegroup.gnutella.settings.UpdateManagerSettings;

/**
 * Pane to let the user decide wether or not to see the FrostWire recommended software.
 */
public final class ShowFrostWireRecommendationsPaneItem extends AbstractPaneItem {

    public final static String TITLE = I18n.tr("FrostWire Recommendations");
    
    public final static String LABEL = I18n.tr("By enabling this feature you become eligible to receive FrostWire sponsored offers and software recommendations to complement your experience.");

	/**
	 * Constant for the key of the locale-specific <tt>String</tt> for the 
	 * frostclick promotions enabled check box label..
	 */
    private final String SHOW_FROSTWIRE_RECOMMENDATIONS_LABEL = 
        I18n.tr("Enable FrostWire Recommendations (highly recommended):");
    
    /**
	 * Constant for the check box that specifies whether to enable or 
	 * disable frostclick promos
	 */
    private final JCheckBox CHECK_BOX = new JCheckBox();
    
    /**
	 * The constructor constructs all of the elements of this
	 * <tt>AbstractPaneItem</tt>.
	 * 
	 * @param key the key for this <tt>AbstractPaneItem</tt> that the
	 *        superclass uses to generate locale-specific keys
	 */
	public ShowFrostWireRecommendationsPaneItem() {
		super(TITLE, LABEL);
		
		LabeledComponent c = new LabeledComponent(SHOW_FROSTWIRE_RECOMMENDATIONS_LABEL,
				CHECK_BOX, LabeledComponent.LEFT_GLUE, LabeledComponent.LEFT);
		add(c.getComponent());
	}

    /**
	 * Defines the abstract method in <tt>AbstractPaneItem</tt>.<p>
	 *
	 * Sets the options for the fields in this <tt>PaneItem</tt> when the 
	 * window is shown.
	 */
    public void initOptions() {
        CHECK_BOX.setSelected(UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS.getValue());
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
    	UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS.setValue(CHECK_BOX.isSelected());
        return false;
    }
    
    public boolean isDirty() {
        return UpdateManagerSettings.SHOW_FROSTWIRE_RECOMMENDATIONS.getValue() != CHECK_BOX.isSelected();   
    }    
}


