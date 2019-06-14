package org.limewire.collection;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Provides a set-like interface designed specifically for <code>String</code>s.
 * Uses a Trie as the backing map and provides an implementation specific to
 * <code>String</code>s. Has the same retrieval/insertion times as the backing
 * Trie. Stores the value as the string, for easier retrieval.
 * The goal is to efficiently find Strings that can branch off a prefix.
 * <p>
 * Primarily designed as an {@link AutoCompleteDictionary}.
 * <p>
 * See <a href="http://en.wikipedia.org/wiki/Trie">Trie</a> for more information.
 * <p>
 *
 * @modified David Soh (yunharla00@hotmail.com)
 * 1. added getIterator() & getIterator(String) for enhanced AutoCompleteTextField use.
 * 2. disallowed adding duplicates
 */
public class StringTrieSet implements AutoCompleteDictionary, Iterable<String> {
    /**
     * The backing map. A binary-sorted Trie.
     */
    private final transient StringTrie<String> map;

    /**
     * This constructor sets up a dictionary where case IS significant
     * but whose sort order is binary based.
     * All Strings are stored with the case of the last entry added.
     */
    public StringTrieSet(boolean caseSensitive) {
        map = new StringTrie<>(caseSensitive);
    }

    /**
     * Adds a value to the set.  Different letter case of values is always
     * kept and significant.  If the TrieSet is made case-insensitive,
     * it will not store two Strings with different case but will update
     * the stored values with the case of the last entry.
     */
    public void addEntry(String data) {
        if (!contains(data))    //disallow adding duplicates
            map.add(data, data);
    }

    /**
     * Determines whether or not the Set contains this String.
     */
    private boolean contains(String data) {
        return map.get(data) != null;
    }

    /**
     * Removes a value from the Set.
     *
     * @return <tt>true</tt> if a value was actually removed.
     */
    public boolean removeEntry(String data) {
        return map.remove(data);
    }

    /**
     * Return the last String in the set that can be prefixed by this String
     * (Trie's are stored in alphabetical order).
     * Return null if no such String exist in the current set.
     */
    public String lookup(String data) {
        Iterator<String> it = map.getPrefixedBy(data);
        if (!it.hasNext())
            return null;
        return it.next();
    }

    /**
     * Returns all values (entire TrieSet)
     */
    public Iterator<String> iterator() {
        return map.getIterator();
    }

    /**
     * Returns all potential matches off the given String.
     */
    public Iterator<String> iterator(String s) {
        return map.getPrefixedBy(s);
    }

    /**
     * Clears all items in the dictionary.
     */
    public void clear() {
        List<String> l = new LinkedList<>();
        for (String string : this)
            l.add(string);
        for (String string : l)
            removeEntry(string);
    }
}
