/**
 * Code taken freely from
 * http://www.java-engineer.com/java/auto-complete.html
 */

//------------------------------------------------------------------------------
// Copyright (c) 1999-2001 Matt Welsh.  All Rights Reserved.
//------------------------------------------------------------------------------

package org.limewire.collection;

import java.util.Iterator;

/**
 * Defines the interface for an auto-complete dictionary. Implementations 
 * should perform look ups quickly to avoid delays as the user types. The 
 * interface includes adding, removing and clearing entries from the dictionary.
 * Additionally, <code>AutoCompleteDictionary</code> provides a search and way 
 * to iterate through the dictionary entries.
 * @author Matt Welsh (matt@matt-welsh.com)
 * <br>
 * @modified David Soh (yunharla00@hotmail.com)
 *       added getIterator() & getIterator(String) for enhanced 
 *       AutoCompleteTextField use.
 */
public interface AutoCompleteDictionary extends Iterable<String> {
    /**
     * Adds an entry to the dictionary.
     *
     * @param s The string to add to the dictionary.
     */
    public void addEntry(String s);

    /**
     * Removes an entry from the dictionary.
     *
     * @param s The string to remove to the dictionary.
     * @return True if successful, false if the string is not contained or cannot
     *         be removed.
     */
    public boolean removeEntry(String s);

    /**
     * Perform a lookup and returns the closest matching string to the passed
     * string.
     *
     * @param s The string to use as the base for the lookup. How this routine
     *          is implemented determines the behaviour of the component.
     *          Typically, the closest matching string that completely contains
     *          the given string is returned.
     */
    public String lookup(String s);

    /**
     * Returns all available entries in dictionary
     *
     */
    public Iterator<String> iterator();

    /**
     * Returns an iterator of potential matches from the given string.
     *
     */
    public Iterator<String> iterator(String s);
    
    /**
     * Clears the dictionary.
     */
    public void clear();
}
