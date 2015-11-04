package com.limegroup.gnutella.gui.tables;


import java.awt.Point;

/**
 * Simple interface that designates something as handling
 * the interactions to a table header.
 * @author Sam Berlin
 */
public interface HeaderMouseObserver {
    public void handleHeaderColumnLeftClick(Point p);
    public void handleHeaderColumnPressed(Point p);
    public void handleHeaderColumnReleased(Point p);
    public void handleHeaderPopupMenu(Point p);
}

