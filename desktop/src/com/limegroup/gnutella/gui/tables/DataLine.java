
package com.limegroup.gnutella.gui.tables;

/**
 * The basic interface through which datalines should be accessed
 * @author Sam Berlin
 */

public interface DataLine<E> {

    /**
     * Return the number of columns this dataline controls.
     */
    public int getColumnCount();

    /**
     * Return the LimeTableColumn for this column.
     */
    public LimeTableColumn getColumn(int col);

    /**
     * Returns whether or not this column can change on subsequent
     * updates to the DataLine
     */
    public boolean isDynamic(int col);
    
    /**
     * Returns whether or not this column can be clipped (and should
     * display a tooltip of the full data if it is).
     */
    public boolean isClippable(int col);

    /**
     * Set up a new DataLine with o
     */
    public void initialize(E o);

    /**
     * Get the object that initialized the DataLine
     */
    public E getInitializeObject();

    /**
     * Reset the object that initialized the DataLine
     */
    public void setInitializeObject(E o);

    /**
     * Get the value of a column in the DataLine
     */
    public Object getValueAt(int col);

    /**
     * Set a value in a column of the DataLine
     */
    public void setValueAt(Object o, int col);
    
    /**
     * Gets the 'type ahead' column.
     */
    public int getTypeAheadColumn();

    /**
     * Cleanup any of the underlying data referenced by the DataLine
     */
    public void cleanup();

    /**
     * Update the cached info in the DataLine
     */
    public void update();

    /**
     * Gets the tooltip for this line
     */
    public String[] getToolTipArray(int col);
    
    /** Determines if a tooltip is required. */
    public boolean isTooltipRequired(int col);

}