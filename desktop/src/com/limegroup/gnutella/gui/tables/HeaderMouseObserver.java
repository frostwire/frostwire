package com.limegroup.gnutella.gui.tables;

import java.awt.*;

/**
 * Simple interface that designates something as handling
 * the interactions to a table header.
 *
 * @author Sam Berlin
 */
interface HeaderMouseObserver {
    void handleHeaderColumnLeftClick(Point p);

    void handleHeaderColumnPressed(Point p);

    void handleHeaderColumnReleased(Point p);

    void handleHeaderPopupMenu(Point p);
}

