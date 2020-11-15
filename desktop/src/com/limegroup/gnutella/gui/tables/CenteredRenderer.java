package com.limegroup.gnutella.gui.tables;

import javax.swing.*;

/**
 * Simple renderer that centers the data.
 */
public final class CenteredRenderer extends DefaultTableBevelledCellRenderer {
    /**
     *
     */
    private static final long serialVersionUID = 4600574816511326644L;

    public CenteredRenderer() {
        super();
        setHorizontalAlignment(JLabel.CENTER);
    }
}