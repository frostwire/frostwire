package com.limegroup.gnutella.gui.tables;

import java.awt.Component;

import javax.swing.JTable;

import com.limegroup.gnutella.gui.GUIUtils;

/**
 * Simple renderer for speeds.
 */
public final class SpeedRenderer extends DefaultTableBevelledCellRenderer {
	
	/**
     * 
     */
    private static final long serialVersionUID = -3181771818657704593L;

    /**
	 * Constructs the speed drawer.
	 */
	public SpeedRenderer() {
	    super();
	}

	/**
	 * Draws a speed.
	 */
	public Component getTableCellRendererComponent
		(JTable table,Object value,boolean isSelected,
		 boolean hasFocus,int row,int column) {
		    
        Double d = (Double)value;
        String s = "";
        if(d != null) {
            double dd = d.doubleValue();
            if( dd != -1 )
                s = GUIUtils.rate2speed(dd);
        }

        return super.getTableCellRendererComponent(
            table, s, isSelected, hasFocus, row, column);
	}
}
