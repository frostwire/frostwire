package com.limegroup.gnutella.gui.tables;

/**
 * A simple callback for table columns, having events for
 * when a column is made visible & when a column is made invisible.
 */
public interface SimpleColumnListener {
    /**
     * Signifies this column was just added to the table.
     */
    public void columnAdded(LimeTableColumn column, LimeJTable table);
    
    /**
     * Signifies this column was just added to the table.
     */
    public void columnRemoved(LimeTableColumn columm, LimeJTable table);
}
