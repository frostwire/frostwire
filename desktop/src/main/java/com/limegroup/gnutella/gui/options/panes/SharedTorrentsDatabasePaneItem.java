/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.search.relay.LocalIndex;
import com.frostwire.search.relay.LocalIndexTable;
import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchEngine;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public final class SharedTorrentsDatabasePaneItem extends AbstractPaneItem {

    private static final Logger LOG = Logger.getLogger(SharedTorrentsDatabasePaneItem.class);

    private static final String TITLE = I18n.tr("Shared Torrents Database");
    private static final String LABEL = I18n.tr(
            "Browse the contents of your local shared torrents database (LocalIndex). " +
            "Search by name or file path, inspect info hashes and publisher keys, " +
            "and copy magnet links for any shared torrent.");

    private final JLabel TOTAL_LABEL = new JLabel("—");
    private final JLabel DB_SIZE_LABEL = new JLabel("—");
    private final JTextField SEARCH_FIELD = new JTextField(20);
    private final JButton REFRESH_BUTTON = new JButton(I18n.tr("Refresh"));
    private final JButton CLEAR_BUTTON = new JButton(I18n.tr("Clear Search"));
    private final TorrentTableModel TABLE_MODEL = new TorrentTableModel();
    private final JTable TABLE = new JTable(TABLE_MODEL);

    public SharedTorrentsDatabasePaneItem() {
        super(TITLE, LABEL);

        add(buildStatsPanel());
        add(getVerticalSeparator());
        add(buildSearchPanel());
        add(getVerticalSeparator());
        add(buildTablePanel());

        TABLE.setAutoCreateRowSorter(true);
        TABLE.setCellSelectionEnabled(false);
        TABLE.setRowSelectionAllowed(true);
        TABLE.setDefaultEditor(Object.class, null);
        TABLE.addMouseListener(new TableContextMenuListener());

        SEARCH_FIELD.addActionListener(e -> doSearch());
        REFRESH_BUTTON.addActionListener(e -> refresh());
        CLEAR_BUTTON.addActionListener(e -> clearSearch());
    }

    private JPanel buildStatsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 4, 2, 4);

        JLabel totalCaption = new JLabel(I18n.tr("Total shared torrents:"));
        totalCaption.setFont(totalCaption.getFont().deriveFont(Font.BOLD));
        c.gridx = 0; c.gridy = 0; c.weightx = 0;
        panel.add(totalCaption, c);
        c.gridx = 1; c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(TOTAL_LABEL, c);
        c.fill = GridBagConstraints.NONE;

        JLabel sizeCaption = new JLabel(I18n.tr("Database file size:"));
        sizeCaption.setFont(sizeCaption.getFont().deriveFont(Font.BOLD));
        c.gridx = 0; c.gridy = 1; c.weightx = 0;
        panel.add(sizeCaption, c);
        c.gridx = 1; c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(DB_SIZE_LABEL, c);
        c.fill = GridBagConstraints.NONE;

        return panel;
    }

    private JPanel buildSearchPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel(I18n.tr("Search:")));
        panel.add(SEARCH_FIELD);
        panel.add(REFRESH_BUTTON);
        panel.add(CLEAR_BUTTON);
        return panel;
    }

    private JPanel buildTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JScrollPane scroll = new JScrollPane(TABLE);
        scroll.setPreferredSize(new Dimension(700, 320));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static SearchEngine localEngine() {
        return SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.LOCAL_ID);
    }

    private static LocalIndex localIndexOrNull() {
        try {
            return localEngine().getLocalIndex();
        } catch (Throwable t) {
            LOG.warn("Failed to obtain LocalIndex", t);
            return null;
        }
    }

    @Override
    public void initOptions() {
        refresh();
    }

    private void refresh() {
        LocalIndex index = localIndexOrNull();
        if (index == null) {
            TOTAL_LABEL.setText(I18n.tr("Database not initialized"));
            DB_SIZE_LABEL.setText("—");
            TABLE_MODEL.set(Collections.emptyList());
            return;
        }
        try {
            TOTAL_LABEL.setText(index.size() + " " + I18n.tr("torrents"));
        } catch (Throwable t) {
            LOG.warn("LocalIndex.size() failed", t);
            TOTAL_LABEL.setText("—");
        }
        DB_SIZE_LABEL.setText(formatDbSize(index));
        loadTorrentsInBackground(index, null);
    }

    private void doSearch() {
        String query = SEARCH_FIELD.getText();
        if (query == null || query.trim().isEmpty()) {
            refresh();
            return;
        }
        LocalIndex index = localIndexOrNull();
        if (index == null) {
            TOTAL_LABEL.setText(I18n.tr("Database not initialized"));
            TABLE_MODEL.set(Collections.emptyList());
            return;
        }
        loadTorrentsInBackground(index, query.trim());
    }

    private void clearSearch() {
        SEARCH_FIELD.setText("");
        refresh();
    }

    private void loadTorrentsInBackground(LocalIndex index, String query) {
        Thread t = new Thread(() -> {
            List<LocalSharedTorrent> torrents;
            try {
                if (query == null) {
                    torrents = index.listAll();
                } else {
                    torrents = index.search(query, 100);
                }
            } catch (Throwable ex) {
                LOG.warn("Failed to load torrents (query=" + query + ")", ex);
                torrents = Collections.emptyList();
            }
            final List<LocalSharedTorrent> result = torrents;
            SwingUtilities.invokeLater(() -> TABLE_MODEL.set(result));
        }, "SharedTorrentsDBLoader");
        t.setDaemon(true);
        t.start();
    }

    private static String formatDbSize(LocalIndex index) {
        if (index instanceof LocalIndexTable) {
            try {
                return formatSize(((LocalIndexTable) index).sizeInBytes());
            } catch (Throwable t) {
                LOG.warn("sizeInBytes() failed", t);
            }
        }
        return "—";
    }

    private static String formatSize(long bytes) {
        if (bytes < 0) return "—";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        double gb = mb / 1024.0;
        return String.format("%.1f GB", gb);
    }

    private static String formatTimestamp(long seconds) {
        if (seconds <= 0) return "—";
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(seconds * 1000L));
        } catch (Throwable t) {
            return String.valueOf(seconds);
        }
    }

    private static String shortHex(String hex, int max) {
        if (hex == null) return "—";
        return hex.length() <= max ? hex : hex.substring(0, max);
    }

    private static String shortHex(byte[] bytes, int max) {
        if (bytes == null || bytes.length == 0) return "—";
        return shortHex(Hex.encode(bytes), max);
    }

    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    private static String magnetLink(LocalSharedTorrent t) {
        return "magnet:?xt=urn:btih:" + t.infoHashHex();
    }

    private final class TableContextMenuListener extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            showPopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            showPopup(e);
        }

        private void showPopup(MouseEvent e) {
            if (!e.isPopupTrigger()) return;
            int row = TABLE.rowAtPoint(e.getPoint());
            if (row < 0) return;
            TABLE.setRowSelectionInterval(row, row);
            int modelRow = TABLE.convertRowIndexToModel(row);
            LocalSharedTorrent t = TABLE_MODEL.torrentAt(modelRow);
            if (t == null) return;

            JPopupMenu menu = new JPopupMenu();

            JMenuItem copyHash = new JMenuItem(I18n.tr("Copy Info Hash"));
            copyHash.addActionListener(ev -> copyToClipboard(t.infoHashHex()));
            menu.add(copyHash);

            JMenuItem copyMagnet = new JMenuItem(I18n.tr("Copy Magnet Link"));
            copyMagnet.addActionListener(ev -> copyToClipboard(magnetLink(t)));
            menu.add(copyMagnet);

            JMenuItem copyPub = new JMenuItem(I18n.tr("Copy Publisher Key"));
            copyPub.addActionListener(ev -> copyToClipboard(Hex.encode(t.publisherEd25519Pub())));
            menu.add(copyPub);

            menu.show(TABLE, e.getX(), e.getY());
        }
    }

    private static final class TorrentTableModel extends AbstractTableModel {
        private static final String[] COLUMNS = {
                I18n.tr("Name"),
                I18n.tr("Size"),
                I18n.tr("Files"),
                I18n.tr("Info Hash"),
                I18n.tr("Publisher"),
                I18n.tr("Added At"),
                I18n.tr("Last Seen"),
                I18n.tr("Matched File")
        };

        private List<LocalSharedTorrent> rows = new ArrayList<>();

        void set(List<LocalSharedTorrent> torrents) {
            rows = new ArrayList<>(torrents);
            fireTableDataChanged();
        }

        LocalSharedTorrent torrentAt(int row) {
            if (row < 0 || row >= rows.size()) return null;
            return rows.get(row);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LocalSharedTorrent t = rows.get(rowIndex);
            if (t == null) return "";
            switch (columnIndex) {
                case 0:
                    return t.name() == null ? "" : t.name();
                case 1:
                    return formatSize(t.sizeBytes());
                case 2:
                    return t.fileCount();
                case 3:
                    return shortHex(t.infoHashHex(), 16);
                case 4:
                    return shortHex(t.publisherEd25519Pub(), 16);
                case 5:
                    return formatTimestamp(t.addedAt());
                case 6:
                    return formatTimestamp(t.lastSeenAt());
                case 7:
                    return t.matchedFile() == null ? "" : t.matchedFile();
                default:
                    return "";
            }
        }
    }

    @Override
    public boolean applyOptions() {
        return true;
    }

    @Override
    public boolean isDirty() {
        return false;
    }
}
