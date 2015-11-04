package com.frostwire.gui.searchfield;

import java.beans.PropertyChangeListener;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JComponent;

public abstract class AbstractUIChangeHandler implements PropertyChangeListener {
	//prevent double installation.
	private final Set<JComponent> installed = new HashSet<JComponent>();
	
	public void install(JComponent c){
		if(isInstalled(c)){
			return;
		}
		
		c.addPropertyChangeListener("UI", this);
		installed.add(c);
	}
	
	public boolean isInstalled(JComponent c) {
		return installed.contains(c);
	}

	public void uninstall(JComponent c){
		c.removePropertyChangeListener("UI", this);
		installed.remove(c);
	}
}