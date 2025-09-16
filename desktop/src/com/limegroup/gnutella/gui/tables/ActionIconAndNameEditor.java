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

package com.limegroup.gnutella.gui.tables;

import com.frostwire.util.Logger;

import javax.swing.*;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author gubatron
 * @author aldenml
 */
public class ActionIconAndNameEditor extends AbstractCellEditor implements TableCellEditor {
    private static final long serialVersionUID = 2661028644256459921L;
    private static final Logger LOG = Logger.getLogger(ActionIconAndNameEditor.class);
    private final Rectangle actionRegion;
    private ActionListener action;

    private ActionIconAndNameEditor(Rectangle actionRegion) {
        this.actionRegion = actionRegion;
    }

    private ActionIconAndNameEditor() {
        this(null);
    }

    public Object getCellEditorValue() {
        return null;
    }

    public Component getTableCellEditorComponent(final JTable table, Object value, boolean isSelected, int row, int column) {
        ActionIconAndNameHolder in = (ActionIconAndNameHolder) value;
        action = in.getAction();
        final Component component = new ActionIconAndNameRenderer().getTableCellRendererComponent(table, value, isSelected, true, row, column);
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    if (actionRegion == null) {
                        component_mousePressed(e);
                    } else {
                        if (actionRegion.contains(e.getPoint())) {
                            component_mousePressed(e);
                        } else {
                            if (e.getClickCount() >= 2) {
                                Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new MouseEvent(table, MouseEvent.MOUSE_CLICKED, e.getWhen(), e.getModifiersEx(), component.getX() + e.getX(), component.getY() + e.getY(), e.getClickCount(), false));
                            }
                        }
                    }
                } else if (e.getButton() == MouseEvent.BUTTON3) {
                    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new MouseEvent(table, e.getID(), e.getWhen(), e.getModifiersEx(), component.getX() + e.getX(), component.getY() + e.getY(), e.getClickCount(), true));
                }
            }
        });
        return component;
    }

    private void component_mousePressed(MouseEvent e) {
        if (action != null) {
            try {
                action.actionPerformed(new ActionEvent(e.getSource(), e.getID(), ""));
            } catch (Throwable e1) {
                LOG.error("Error performing action", e1);
            }
        }
    }
}
