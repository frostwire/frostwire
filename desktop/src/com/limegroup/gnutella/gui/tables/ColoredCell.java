package com.limegroup.gnutella.gui.tables;

import java.awt.*;

interface ColoredCell {
    Object getValue();

    Color getColor();

    Class<?> getCellClass();
}

