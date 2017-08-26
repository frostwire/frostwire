package com.limegroup.gnutella.gui.tables;

import java.awt.*;

public interface ColoredCell {
    public Object getValue();
    public Color getColor();
    public Class<?> getCellClass();
}

