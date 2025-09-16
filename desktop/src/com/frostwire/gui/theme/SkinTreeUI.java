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
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.synth.SynthTreeUI;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * @author gubatron
 * @author aldenml
 */
@SuppressWarnings("unused")
public final class SkinTreeUI extends SynthTreeUI {
    private SkinMouseListener mouseListener;

    public static ComponentUI createUI(JComponent comp) {
        ThemeMediator.testComponentCreationThreadingViolation();
        return new SkinTreeUI();
    }

    @Override
    protected CellRendererPane createCellRendererPane() {
        return new SkinCellRendererPane();
    }

    @Override
    protected MouseListener createMouseListener() {
        if (mouseListener == null) {
            mouseListener = new SkinMouseListener(super.createMouseListener());
        }
        return mouseListener;
    }

    private void paintRowBackground(Graphics g, int x, int y, int w, int h) {
        try {
            TreePath path = getClosestPathForLocation(tree, 0, y);
            int row = treeState.getRowForPath(path);
            boolean selected = tree.isRowSelected(row);
            if (selected) {
                g.setColor(SkinColors.TABLE_SELECTED_BACKGROUND_ROW_COLOR);
            } else if (row % 2 == 1) {
                g.setColor(SkinColors.TABLE_ALTERNATE_ROW_COLOR);
            } else if (row % 2 == 0) {
                g.setColor(Color.WHITE);
            }
            g.fillRect(0, y, tree.getWidth(), h);
        } catch (Throwable e) {
            // just eat, not critical
        }
    }

    private final class SkinCellRendererPane extends CellRendererPane {
        // the following code is copied from the parent method
        @Override
        public void paintComponent(Graphics g, Component c, Container p, int x, int y, int w, int h, boolean shouldValidate) {
            if (c == null) {
                if (p != null) {
                    Color oldColor = g.getColor();
                    g.setColor(p.getBackground());
                    g.fillRect(x, y, w, h);
                    g.setColor(oldColor);
                }
                return;
            }
            if (c.getParent() != this) {
                this.add(c);
            }
            c.setBounds(x, y, w, h);
            if (shouldValidate) {
                c.validate();
            }
            boolean wasDoubleBuffered = false;
            if ((c instanceof JComponent) && c.isDoubleBuffered()) {
                wasDoubleBuffered = true;
                ((JComponent) c).setDoubleBuffered(false);
            }
            paintRowBackground(g, x, y, w, h);
            Graphics cg = g.create(x, y, w, h);
            try {
                c.paint(cg);
            } finally {
                cg.dispose();
            }
            if (wasDoubleBuffered && (c instanceof JComponent)) {
                ((JComponent) c).setDoubleBuffered(true);
            }
            c.setBounds(-w, -h, 0, 0);
        }
    }

    private final class SkinMouseListener implements MouseListener {
        private final MouseListener delegate;

        public SkinMouseListener(MouseListener delegate) {
            this.delegate = delegate;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            delegate.mouseClicked(e);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            TreePath path = getClosestPathForLocation(tree, e.getX(), e.getY());
            if (path != null) {
                tree.setSelectionPath(path);
            }
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
    }
}
