/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.theme;

import javax.swing.*;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.synth.SynthTableUI;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author gubatron
 * @author aldenml
 */
public class SkinTableUI extends SynthTableUI {
    private MouseInputListener mouseListener;
    private Point lastMousePosition;

    public SkinTableUI() {
    }

    public static ComponentUI createUI(JComponent comp) {
        ThemeMediator.testComponentCreationThreadingViolation();
        return new SkinTableUI();
    }

    public int getRowAtMouse() {
        int row = -1;
        if (lastMousePosition != null) {
            row = table.rowAtPoint(lastMousePosition);
        }
        return row;
    }

    @Override
    protected MouseInputListener createMouseInputListener() {
        if (mouseListener == null) {
            mouseListener = new TableMouseInputListener(super.createMouseInputListener());
        }
        return mouseListener;
    }

    private void repaintAtPoint(Point p) {
        if (p != null) {
            table.repaint(0, p.y - 50, table.getWidth(), 100);
        }
    }

    private class TableMouseInputListener implements MouseInputListener {
        private final MouseInputListener delegate;

        public TableMouseInputListener(MouseInputListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            delegate.mouseClicked(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            delegate.mousePressed(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            delegate.mouseReleased(e);
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            delegate.mouseEntered(e);
        }

        @Override
        public void mouseExited(MouseEvent e) {
            delegate.mouseExited(e);
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            delegate.mouseDragged(e);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            delegate.mouseMoved(e);
            repaintAtPoint(lastMousePosition);
            Point p = e.getPoint();
            lastMousePosition = p;
            repaintAtPoint(p);
        }
    }
}
