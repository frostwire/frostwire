package org.limewire.collection;

/**
 * Defines the interface for passing a type as an argument to a method, with a 
 * return value of a type. 
 */
public interface Function<I, O> {
    
    /**  Applies this function to argument, returning the result. */
    public O apply(I argument);
}
