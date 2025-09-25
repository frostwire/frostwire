package org.limewire.collection;

import java.util.Map;
import java.util.SortedMap;

/**
 * Defines the interface for a prefix tree, an ordered tree data structure. For
 * more information, see <a href= "http://en.wikipedia.org/wiki/Trie">Tries</a>.
 *
 * @author Roger Kapsi
 * @author Sam Berlin
 */
public interface Trie<K, V> extends SortedMap<K, V> {
    /**
     * Returns a view of this Trie of all elements that are
     * prefixed by the given key.
     * <p>
     * In a fixed-keysize Trie, this is essentially a 'get' operation.
     * <p>
     * For example, if the Trie contains 'Lime', 'LimeWire',
     * 'LimeRadio', 'Lax', 'Later', 'Lake', and 'Lovely', then
     * a lookup of 'Lime' would return 'Lime', 'LimeRadio', and 'LimeWire'.
     */
    SortedMap<K, V> getPrefixedBy(K key);

    /**
     * Returns a view of this Trie of all elements that are
     * prefixed by the length of the key.
     * <p>
     * Fixed-keysize Tries will not support this operation
     * (because all keys will be the same length).
     * <p>
     * For example, if the Trie contains 'Lime', 'LimeWire',
     * 'LimeRadio', 'Lax', 'Later', 'Lake', and 'Lovely', then
     * a lookup of 'LimePlastics' with a length of 4 would
     * return 'Lime', 'LimeRadio', and 'LimeWire'.
     */
    SortedMap<K, V> getPrefixedBy(K key, int length);

    /**
     * Returns a view of this Trie of all elements that are prefixed
     * by the key, starting at the given offset and for the given length.
     * <p>
     * Fixed-keysize Tries will not support this operation
     * (because all keys are the same length).
     * <p>
     * For example, if the Trie contains 'Lime', 'LimeWire',
     * 'LimeRadio', 'Lax', 'Later', 'Lake', and 'Lovely', then
     * a lookup of 'The Lime Plastics' with an offset of 4 and a
     * length of 4 would return 'Lime', 'LimeRadio', and 'LimeWire'.
     */
    SortedMap<K, V> getPrefixedBy(K key, int offset, int length);

    /**
     * Returns a view of this Trie of all elements that are prefixed
     * by the number of bits in the given Key.
     * <p>
     * Fixed-keysize Tries can support this operation as a way to do
     * lookups of partial keys.  That is, if the Trie is storing IP
     * addresses, you can lookup all addresses that begin with
     * '192.168' by providing the key '192.168.X.X' and a length of 16
     * would return all addresses that begin with '192.168'.
     */
    SortedMap<K, V> getPrefixedByBits(K key, int bitLength);

    /**
     * Returns the value for the entry whose key is closest in a bitwise
     * XOR metric to the given key.  This is NOT lexicographic closeness.
     * For example, given the keys:<br>
     * D = 1000100 <br>
     * H = 1001000 <br>
     * L = 1001100 <br>
     * <p>
     * If the Trie contained 'H' and 'L', a lookup of 'D' would return 'L',
     * because the XOR distance between D and L is smaller than the XOR distance
     * between D and H.
     */
    V select(K key);

    /**
     * Iterates through the Trie, starting with the entry whose bitwise
     * value is closest in an XOR metric to the given key.  After the closest
     * entry is found, the Trie will call select on that entry and continue
     * calling select for each entry (traversing in order of XOR closeness,
     * NOT lexicographically) until the cursor returns
     * <code>Cursor.SelectStatus.EXIT</code>.<br>
     * The cursor can return <code>Cursor.SelectStatus.CONTINUE</code> to
     * continue traversing.<br>
     * <code>Cursor.SelectStatus.REMOVE_AND_EXIT</code> is used to remove the current element
     * and stop traversing.
     * <p>
     * Note: The {@link Cursor.SelectStatus#REMOVE} operation is not supported.
     *
     * @return The entry the cursor returned EXIT on, or null if it continued
     * till the end.
     */
    Map.Entry<K, V> select(K key, Cursor<? super K, ? super V> cursor);

    /**
     * Traverses the Trie in lexicographical order. <code>Cursor.select</code>
     * will be called on each entry.<p>
     * The traversal will stop when the cursor returns <code>Cursor.SelectStatus.EXIT</code>.<br>
     * <code>Cursor.SelectStatus.CONTINUE</code> is used to continue traversing.<br>
     * <code>Cursor.SelectStatus.REMOVE</code> is used to remove the element that was
     * selected and continue traversing.<br>
     * <code>Cursor.SelectStatus.REMOVE_AND_EXIT</code> is used to remove the current element
     * and stop traversing.
     *
     * @return The entry the cursor returned EXIT on, or null if it continued
     * till the end.
     */
    Map.Entry<K, V> traverse(Cursor<? super K, ? super V> cursor);

    /**
     * An interface used by a {@link Trie}. A {@link Trie} selects items by
     * closeness and passes the items to the <code>Cursor</code>. You can then
     * decide what to do with the key-value pair and the return value
     * from {@link #select(java.util.Map.Entry)} tells the <code>Trie</code>
     * what to do next.
     * <p>
     * <code>Cursor</code> returns status/selection status might be:
     * | Return Value    | Status                                    |
     * | --------------- | ----------------------------------------- |
     * | EXIT            | Finish the Trie operation                 |
     * | CONTINUE        | Look at the next element in the traversal |
     * | REMOVE_AND_EXIT | Remove the entry and stop iterating       |
     * | REMOVE          | Remove the entry and continue iterating   |
     * <p>
     * Note: {@link Trie#select(Object, org.limewire.collection.Trie.Cursor)} does
     * not support <code>REMOVE</code>.
     *
     * @param <K> Key Type
     * @param <V> Key Value
     */
    interface Cursor<K, V> {
        /**
         * Notification that the Trie is currently looking at the given entry.
         * Return <code>EXIT</code> to finish the Trie operation,
         * <code>CONTINUE</code> to look at the next entry, <code>REMOVE</code>
         * to remove the entry and continue iterating, or
         * <code>REMOVE_AND_EXIT</code> to remove the entry and stop iterating.
         * Not all operations support <code>REMOVE</code>.
         */
        SelectStatus select(Map.Entry<? extends K, ? extends V> entry);

        /**
         * The mode during selection.
         */
        enum SelectStatus {
            EXIT, CONTINUE, REMOVE, REMOVE_AND_EXIT
        }
    }
}

