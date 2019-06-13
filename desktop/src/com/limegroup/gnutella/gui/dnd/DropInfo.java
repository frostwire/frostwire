package com.limegroup.gnutella.gui.dnd;

import java.awt.*;
import java.awt.datatransfer.Transferable;

/**
 * Simple class that exposes information about a drop.
 */
public interface DropInfo {
    Transferable getTransferable();

    Point getPoint();
}
