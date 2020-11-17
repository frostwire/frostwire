/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2020, FrostWire(R). All rights reserved.
 *
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
 * @author AholicKnight
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
            sourceIcons.put("magnetdl", GUIMediator.getThemeImage("magnetdl_source"));
            sourceIcons.put("default", GUIMediator.getThemeImage("seeding_small_source"));
            sourceIcons.put("1337x", GUIMediator.getThemeImage("1337_source")); // Thank you AholicKnight for the icon.
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
