
package com.limegroup.gnutella.gui.tables;

import java.util.Comparator;

import javax.swing.table.TableModel;

/**
 * Interface for the model of a DataLineTable
 * @author Sam Berlin
 */
public interface DataLineModel<T extends DataLine<E>, E> extends Comparator<T>, TableModel {

    /**
     * Whether or not the underlying data is sorted.
     */
    public boolean isSorted();

    /**
     * Returns whether or not the underlying data is sorted ascending.
     */
    public boolean isSortAscending();

    /**
     * Returns the column by which the underlying data is sorted ascending.
     */
    public int getSortColumn();

    /**
     * Sort the underlying data by the column.
     */
    public void sort(int col);

    /**
     * Whether or not the underlying data needs to be resorted.
     */
    public boolean needsResort();

    /**
     * Resorts the underlying data.
     */
    public void resort();

    /**
     * Clear the table of all data.
     */
    public void clear();

    /**
     * Refresh the data's info.
     */
    public Object refresh();

    /**
     * Update a specific DataLine.
     * The dataline updated is one that was initialized by Object o.
     * Should return the row of the DataLine updated.
     */
    public int update(E o);
    
    /**
     * Fires an update event for the table.
     */
    public void fireTableDataChanged();    

    /**
     * Add a new DataLine to the info, initialized by o.
     * Return the row it was added at.
     */
    public int add(E o);

    /**
     * Adds a new DataLine to the info, initialized by o.
     * Added to whatever row will keep the DataLine sorted.
     * Return the row it was added at.
     */
    public int addSorted(E o);

    /**
     * Adds a new DataLine to the info.
     * Return the row it was added at.
     */
    public int add(T dl);

    /**
     * Adds a new DataLine to the model in whatever row will keep
     * the DataLine sorted.
     * Return the row it was added at.
     */
    public int addSorted(T dl);

    /**
     * Add a new DataLine to the info, at a specific row initialized by o.
     * Return the row it was added at.
     */
    public int add(E o, int row);

    /**
     * Adds a new DataLine to the info at a specific row.
     * Return the row it was added at.
     */
    public int add(T dl, int row);

    /**
     * Get the DataLine associated with the row.
     */
    public T get(int row);

    /**
     * Gets the DataLine that was initialized by Object o.
     */
    public T get(E o);

    /**
     * Gets the first DataLine that has Object o in column col.
     */
    public T get(Object o, int col);

    /**
     * Remove a row from the data.
     */
    public void remove(int row);

    /**
     * Remove the row associated with the DataLine 'line'.
     */
    public void remove(T line);

    /**
     * Remove the row that was initialized by Object 'o'.
     */
    public void remove(Object o);

    /**
     * Determine if the list contains Object o in column col.
     */
    public boolean contains(Object o, int col);

    /**
     * Determine if the list contains a row that was initialized by Object o.
     */
    public boolean contains(Object o);

    /**
     * Get the row of the row that contains Object o in column col.
     */
    public int getRow(Object o, int col);

    /**
     * Get the index of the DataLine that was initialized by Object o.
     */
    public int getRow(E o);

    /**
     * Get the index of this DataLine.
     */
    public int getRow(T dl);

    /**
     * Gets the tooltip for a specific row.
     */
    public String[] getToolTipArray(int row, int col);
    
    /** Determines if a tooltip must show, regardless of the table settings. */
    public boolean isTooltipRequired(int row, int col);
    
    /**
     * Determines if the specified column can be clipped.
     * This is generally true for all text columns.
     */
    public boolean isClippable(int col);

    /**
     * Gets the LimeTableColumn for this column.
     * A LimeTableColumn encapsulates access to the columnId,
     * columnName, etc...
     */
    public LimeTableColumn getTableColumn(int col);

    /**
     * Gets the id of the specified column.
     */
    public Object getColumnId(int col);
    
    /**
     * Gets the 'type ahead' column.
     */
    public int getTypeAheadColumn();

}
