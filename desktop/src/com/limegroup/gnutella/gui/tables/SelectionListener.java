package com.limegroup.gnutella.gui.tables;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

/**
 * This class listens for selections of rows in the component.
 */
final class SelectionListener implements ListSelectionListener {
    // this really should be stored as an AbstractTableMediator
    // instead of a ComponentMediator, but for some reason
    // it causes an NPE when run on Java 1.3 if that is done.
    private final AbstractTableMediator<?, ?, ?> atm;

    SelectionListener(AbstractTableMediator<?, ?, ?> atm) {
        if (atm == null)
            throw new NullPointerException("null atm");
        this.atm = atm;
    }

    /**
     * Implements the <tt>ListSelectionListener</tt> interface, setting
     * the states of buttons, menus, and anything else depending on the
     * characteristics of the currently selected row.
     *
     * @param e the event producing the selection change
     */
    public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) { // IMPORTANT!! change that might break a lot of things
            return;
        }
        if (atm.isResorting)
            return;
        ListSelectionModel lsm = (ListSelectionModel) e.getSource();
        // send the message to the component mediator
        // this listener is associated with
        if (lsm.isSelectionEmpty()) {
            atm.handleNoSelection();
        } else {
            int sel = lsm.getMinSelectionIndex();
            if (sel < atm.getSize())
                atm.handleSelection(sel);
            // otherwise it's a java bug for letting us select something
            // that doesn't exist.
        }
    }
}
