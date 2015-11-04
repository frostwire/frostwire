package com.limegroup.gnutella.gui.dnd;

import java.awt.Point;
import java.awt.datatransfer.Transferable;

/**
 * Simple class that exposes information about a drop.
 * 
 * TODO dnd: When using Java 1.6, this isn't going to be necessary because
 *       of their DropSupport class.
 */
public interface DropInfo {
    public Transferable getTransferable();
    public int getDropAction();
    public void setDropAction(int newAction);
    public Point getPoint();
}
