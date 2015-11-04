
package com.limegroup.gnutella.gui.tables;

// simple interface to designate a table as sortable.
public interface JSortTable {

    public int getSortedColumnIndex();
    public boolean isSortedColumnAscending();
	public int getPressedColumnIndex();
}
