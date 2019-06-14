/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.tables;

import javax.swing.table.AbstractTableModel;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles common tasks associated with storing the DataLine's of a table.
 * Previously, this class used to be split between a DataLineList and a
 * AbstractTableModel.  However, because the function of the DataLineList was
 * really to handle all interactions with the data, it essentially was a model.
 * Now, because the two classes are combined, the model can fire its own events.
 *
 * @author Sam Berlin
 */
public class BasicDataLineModel<T extends DataLine<E>, E> extends AbstractTableModel
        implements DataLineModel<T, E> {
    /**
     *
     */
    private static final long serialVersionUID = -974830504558996194L;
    private static final int ASCENDING = 1;
    private static final int DESCENDING = -1;
    /**
     * Variable to determine which DataLine class
     * to create instances of
     */
    private final Class<? extends T> _dataLineClass;
    /**
     * Variable for the instance of the DataLine that'll be used
     * to determine column length/names/classes.
     */
    private final T _internalDataLine;
    /**
     * Internally used list object storing the DataLines.
     */
    protected final List<T> _list = new ArrayList<>();
    /**
     * Variable for whether or not the current sorting scheme
     * is ascending (value 1) or descending (value -1).
     */
    protected int _ascending = ASCENDING;
    /**
     * Variable for which column is currently being sorted.
     */
    protected int _activeColumn = -1;
    /**
     * Variable for whether or not this list has been sorted
     * at least once.
     */
    private boolean _isSorted = false;

    /*
     * Constructor -- creates the model, tying it to
     * a specific DataLine class.
     */
    protected BasicDataLineModel(Class<? extends T> dataLineClass) {
        _dataLineClass = dataLineClass;
        _internalDataLine = createDataLine();
    }

    //Implements DataLineModel interface
    public String[] getToolTipArray(int row, int col) {
        return _list.get(row).getToolTipArray(col);
    }

    public boolean isTooltipRequired(int row, int col) {
        return _list.get(row).isTooltipRequired(col);
    }

    //Implements DataLineModel interface.
    public boolean isSortAscending() {
        return _ascending == ASCENDING;
    }

    //Implements DataLineModel interface.
    public int getSortColumn() {
        return _activeColumn;
    }

    //Implements DataLineModel interface.
    public boolean isSorted() {
        return _isSorted;
    }

    //Implements DataLineModel interface.
    public void sort(int col) {
        if (col == _activeColumn) {
            if (_ascending == DESCENDING) {
                unsort();
                return;
            } else {
                _ascending = DESCENDING;
            }
        } else {
            _ascending = ASCENDING;
            _activeColumn = col;
        }
        _isSorted = true;
        resort();
    }

    // Re-sort the list to provide real-time sorting
    public void resort() {
        if (_isSorted) {
            doResort();
            fireTableDataChanged();
        }
    }

    /**
     * Stops sorting.
     */
    public void unsort() {
        _isSorted = false;
        _activeColumn = -1;
    }

    /**
     * Implementation of resorting.
     */
    protected void doResort() {
        _list.sort(this);
    }

    /*
     * Determines whether or not the active column is dynamic
     * and needs resorting.
     */
    public boolean needsResort() {
        return _isSorted &&
                _internalDataLine.isDynamic(_activeColumn);
    }

    //Implements DataLineModel interface
    public void clear() {
        cleanup();
        _list.clear();
        fireTableDataChanged();
    }

    //Cleans up all the data lines.
    protected void cleanup() {
        for (T t : _list) {
            t.cleanup();
        }
    }

    /**
     * Basic linear update.
     * Extending classes may wish to override this function to provide
     * a fine-tuned refresh, possibly receiving feedback from each
     * row after it is updated.  The return value can be used to notify
     * the Mediator of information related to the refresh.
     *
     * @return null
     */
    public Object refresh() {
        int end = _list.size();
        for (T t : _list) t.update();
        fireTableRowsUpdated(0, end);
        return null;
    }

    /**
     * Update a specific DataLine
     * The DataLine updated is the one that was initialized by Object o
     */
    public int update(E o) {
        int row = getRow(o);
        _list.get(row).update();
        fireTableRowsUpdated(row, row);
        return row;
    }

    /**
     * Instantiates a DataLine.
     * <p>
     * This uses reflection to create an instance of the DataLine class.
     * The dataLineClass Class is used to determine which class should
     * be created.
     * Failure to create results in AssertFailures.
     * <p>
     * Extending classes should override this to change the way
     * DataLine's are instantiated.
     */
    protected T createDataLine() {
        try {
            return _dataLineClass.getDeclaredConstructor().newInstance();
        } catch (IllegalAccessException | InstantiationException | ClassCastException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns an initialized new dataline.
     */
    private T getNewDataLine(E o) {
        T dl = createDataLine();
        dl.initialize(o);
        return dl;
    }

    /**
     * Determines where the DataLine should be inserted.
     * Runs in log(n) time ArrayLists and linear time for LinkedLists.
     * <p>
     * Extending classes should override this to change the method
     * used to determine where to insert a new DataLine.
     * <p>
     * The current methodology is to use Collections.binarySearch
     * with _list as the list, the DataLine as the key, and this
     * as the Comparator.
     */
    private int getSortedPosition(T dl) {
        // Collections.binarySearch return notes:
        // index of the search key, if it is contained in the list;
        // otherwise, (-(insertion point) - 1). The insertion point
        // is defined as the point at which the key would be inserted
        // into the list: the index of the first element greater than
        // the key, or list.size(), if all elements in the list are
        // less than the specified key. Note that this guarantees that
        // the return value will be >= 0 if & only if the key is found.
        // So....
        // Remember we're comparing columns, not entire DataLines, so
        // it is entirely likely that two columns will be the same.
        // If the returned row is < 0, we want to convert it to
        // the insertion point.
        int row = Collections.binarySearch(_list, dl, this);
        if (row < 0) row = -(row + 1);
        return row;
    }

    /**
     * Helper function.
     * <p>
     * Adds a DataLine initialized by the object to row 0.
     * <p>
     * This should be overriden only if you want the default,
     * non-sorting add to go someplace other than row 0.
     * <p>
     * Delegates to add(Object, int).
     * <p>
     * Extending classes should maintain the delegation to add(Object, int).
     */
    public int add(E o) {
        return add(o, 0);
    }

    /**
     * Helper function.
     * <p>
     * Uses getNewDataLine(Object) and add(DataLine, int).
     * <p>
     * Extending classes can override this, but it is recommended
     * that they override the two above methods instead.
     */
    public int add(E o, int row) {
        T dl = getNewDataLine(o);
        return dl == null ? -1 : add(dl, row);
    }

    /**
     * Adds a DataLine to row 0.
     * Currently unused.
     */
    public int add(T dl) {
        return add(dl, 0);
    }

    /**
     * Adds a DataLine to the list at a row.
     * <p>
     * All forms of add(..) eventually end up here.
     * <p>
     * Extending classes should override this if they want
     * to maintain a HashMap of any type for speedier access.
     */
    public int add(T dl, int row) {
        _list.add(row, dl);
        fireTableRowsInserted(row, row);
        return row;
    }

    /**
     * Helper function.
     * <p>
     * Uses getNewDataLine(Object), getSortedPosition(DataLine),
     * and add(DataLine, int)
     * <p>
     * Extending classes can override this, but it is recommended
     * they override the above mentioned methods instead.
     */
    public int addSorted(E o) {
        T dl = getNewDataLine(o);
        return dl == null ? -1 : add(dl, getSortedPosition(dl));
    }

    /**
     * Helper function.
     * <p>
     * Uses getSortedPosition(DataLine) and add(DataLine, int).
     * <p>
     * Extending classes can override this, but it is recommended
     * they override the above mentioned methods instead.
     */
    public void addSorted(T dl) {
        add(dl, getSortedPosition(dl));
    }

    //Implements the DataLineModel interface.
    public T get(int row) {
        if (row == -1)
            return null;
        else
            return _list.get(row);
    }

    /**
     * Implements DataLineModel interface.
     * Delegates the row find to getRow(Object o).
     * Returns the first DataLine initialized by object o.
     * If no object matches, null is returned.
     */
    public T get(E o) {
        int row = getRow(o);
        if (row != -1)
            return _list.get(row);
        else
            return null;
    }

    /**
     * Implements DataLineModel interface.
     * Delegates the row find to getRow(Object o, int col).
     * Returns the first DataLine that contains object o in column col.
     * If no object matches, null is returned.
     */
    public T get(Object o, int col) {
        int row = getRow(o, col);
        if (row != -1)
            return _list.get(row);
        else
            return null;
    }

    /**
     * Calls cleanup on the DataLine and then removes it from the list.
     */
    public void remove(int row) {
        _list.get(row).cleanup();
        _list.remove(row);
        fireTableRowsDeleted(row, row);
    }

    /**
     * Helper-function that resolves to remove(int).
     * Removes the line associated with the DataLine line.
     * If no matching DataLine exists, nothing happens.
     */
    public void remove(T line) {
        int idx = _list.indexOf(line);
        if (idx != -1)
            remove(idx);
    }

    /**
     * Helper function that resolves to remove(int).
     * Removes the DataLine that was initialized by the Object o.
     * Uses a linear search through the list to find a match.
     * Extending objects that have large lists and call remove(Object)
     * often may wish to override this, add(Object, int) and sort using
     * a HashMap for more timely access.
     */
    public void remove(Object o) {
        int end = _list.size();
        for (int i = 0; i < end; i++) {
            if (_list.get(i).getInitializeObject().equals(o)) {
                remove(i);
                break;
            }
        }
    }

    //Implements the TableModel method
    public Object getValueAt(int row, int col) {
        return _list.get(row).getValueAt(col);
    }

    //Implements the TableModel method
    // Ignores the update if the row doesn't exist.
    public void setValueAt(Object o, int row, int col) {
        if (row >= 0 && row < _list.size()) {
            _list.get(row).setValueAt(o, col);
            fireTableRowsUpdated(row, row);
        }
    }

    /**
     * @return true if List contains the Object o in column col.
     * if a particular column is searched frequently, using a HashMap.
     * The add(Object, int) & sort function should be overriden to initialize
     * the HashMap.
     */
    public boolean contains(Object o, int col) {
        for (T t : _list) {
            if (t.getValueAt(col).equals(o))
                return true;
        }
        return false;
    }

    /**
     * @return true if the List contains a DataLine that was initialized
     * by Object o.
     * if searching is done frequently, using a HashMap.
     * The add(Object, int) & sort function should be overriden to initialize
     * the HashMap.
     */
    public boolean contains(Object o) {
        for (T t : _list) {
            if (t.getInitializeObject().equals(o))
                return true;
        }
        return false;
    }

    /**
     * @return the index of the matching DataLine.
     */
    public int getRow(T dl) {
        return _list.indexOf(dl);
    }

    /**
     * @return the index of the first DataLine that contains Object o
     * in column col.
     * if a particular column is searched frequently, using a HashMap.
     * The add(Object, int) & sort function should be overriden to initialize
     * the HashMap.
     */
    public int getRow(Object o, int col) {
        int end = _list.size();
        for (int i = 0; i < end; i++) {
            if (_list.get(i).getValueAt(col).equals(o))
                return i;
        }
        return -1;
    }

    /**
     * @return the index of the first DataLine that was initialized by Object o.
     * if searching is done frequently, using a HashMap.
     * The add(Object, int) & sort function should be overriden to initialize
     * the HashMap.
     */
    public int getRow(E o) {
        int end = _list.size();
        for (int i = 0; i < end; i++) {
            if (_list.get(i).getInitializeObject().equals(o))
                return i;
        }
        return -1;
    }

    /**
     * A generic compare function.
     */
    public int compare(T a, T b) {
        Object o1 = a.getValueAt(_activeColumn);
        Object o2 = b.getValueAt(_activeColumn);
        return AbstractTableMediator.compare(o1, o2) * _ascending;
    }

    /**
     * Returns the LimeTableColumn at the specific column this data line.
     */
    public LimeTableColumn getTableColumn(int col) {
        if (_internalDataLine == null)
            return null;
        else
            return _internalDataLine.getColumn(col);
    }

    /**
     * Returns the size of _list.
     */
    public int getRowCount() {
        return _list.size();
    }

    /**
     * Returns the number of columns as specified by the data line.
     */
    public int getColumnCount() {
        if (_internalDataLine == null)
            return 0;
        else
            return _internalDataLine.getColumnCount();
    }

    /**
     * Returns whether or not the specified column is clippable.
     */
    public boolean isClippable(int col) {
        if (_internalDataLine == null)
            return false;
        else
            return _internalDataLine.isClippable(col);
    }

    public int getTypeAheadColumn() {
        if (_internalDataLine == null)
            return -1;
        else
            return _internalDataLine.getTypeAheadColumn();
    }

    /**
     * Returns the name of the TableColumn as specified by the data line.
     */
    public String getColumnName(int col) {
        return getTableColumn(col).getName();
    }

    /**
     * Returns the Id of the TableColumn as specified by the data line.
     */
    public Object getColumnId(int col) {
        return getTableColumn(col).getIdentifier();
    }

    /**
     * Returns the class of the TableColumn as specified by the data line.
     */
    public Class<?> getColumnClass(int col) {
        return getTableColumn(col).getColumnClass();
    }
}
    