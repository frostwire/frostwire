/*
 * FixedSizeForgetfulHashMap.java
 *
 * Created on December 11, 2000, 2:08 PM
 */

package org.limewire.collection;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

/**
 * Provides a better-defined replacement policy version of
 * Like <code>ForgetfulHashMap</code>, this is a
 * mapping that "forgets" keys and values using a FIFO replacement policy, much
 * like a cache.
 * <p>
 * Specifically, <code>FixedsizeForgetfulHashMap</code> allows a
 * key to be re-mapped to a different value and then "renews" this key so it
 * is the last key to be replaced (done in constant time).
 * <pre>
 * FixedsizeForgetfulHashMap&lt;String, String&gt; ffhm =
 * new FixedsizeForgetfulHashMap&lt;String, String&gt;(3);
 *
 * ffhm.put("myKey1", "Abby");
 * ffhm.put("myKey2", "Bob");
 * ffhm.put("myKey3", "Chris");
 * System.out.println(ffhm);
 *
 * ffhm.put("myKey4", "Dan");
 * System.out.println(ffhm);
 *
 * ffhm.put("myKey3", "replace");
 * System.out.println(ffhm);
 *
 * Output:
 * {myKey1=Abby, myKey2=Bob, myKey3=Chris}
 * {myKey2=Bob, myKey3=Chris, myKey4=Dan}
 * {myKey2=Bob, myKey4=Dan, myKey3=replace}
 * </pre>
 *
 * @author Anurag Singla -- initial version
 * @author Christopher Rohrs -- cleaned up and added unit tests
 * @author Sam Berlin -- extend LinkedHashMap (adds unimplemented methods, simplifies)
 */
public class FixedsizeForgetfulHashMap<K, V> extends LinkedHashMap<K, V> {
    /**
     *
     */
    private static final long serialVersionUID = -519304540549432803L;
    /**
     * Maximum number of elements to be stored in the underlying hashMap
     */
    private final int MAXIMUM_SIZE;

    /**
     * Create a new instance that holds only the last "size" entries.
     *
     * @param size the number of entries to hold
     * @throws IllegalArgumentException if size is less < 1.
     */
    public FixedsizeForgetfulHashMap(int size) {
        this(size, (size * 4) / 3 + 10, 0.75f);
    }

    /**
     * Create a new instance that holds only the last "size" entries,
     * using the given initialCapacity and a loadFactor of 0.75.
     *
     * @param size the number of entries to hold
     * @throws IllegalArgumentException if size is less < 1.
     */
    FixedsizeForgetfulHashMap(int size, int initialCapacity) {
        this(size, initialCapacity, 0.75f);
    }

    /**
     * Create a new instance that holds only the last "size" entries, using
     * the given initialCapacity & loadFactor.
     *
     * @param size the number of entries to hold
     * @throws IllegalArgumentException if size is less < 1.
     */
    private FixedsizeForgetfulHashMap(int size, int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor);
        //if size is < 1
        if (size < 1)
            throw new IllegalArgumentException("invalid size: " + size);
        //set the max size to the size specified
        MAXIMUM_SIZE = size;
    }

    /**
     * Tests if the map is full
     *
     * @return true, if the map is full (ie if adding any other entry will
     * lead to removal of some other entry to maintain the fixed-size property
     * of the map. Returns false, otherwise
     */
    public boolean isFull() {
        return size() >= MAXIMUM_SIZE;
    }

    /**
     * Returns a shallow copy of this Map instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map.
     */
    @SuppressWarnings("unchecked")
    public FixedsizeForgetfulHashMap<K, V> clone() {
        return (FixedsizeForgetfulHashMap<K, V>) super.clone();
    }

    /**
     * Returns true if the eldest entry should be removed.
     */
    @Override
    protected boolean removeEldestEntry(Entry<K, V> eldest) {
        return size() > MAXIMUM_SIZE;
    }

    /**
     * Overridden to ensure that remapping a key renews the value in the
     * linked list.
     */
    @Override
    public V put(K key, V value) {
        V ret = null;
        if (containsKey(key))
            ret = remove(key);
        super.put(key, value);
        return ret;
    }
}


