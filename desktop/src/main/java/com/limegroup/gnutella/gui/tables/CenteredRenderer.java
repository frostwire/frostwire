package com.limegroup.gnutella.gui.tables;

import javax.swing.*;

/**
 * Simple renderer that centers the data.
 */
public final class CenteredRenderer extends DefaultTableBevelledCellRenderer {
    /**
     *
     */

    public CenteredRenderer() {
        super();
        setHorizontalAlignment(JLabel.CENTER);
    }
}