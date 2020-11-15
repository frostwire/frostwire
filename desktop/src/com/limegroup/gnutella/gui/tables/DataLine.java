package com.limegroup.gnutella.gui.tables;

/**
 * The basic interface through which datalines should be accessed
 *
 * @author Sam Berlin
 */
public interface DataLine<E> {
    /**
     * Return the number of columns this dataline controls.
     */
    int getColumnCount();

    /**
     * Return the LimeTableColumn for this column.
     */
    LimeTableColumn getColumn(int col);

    /**
     * Returns whether or not this column can change on subsequent
     * updates to the DataLine
     */
    boolean isDynamic(@SuppressWarnings("unused") int col);

    /**
     * Returns whether or not this column can be clipped (and should
     * display a tooltip of the full data if it is).
     */
    boolean isClippable(int col);

    /**
     * Set up a new DataLine with o
     */
    void initialize(E o);

    /**
     * Get the object that initialized the DataLine
     */
    E getInitializeObject();

    /**
     * Reset the object that initialized the DataLine
     */
    @SuppressWarnings("unused")
    void setInitializeObject(E o);

    /**
     * Get the value of a column in the DataLine
     */
    Object getValueAt(int col);

    /**
     * Set a value in a column of the DataLine
     */
    void setValueAt(Object o, int col);

    /**
     * Gets the 'type ahead' column.
     */
    int getTypeAheadColumn();

    /**
     * Cleanup any of the underlying data referenced by the DataLine
     */
    void cleanup();

    /**
     * Update the cached info in the DataLine
     */
    void update();

    /**
     * Gets the tooltip for this line
     */
    String[] getToolTipArray(@SuppressWarnings("unused") int col);

    /**
     * Determines if a tooltip is required.
     */
    boolean isTooltipRequired(@SuppressWarnings("unused") int col);
}