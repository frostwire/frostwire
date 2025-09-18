/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.tables;

import java.util.HashMap;
import java.util.Objects;

/**
 * This class extends the BasicDataLineModel
 * by storing the 'initializing' object in a HashMap.
 * Tables which need quick access to rows based on the
 * initializing object should use this as the underlying TableModel.
 */
public class HashBasedDataLineModel<T extends DataLine<E>, E> extends BasicDataLineModel<T, E> {
    /**
     *
     */
    private static final long serialVersionUID = -4697217283217173076L;
    /**
     * HashMap for quick access to indexes.
     */
    private final HashMap<E, Integer> _indexes = new HashMap<>();

    /**
     * Constructor -- this HashBasedDataLineModel supports the
     * the single param constructor of BasicDataLineModel.
     */
    protected HashBasedDataLineModel(Class<? extends T> dataLineClass) {
        super(dataLineClass);
    }

    /**
     * Utility function that immediately calls super.add(dl, row)
     * without checking if it exists.  Useful for extending classes
     * that override add(DataLine, row).
     */
    protected int forceAdd(T dl, int row) {
        synchronized (getListLock()) {
            _indexes.put(dl.getInitializeObject(), row);
        }
        int addedAt = super.add(dl, row);
        synchronized (getListLock()) {
            remapIndexes(addedAt + 1);
        }
        return addedAt;
    }

    /**
     * Override of the add function so we can maintain a HashMap
     * for quick access to the row an object is in.
     */
    public int add(T dl, int row) {
        E init = dl.getInitializeObject();
        synchronized (getListLock()) {
            // If this object is already added, don't add.
            if (_indexes.containsKey(init)) {
                return -1;
            }
            //otherwise, add it to the indexes list
            _indexes.put(init, row);
            int addedAt = super.add(dl, row);
            remapIndexes(addedAt + 1);
            return addedAt;
        }
    }

    /**
     * Overrides the default remove to remove the index from the hashmap.
     *
     * @param row the index of the row to remove.
     */
    public void remove(int row) {
        Object init = get(row).getInitializeObject();
        synchronized (getListLock()) {
            _indexes.remove(init);
        }
        super.remove(row);
        synchronized (getListLock()) {
            remapIndexes(row);
        }
    }

    /**
     * Overrides the default getRow to look in the HashMap instead
     * of a linear search.
     *
     * @param o the object whose index we want.
     * @return the index of the DataLine initialized by object o.
     * @throws ArrayIndexOutOfBoundsException if no dataline was
     *                                        initialized by o.
     */
    public int getRow(E o) {
        synchronized (getListLock()) {
            Integer idx = _indexes.get(o);
            return Objects.requireNonNullElse(idx, -1);
        }
    }

    /**
     * Overrides the default sort to maintain the indexes HashMap,
     * according to the current sort column and order.
     */
    public void doResort() {
        super.doResort();
        synchronized (getListLock()) {
            _indexes.clear(); // it's easier & quicker to just clear & re-input
            remapIndexes(0);
        }
    }

    /**
     * Overrides the default contains to use the HashMap instead
     * of a linear search.
     *
     * @param o The object which initialized a DataLine.
     * @return true if the List contains a DataLine that was initialized
     * by Object o.
     */
    public boolean contains(Object o) {
        synchronized (getListLock()) {
            return _indexes.containsKey(o);
        }
    }

    /**
     * Overrides the default clear to erase the indexes HashMap.
     */
    public void clear() {
        synchronized (getListLock()) {
            _indexes.clear();
        }
        super.clear();
    }

    /**
     * Remaps the indexes, starting at 'start' and going to the end of
     * the list.  This is needed for when rows are added to the middle of
     * the list to maintain the correct rows per objects.
     */
    private void remapIndexes(int start) {
        int end = getRowCount();
        for (int i = start; i < end; i++) {
            _indexes.put(get(i).getInitializeObject(), i);
        }
    }
}
