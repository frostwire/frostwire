/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.library;

import java.io.File;

import com.frostwire.alexandria.PlaylistItem;
import com.limegroup.gnutella.gui.tables.ColoredCellImpl;
import com.limegroup.gnutella.gui.tables.HashBasedDataLineModel;
import com.limegroup.gnutella.gui.tables.SizeHolder;

/**
 * Library specific DataLineModel.
 * Uses HashBasedDataLineModel instead of BasicDataLineModel
 * for quicker access to row's based on the file.
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
final class LibraryPlaylistsTableModel extends HashBasedDataLineModel<LibraryPlaylistsTableDataLine, PlaylistItem> {

	private static final long serialVersionUID = 2859783399965055446L;
    
    LibraryPlaylistsTableModel() {
	    super(LibraryPlaylistsTableDataLine.class);
	}
	
    /**
     * Creates a new LibraryTableDataLine
     */
    public LibraryPlaylistsTableDataLine createDataLine() {
        return new LibraryPlaylistsTableDataLine();
    }

	/**
	 * Override the normal refresh.
	 * Because the DataLine's don't cache any data,
	 * we can just call update & they'll show the correct info
	 * now.
	 */
	public Object refresh() {
	    fireTableRowsUpdated(0, getRowCount());
	    return null;
	}

    /**
     * OVerride default so new ones get added to the end
     */
    @Override
    public int add(PlaylistItem o) {
        return addSorted(o);//, getRowCount());
    }

	/**
	 * Override the dataline add so we can re-initialize files
	 * to include the FileDesc.  Necessary for changing pending status
	 * to shared status.
	 */
//    @Override
//	public int add(LibraryPlaylistsTableDataLine dl, int row) {
//	    File init = dl.getInitializeObject();
//	    if ( !contains(init) ) {
//	        return forceAdd(dl, row);
//	    } else {
//	        FileDesc fd = dl.getFileDesc();
//	        if ( fd != null ) {
//	            row = getRow(init);
//	            get( row ).setFileDesc(fd);
//	            fireTableRowsUpdated( row, row );
//	        }
//	        // we aren't going to use this dl, so clean it up.
//	        dl.cleanup();
//	    }
//	    return -1;
//    }
    
    /**
     * Reinitializes a dataline that is using the given initialize object.
     */
    void reinitialize(File f) {
        if(contains(f)) {
//            int row = getRow(f);
//            get(row).initialize(f);
//            fireTableRowsUpdated(row, row);
        }
    }
    
    /**
     * Reinitializes a dataline from using one file to use another.
     */
    void reinitialize(File old, File now) {
        if(contains(old)) {
//            int row = getRow(old);
//            get(row).initialize(now);
//            initializeObjectChanged(old, now);
//            fireTableRowsUpdated(row, row);
        }
    }

	/**
	 * Returns the file extension for the given row.
	 *
	 * @param row  The row of the file
	 *
	 * @return  A <code>String</code> object containing the file extension
	 */
	String getType(int row) {
	    return (String)(
	             (ColoredCellImpl)get(row).getValueAt(
	                LibraryPlaylistsTableDataLine.TYPE_IDX)).getValue();
	}

	/**
	 * Returns the file object stored in the given row.
	 *
	 * @param row  The row of the file
	 *
	 * @return  The <code>File</code> object stored at the specified row
	 */
	File getFile(int row) {
	    return new File(get(row).getInitializeObject().getFilePath());
	}

 	/**
	 * Returns the name of the file at the given row.
	 *
	 * @param row  The row of the file
	 *
	 * @return  An <code>int</code> containing the size of the file
	 */
	long getSize(int row) {
	    return ((SizeHolder)(
	        (ColoredCellImpl)get(row).getValueAt(
                LibraryPlaylistsTableDataLine.SIZE_IDX)).getValue()).getSize();
    }
	
	/**
	 * Returns a boolean specifying whether or not specific cell in the table
	 * is editable.
	 *
	 * @param row the row of the table to access
	 *
	 * @param col the column of the table to access
	 *
	 * @return <code>true</code> if the specified cell is editable,
	 *         <code>false</code> otherwise
	 */
	public boolean isCellEditable(int row, int col) {
	    return col == LibraryPlaylistsTableDataLine.STARRED_IDX || col == LibraryPlaylistsTableDataLine.ACTIONS_IDX;
	}
}
