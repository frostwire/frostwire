package com.limegroup.gnutella.gui.tables;

/**
 * Encapsulates access to preferences for a table's column.
 */
 public interface ColumnPreferenceHandler {

    /**
     * Reverts this table's header preferences to their default
     * values.
     */
    void revertToDefault();
    
    /**
     * Determines whether or not the columns are already their default values.
     */
    boolean isDefault();

    /**
     * Sets the headers to the correct widths, depending on
     * the user's preferences for this table.
     */
    void setWidths();

    /**
     * Sets the headers to the correct order, depending on the
     * user's preferences for this table.
     */
    void setOrder();

    /**
     * Sets the headers so that some may be invisible, depending
     * on the user's preferences for this table.
     */
    void setVisibility();
    
    /**
     * Sets the single SimpleColumnListener callback.
     */
    void setSimpleColumnListener(SimpleColumnListener scl);
    
}