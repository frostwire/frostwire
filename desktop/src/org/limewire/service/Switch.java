package org.limewire.service;

/**
 * Defines the interface for a class to get and set a boolean value.
 */
public interface Switch {
    
    /** Returns the current value of the switch. */
    public boolean getValue();
    
    /** Sets the new value of the switch. */
    public void setValue(boolean b);

}
