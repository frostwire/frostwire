package com.limegroup.gnutella.gui.tables;

import java.awt.*;

public interface ColoredCell {
    Object getValue();

    Color getColor();

    Class<?> getCellClass();
}

