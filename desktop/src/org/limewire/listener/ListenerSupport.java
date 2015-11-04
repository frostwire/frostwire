package org.limewire.listener;

/**
 * Describes an interface to allow objects to add and remove listeners for
 * certain events.
 */
public interface ListenerSupport<E> {

    /** Adds the listener. */
    public void addListener(EventListener<E> listener);

    /** Returns true if the listener was removed. */
    public boolean removeListener(EventListener<E> listener);

}
