package com.limegroup.gnutella.gui.dnd;

import java.awt.*;
import java.awt.datatransfer.Transferable;

/**
 * Simple class that exposes information about a drop.
 * 
 * TODO dnd: When using Java 1.6, this isn't going to be necessary because
 *       of their DropSupport class.
 */
public interface DropInfo {
    Transferable getTransferable();
    int getDropAction();
    void setDropAction(int newAction);
    Point getPoint();
}
