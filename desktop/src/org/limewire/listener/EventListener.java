package org.limewire.listener;

/**
 * A listener for a given kind of event.
 * <p>
 * The annotations {@link SwingEDTEvent} and {@link BlockingEvent} can be added
 * to implementations of {@link EventListener#handleEvent(Object)} in order to 
 * allow those events to be dispatched on the EDT thread or a new thread.
 * <p>
 * If classes want to delegate implementations of {@link EventListener}, it is
 * important that the delegate listener's <code>handleEvent(E)</code> method
 * is called via {@link EventListenerList#dispatch(EventListener, Object)}.  This
 * ensures that the event is dispatched appropriately, according to the 
 * annotation on the delegate listener.
 */
public interface EventListener<E> {
    
    // DO NOT CHANGE THIS METHOD NAME WITHOUT CHANGING EventListenerList's annotation inspection
    /** Notification that an event has occurred and should be handled. */
    public void handleEvent(E event); 

}
