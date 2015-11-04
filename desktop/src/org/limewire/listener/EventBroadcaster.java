package org.limewire.listener;

/**
 * Allows an Event to be broadcast.
 */
public interface EventBroadcaster<E> {
    
    /** Broadcasts this event to anyone listening. */
    public void broadcast(E event);

}
