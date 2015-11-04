package com.limegroup.gnutella.gui.tables;

import java.util.HashMap;

/** 
 * This class extends the BasicDataLineModel
 * by storing the 'initializing' object in a HashMap.
 * Tables which need quick access to rows based on the
 * initializing object should use this as the underlying TableModel.
 */

//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public class HashBasedDataLineModel<T extends DataLine<E>, E> extends BasicDataLineModel<T, E> {
    
    /**
     * 
     */
    private static final long serialVersionUID = -4697217283217173076L;
    
    /** 
     * HashMap for quick access to indexes.
     */
    protected HashMap<E, Integer> _indexes = new HashMap<E, Integer>();
    
    /**
     * Constructor -- this HashBasedDataLineModel supports the
     * the single param constructor of BasicDataLineModel.
     */
    public HashBasedDataLineModel(Class<? extends T> dataLineClass) {
        super(dataLineClass);
    }
    
    /**
     * Utility function that immediately calls super.add(dl, row)
     * without checking if it exists.  Useful for extending classes
     * that override add(DataLine, row).
     */
    protected int forceAdd(T dl, int row) {
        _indexes.put(dl.getInitializeObject(), new Integer(row));
        int addedAt = super.add(dl, row);
        remapIndexes(addedAt + 1);
        return addedAt;
    }
    
    /**
     * Override of the add function so we can maintain a HashMap
     * for quick access to the row an object is in.
     */
    public int add(T dl, int row) {
        E init = dl.getInitializeObject();
        // If this object is already added, don't add.
        if (_indexes.containsKey(init)) {
            return -1;
        }
        //otherwise, add it to the indexes list
        else {
            _indexes.put(init, new Integer(row));
            int addedAt = super.add(dl, row);
            remapIndexes(addedAt + 1);
            return addedAt;
        }
    }
    
    /** 
     * Overrides the default remove to remove the index from the hashmap.
     *
     * @param row  the index of the row to remove.
     */
    public void remove(int row) {
        Object init = get(row).getInitializeObject();
        _indexes.remove(init);
        super.remove(row);
        remapIndexes(row);
    }
    
    /** 
     * Overrides the default getRow to look in the HashMap instead
     * of a linear search.
     *
     * @param o the object whose index we want.
     * @return the index of the DataLine initialized by object o.
     * @throws ArrayIndexOutOfBoundsException if no dataline was
     *  initialized by o.
     */
    public int getRow(E o) {
        Integer idx = _indexes.get(o);
        if (idx == null) 
            return -1;
        else
            return idx.intValue();
    }
    
    /** 
     * Overrides the default sort to maintain the indexes HashMap,
     * according to the current sort column and order.
     */
    public void doResort() {
        super.doResort();
        _indexes.clear(); // it's easier & quicker to just clear & re-input
        remapIndexes(0);
    }
    
    /**
     * Overrides the default contains to use the HashMap instead
     * of a linear search.
     *
     * @param o The object which initialized a DataLine.
     * @return true if the List contains a DataLine that was initialized
     *  by Object o.
     */
    public boolean contains(Object o) {
        return _indexes.containsKey(o);
    }    
    
    /**
     * Overrides the default clear to erase the indexes HashMap.
     */
    public void clear() {
        _indexes.clear();
        super.clear();
    }
    
    /**
     * Remaps the indexes, starting at 'start' and going to the end of
     * the list.  This is needed for when rows are added to the middle of
     * the list to maintain the correct rows per objects.
     */
    public void remapIndexes(int start) {
        int end = getRowCount();
        for (int i = start; i < end; i++) {
            _indexes.put(get(i).getInitializeObject(), new Integer(i));
        }
    }
    
    /**
     * Notifies the model that the initialize object of a line
     * has changed.
     */
    public void initializeObjectChanged(E old, E now) {
        Integer val = _indexes.remove(old);
        _indexes.put(now, val);
    }
            
}  
