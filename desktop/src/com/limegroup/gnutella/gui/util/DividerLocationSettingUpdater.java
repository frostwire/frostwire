package com.limegroup.gnutella.gui.util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JSplitPane;

import org.limewire.setting.IntSetting;


/**
 * Keeps track of the divder location changes of a {@link JSplitPane} and updates
 * an {@link IntSetting}.
 */
public class DividerLocationSettingUpdater {

	private static final LocationChangeListener propertyListener = new LocationChangeListener();
	
	/**
	 * Adds a property change listener to the split pane and updates the int 
	 * setting when the divider location changes.
	 * <p>
	 * Also sets the divider location to the value of the setting.
	 * @param pane
	 * @param setting
	 */
	public static void install(JSplitPane pane, IntSetting setting) {
		pane.setDividerLocation(setting.getValue());
		pane.putClientProperty(propertyListener, setting);
		pane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, propertyListener);
	}
	
	private static class LocationChangeListener implements PropertyChangeListener {

		public void propertyChange(PropertyChangeEvent evt) {
			JSplitPane pane = (JSplitPane)evt.getSource();
			IntSetting setting = (IntSetting)pane.getClientProperty(this);
			setting.setValue(pane.getDividerLocation());
		}
		
	}
}
