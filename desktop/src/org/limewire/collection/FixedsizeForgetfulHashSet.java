package org.limewire.collection;

import java.util.AbstractSet;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;

/**
 * Stores a fixed size of elements as a <code>Set</code> and removes elements
 * when that size is reached. <code>FixedsizeForgetfulHashSet</code> is a
 * <code>Set</code> version of {@link FixedsizeForgetfulHashMap}. Like
 * <code>ForgetfulHashMap</code>, values are "forgotten" using a FIFO replacement
 * policy.
 * <p>
 * <code>FixedsizeForgetfulHashSet</code> works in constant time.
 *
 * <pre>
 * FixedsizeForgetfulHashSet&lt;String&gt; ffhs = new FixedsizeForgetfulHashSet&lt;String&gt;(4);
 *
 * ffhs.add("Abby");
 * System.out.println(ffhs);
 * if(!ffhs.add("Abby"))
 * System.out.println("The set already contained that item; Set contents: " + ffhs);
 *
 * ffhs.add("Bob");
 * ffhs.add("Bob");
 * ffhs.add("Chris");
 *
 * ffhs.add("Dan");
 * System.out.println(ffhs);
 * ffhs.add("Eric");
 * System.out.println(ffhs);
 *
 * Output:
 * [Abby]
 * The set already contained that item; Set contents: [Abby]
 * [Abby, Bob, Chris, Dan]
 * [Bob, Chris, Dan, Eric]
 *
 * </pre>
 */
public class FixedsizeForgetfulHashSet<E> extends AbstractSet<E> implements Set<E>, Cloneable {
    // Dummy value to associate with an Object in the backing Map
    private static final Object PRESENT = new Object();
    /**
     * Backing map which the set delegates.
     */
    private transient FixedsizeForgetfulHashMap<E, Object> map;

    /**
     * Constructs a new, empty set, using the given initialCapacity.
     */
    public FixedsizeForgetfulHashSet(int size, int initialCapacity) {
        map = new FixedsizeForgetfulHashMap<>(size, initialCapacity);
    }

    /**
     * Returns an iterator over the elements in this set.  The elements
     * are returned in no particular order.
     *
     * @return an Iterator over the elements in this set.
     * @see ConcurrentModificationException
     */
    public Iterator<E> iterator() {
        return map.keySet().iterator();
    }

    /**
     * Returns the number of elements in this set (its cardinality).
     *
     * @return the number of elements in this set (its cardinality).
     */
    public int size() {
        return map.size();
    }

    /**
     * Returns <tt>true</tt> if this set contains no elements.
     *
     * @return <tt>true</tt> if this set contains no elements.
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * Returns <tt>true</tt> if this set contains the specified element.
     *
     * @param o element whose presence in this set is to be tested.
     * @return <tt>true</tt> if this set contains the specified element.
     */
    public boolean contains(Object o) {
        return map.containsKey(o);
    }

    /**
     * Adds the specified element to this set if it is not already
     * present.
     *
     * @param o element to be added to this set.
     * @return <tt>true</tt> if the set did not already contain the specified
     * element.
     */
    public boolean add(E o) {
        return map.put(o, PRESENT) == null;
    }

    /**
     * Removes the specified element from this set if it is present.
     *
     * @param o object to be removed from this set, if present.
     * @return <tt>true</tt> if the set contained the specified element.
     */
    public boolean remove(Object o) {
        return map.remove(o) == PRESENT;
    }

    /**
     * Removes all of the elements from this set.
     */
    public void clear() {
        map.clear();
    }

    /**
     * Returns a shallow copy of this <tt>FixedsizeForgetfulHashSet</tt> instance: the elements
     * themselves are not cloned.
     *
     * @return a shallow copy of this set.
     */
    @SuppressWarnings("unchecked")
    public FixedsizeForgetfulHashSet<E> clone() {
        try {
            FixedsizeForgetfulHashSet<E> newSet = (FixedsizeForgetfulHashSet<E>) super.clone();
            newSet.map = map.clone();
            return newSet;
        } catch (CloneNotSupportedException e) {
            throw new InternalError();
        }
    }
}
