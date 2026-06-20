/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.limegroup.gnutella.gui.options.panes;

import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.KarmaChain;
import com.frostwire.search.relay.KarmaChainEntry;
import com.frostwire.search.relay.KarmaChainTable;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchEngine;
import org.limewire.util.CommonUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public final class KarmaSettingsPaneItem extends AbstractPaneItem {

    private static final Logger LOG = Logger.getLogger(KarmaSettingsPaneItem.class);

    private static final String NA = "—";

    private static SearchEngine distributedEngine() {
        return SearchEngine.getSearchEngineByID(SearchEngine.SearchEngineID.DISTRIBUTED_ID);
    }

    private static final String TITLE = I18n.tr("Karma");
    private static final String LABEL = I18n.tr(
            "Your karma reputation is anchored to a hash-linked chain of endorsements " +
            "you issue to peers after completing downloads from them. Endorsements are " +
            "created automatically when a download finishes.");

    private final JLabel EPOCH_LABEL = new JLabel(NA);
    private final JLabel ENERGY_LABEL = new JLabel(NA);
    private final JLabel ENDORSEMENTS_LABEL = new JLabel(NA);
    private final JLabel SCORE_LABEL = new JLabel(NA);

    private final DefaultTableModel TABLE_MODEL = new DefaultTableModel(
            new String[]{
                    I18n.tr("Peer Public Key"),
                    I18n.tr("Score"),
                    I18n.tr("Endorsements"),
                    I18n.tr("Last Endorsed At")
            }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable PEERS_TABLE = new JTable(TABLE_MODEL);

    private final JButton ENDORSE_BUTTON = new JButton(I18n.tr("Endorse Peer"));
    private final JButton REFRESH_BUTTON = new JButton(I18n.tr("Refresh"));

    public KarmaSettingsPaneItem() {
        super(TITLE, LABEL);

        add(buildStatusPanel());
        add(getHorizontalSeparator());
        add(buildLeaderboardPanel());
        add(getHorizontalSeparator());
        add(buildActionsPanel());
        add(getVerticalSeparator());

        ENDORSE_BUTTON.addActionListener(e -> showEndorseInfo());
        REFRESH_BUTTON.addActionListener(e -> refreshKarmaInfo());

        PEERS_TABLE.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        PEERS_TABLE.setAutoCreateRowSorter(true);
        PEERS_TABLE.setFillsViewportHeight(true);
        PEERS_TABLE.getColumnModel().getColumn(0).setCellRenderer(new PublicKeyCellRenderer());
        PEERS_TABLE.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowCopyMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowCopyMenu(e);
            }
        });
    }

    private JPanel buildStatusPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 4, 2, 4);

        addInfoRow(panel, c, 0, I18n.tr("Current Epoch:"), EPOCH_LABEL);
        addInfoRow(panel, c, 1, I18n.tr("Available Endorsement Energy:"), ENERGY_LABEL);
        addInfoRow(panel, c, 2, I18n.tr("Total Endorsements Given:"), ENDORSEMENTS_LABEL);
        addInfoRow(panel, c, 3, I18n.tr("Karma Score:"), SCORE_LABEL);

        return panel;
    }

    private void addInfoRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent value) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        panel.add(l, c);
        c.gridx = 1; c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, c);
        c.fill = GridBagConstraints.NONE;
    }

    private JPanel buildLeaderboardPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        JLabel title = new JLabel(I18n.tr("Top Peers by Karma"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        panel.add(title, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(PEERS_TABLE);
        scroll.setPreferredSize(new Dimension(600, 220));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildActionsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(ENDORSE_BUTTON);
        panel.add(REFRESH_BUTTON);
        return panel;
    }

    @Override
    public void initOptions() {
        refreshKarmaInfo();
    }

    private void refreshKarmaInfo() {
        EPOCH_LABEL.setText(NA);
        ENERGY_LABEL.setText(NA);
        ENDORSEMENTS_LABEL.setText(NA);
        SCORE_LABEL.setText(NA);
        TABLE_MODEL.setRowCount(0);

        IdentityKeys identity = distributedEngine().identityKeys();
        if (identity == null) {
            EPOCH_LABEL.setText(I18n.tr("(not initialized)"));
            ENERGY_LABEL.setText(I18n.tr("(not available)"));
            ENDORSEMENTS_LABEL.setText(I18n.tr("(not available)"));
            SCORE_LABEL.setText(I18n.tr("(not available)"));
            return;
        }

        File dbFile = new File(CommonUtils.getUserSettingsDir(), "frostwire-shared-torrents.db");
        if (!dbFile.exists()) {
            EPOCH_LABEL.setText(I18n.tr("(no karma database)"));
            ENERGY_LABEL.setText(I18n.tr("(no karma database)"));
            ENDORSEMENTS_LABEL.setText(I18n.tr("(no karma database)"));
            SCORE_LABEL.setText(I18n.tr("(no karma database)"));
            return;
        }

        try (KarmaChainTable table = KarmaChainTable.open(dbFile)) {
            KarmaChain chain = table.loadChain(identity.ed25519PubRaw());

            long epoch = chain.currentEpoch();
            EPOCH_LABEL.setText(epoch >= 0 ? String.valueOf(epoch) : NA);

            int energy = chain.availableEnergy();
            ENERGY_LABEL.setText(String.valueOf(energy));

            int endorsementsGiven = 0;
            for (KarmaChainEntry e : chain.entries()) {
                if (e.kind() == KarmaChainEntry.Kind.ENDORSEMENT) {
                    endorsementsGiven++;
                }
            }
            ENDORSEMENTS_LABEL.setText(String.valueOf(endorsementsGiven));

            long score = PeerKarmaCache.computeScore(chain.entries());
            SCORE_LABEL.setText(score + " " + I18n.tr("endorsements"));

            fillTopPeers(table);
        } catch (Throwable t) {
            LOG.warn("Failed to read karma status", t);
        }
    }

    private void fillTopPeers(KarmaChainTable table) {
        try {
            List<KarmaChainTable.PeerKarmaScore> top = table.getTopPeers(20);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (KarmaChainTable.PeerKarmaScore p : top) {
                String pubHex = Hex.encode(p.peerPub());
                String lastEndorsed = p.lastEndorsedAt() > 0
                        ? sdf.format(new Date(p.lastEndorsedAt() * 1000L)) : NA;
                TABLE_MODEL.addRow(new Object[]{
                        pubHex,
                        p.totalScore(),
                        p.endorsementCount(),
                        lastEndorsed
                });
            }
        } catch (Throwable t) {
            LOG.warn("Failed to read top peers", t);
        }
    }

    private void maybeShowCopyMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = PEERS_TABLE.rowAtPoint(e.getPoint());
        if (row < 0) {
            return;
        }
        PEERS_TABLE.setRowSelectionInterval(row, row);
        int modelRow = PEERS_TABLE.convertRowIndexToModel(row);
        String full = (String) TABLE_MODEL.getValueAt(modelRow, 0);
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyItem = new JMenuItem(I18n.tr("Copy Full Public Key"));
        copyItem.addActionListener(ev -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(full), null);
            copyItem.setText(I18n.tr("Copied!"));
        });
        menu.add(copyItem);
        menu.show(PEERS_TABLE, e.getX(), e.getY());
    }

    private void showEndorseInfo() {
        JOptionPane.showMessageDialog(
                GUIMediator.getAppFrame(),
                I18n.tr("Manual endorsement requires a completed download from this peer. " +
                        "Endorsements are automatically created when a download from a peer completes."),
                I18n.tr("Endorse Peer"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public boolean applyOptions() {
        return true;
    }

    @Override
    public boolean isDirty() {
        return false;
    }

    private static final class PublicKeyCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (value instanceof String) {
                String hex = (String) value;
                setText(hex.length() > 16 ? hex.substring(0, 16) + "..." : hex);
                setToolTipText(hex);
            }
            return this;
        }
    }
}
