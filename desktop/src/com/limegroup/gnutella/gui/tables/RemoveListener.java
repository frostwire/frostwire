package com.limegroup.gnutella.gui.tables;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * This class is an <tt>ActionListener</tt> that removes the
 * selected rows in the component
 */
public final class RemoveListener implements ActionListener {
    
    private ComponentMediator<?> cm;
    
    RemoveListener(ComponentMediator<?> cm) {
        this.cm = cm;
   }

	public void actionPerformed(ActionEvent ae) {
		//send message to the ComponentMediator
	    cm.removeSelection();
	}	
}
