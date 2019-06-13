/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.search;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.tables.DefaultTableBevelledCellRenderer;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public class SourceRenderer extends DefaultTableBevelledCellRenderer implements TableCellRenderer {
    private static final Map<String, ImageIcon> sourceIcons = new HashMap<>();

    static {
        try {
            sourceIcons.put("soundcloud", GUIMediator.getThemeImage("soundcloud_off"));
            sourceIcons.put("archive.org", GUIMediator.getThemeImage("archive_source"));
            sourceIcons.put("tpb", GUIMediator.getThemeImage("tpb_source"));
            sourceIcons.put("torlock", GUIMediator.getThemeImage("torlock_off"));
            sourceIcons.put("torrentdownloads", GUIMediator.getThemeImage("torrentdownloads_source"));
            sourceIcons.put("limetorrents", GUIMediator.getThemeImage("limetorrent")); // Thank you trollmad3 for the icon
            sourceIcons.put("eztv", GUIMediator.getThemeImage("eztv_off"));
            sourceIcons.put("zooqle", GUIMediator.getThemeImage("zooqle_source"));
            sourceIcons.put("default", GUIMediator.getThemeImage("seeding_small_source"));
            //TODO icon for torrentz2
        } catch (Throwable e) {
            // just print it
            e.printStackTrace();
        }
    }

    private SourceHolder sourceHolder;

    public SourceRenderer() {
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        initMouseListener();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int columns) {
        setOpaque(true);
        setEnabled(true);
        if (isSelected) {
            setBackground(ThemeMediator.TABLE_SELECTED_BACKGROUND_ROW_COLOR);
        } else {
            setBackground(row % 2 == 1 ? ThemeMediator.TABLE_ALTERNATE_ROW_COLOR : Color.WHITE);
        }
        updateUI((SourceHolder) value, table);
        return super.getTableCellRendererComponent(table, getText(), isSelected, hasFocus, row, columns);
    }

    private void initMouseListener() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (sourceHolder != null) {
                    sourceHolder.getUISearchResult().showSearchResultWebPage(true);
                    e.consume();
                }
            }
        };
        addMouseListener(mouseAdapter);
    }

    private void updateUI(SourceHolder value, JTable table) {
        sourceHolder = value;
        updateIcon();
        updateLinkLabel(table);
    }

    private void updateIcon() {
        if (sourceHolder != null) {
            String sourceName = sourceHolder.getSourceName().toLowerCase();
            if (sourceName.contains("-")) {
                sourceName = sourceName.substring(0, sourceName.indexOf("-")).trim();
            }
            ImageIcon icon = sourceIcons.get(sourceName);
            if (icon != null) {
                setIcon(icon);
            } else {
                setIcon(sourceIcons.get("default"));
            }
        }
    }

    private void updateLinkLabel(JTable table) {
        if (sourceHolder != null) {
            setText(sourceHolder.getSourceNameHTML());
            syncFont(table, this);
        }
    }

    private void syncFont(JTable table, JComponent c) {
        Font tableFont = table.getFont();
        if (tableFont != null && !tableFont.equals(c.getFont())) {
            c.setFont(tableFont);
        }
    }
}
