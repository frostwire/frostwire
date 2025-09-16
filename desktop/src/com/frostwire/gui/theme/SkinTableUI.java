/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
