
package com.limegroup.gnutella.gui.tables;

import javax.swing.JComponent;

import com.limegroup.gnutella.gui.RefreshListener;

/**
 * A common interface for GUI components
 * Allows adding/removing objects, and supports
 * imbedding buttons that are 'attached' to the information.
 * @author Sam Berlin
 */
public interface ComponentMediator<T> extends RefreshListener, MouseObserver {
    
    /**
     * Signifies an object was added to this component
     */
    public void add(T o);
    
    /**
     * Signifies an object was removed from this component.
     */
    public void remove(T o);
    
    /**
     * Signifies an object in this component was updated.
     */
    public void update(T o);
    
    /**
     * Event for the 'action' key being pressed.
     */
    public void handleActionKey();
    
    /**
     * Returns the underlying component that this Mediator handles
     */
    public JComponent getComponent();
    
    /**
     * Removes whatever is selected from the component
     */
    public void removeSelection();
    
    /**
     * Event for when something (such as a row) is selected.
     */
    public void handleSelection(int row);
    
    /**
     * Event for when nothing is selected.
     */
    public void handleNoSelection();
    
    /**
     * Handles setting/unsetting  the status of the buttons
     * that this Mediator controls.
     */
    public void setButtonEnabled(int buttonIdx, boolean enabled);
}
    
    
    
    
    
    