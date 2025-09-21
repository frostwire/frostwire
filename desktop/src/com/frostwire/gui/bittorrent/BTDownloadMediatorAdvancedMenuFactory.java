/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.gui.theme.SkinMenu;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.util.BackgroundQueuedExecutorService;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BTDownloadMediatorAdvancedMenuFactory {
    private static final Logger LOG = Logger.getLogger(BTDownloadMediatorAdvancedMenuFactory.class);

    // Units and precision for formatting byte counts
    private static final String[] UNITS_RATE = {"B/s", "kB/s", "MB/s", "GB/s", "TB/s"};
    private static final int[] UNITS_PRECISION = {0, 1, 2, 2, 3};
    private static final int UNITS_STOP_AT = 4; // TB

    // Format byte count to human-readable string (e.g., "123.45 kB/s")
    private static String formatByteCountToKiBEtcPerSec(long n) {
        double value = n;
        int unitIndex = 0;
        while (value >= 1000 && unitIndex < UNITS_STOP_AT) {
            value /= 1000;
            unitIndex++;
        }
        int precision = UNITS_PRECISION[unitIndex];
        DecimalFormat df = new DecimalFormat();
        df.setGroupingUsed(false); // No commas
        df.setMinimumFractionDigits(precision);
        df.setMaximumFractionDigits(precision);
        return df.format(value) + " " + UNITS_RATE[unitIndex];
    }

    static SkinMenu createAdvancedSubMenu() {
        final com.frostwire.bittorrent.BTDownload[] dms = getSingleSelectedDownloadManagers();
        if (dms == null) {
            return null;
        }
        boolean upSpeedDisabled = false;
        long totalUpSpeed = 0;
        boolean upSpeedUnlimited = false;
        long upSpeedSetMax = 0;
        boolean downSpeedDisabled = false;
        long totalDownSpeed = 0;
        boolean downSpeedUnlimited = false;
        long downSpeedSetMax = 0;
        for (com.frostwire.bittorrent.BTDownload btDownload : dms) {
            try {
                int maxul = btDownload.getUploadRateLimit();
                if (maxul == 0) {
                    upSpeedUnlimited = true;
                } else {
                    if (maxul > upSpeedSetMax) {
                        upSpeedSetMax = maxul;
                    }
                }
                if (maxul == -1) {
                    maxul = 0;
                    upSpeedDisabled = true;
                }
                totalUpSpeed += maxul;
                int maxdl = btDownload.getDownloadRateLimit();
                if (maxdl == 0) {
                    downSpeedUnlimited = true;
                } else {
                    if (maxdl > downSpeedSetMax) {
                        downSpeedSetMax = maxdl;
                    }
                }
                if (maxdl == -1) {
                    maxdl = 0;
                    downSpeedDisabled = true;
                }
                totalDownSpeed += maxdl;
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        final SkinMenu menuAdvanced = new SkinMenu(I18n.tr("Advanced"));
        // advanced > Download Speed Menu //
        BTEngine engine = BTEngine.getInstance();
        long maxDownload = engine.downloadRateLimit();
        long maxUpload = engine.uploadRateLimit();
        addSpeedMenu(menuAdvanced, downSpeedDisabled, downSpeedUnlimited, totalDownSpeed, downSpeedSetMax, maxDownload, upSpeedDisabled, upSpeedUnlimited, totalUpSpeed, upSpeedSetMax,
                maxUpload, dms.length, new SpeedAdapter() {
                    public void setDownSpeed(final int speed) {
                        for (com.frostwire.bittorrent.BTDownload dm : dms) {
                            dm.setDownloadRateLimit(speed);
                        }
                    }

                    public void setUpSpeed(final int speed) {
                        for (com.frostwire.bittorrent.BTDownload dm : dms) {
                            dm.setUploadRateLimit(speed);
                        }
                    }
                });
        SkinMenu menuTracker = createTrackerMenu();
        if (menuTracker != null) {
            menuAdvanced.add(menuTracker);
        }
        
        // Add Check Local Data action to Advanced menu
        com.frostwire.bittorrent.BTDownload[] checkDataDms = getSingleSelectedDownloadManagers();
        if (checkDataDms != null) {
            menuAdvanced.add(new SkinMenuItem(new CheckLocalDataAction(checkDataDms[0])));
        }
        
        return menuAdvanced;
    }

    private static SkinMenu createTrackerMenu() {
        com.frostwire.bittorrent.BTDownload[] dms = getSingleSelectedDownloadManagers();
        if (dms == null) {
            return null;
        }
        SkinMenu menu = new SkinMenu(I18n.tr("Trackers"));
        menu.add(new SkinMenuItem(new EditTrackersAction(dms[0])));
        menu.add(new SkinMenuItem(new UpdateTrackerAction(dms[0])));
        menu.add(new SkinMenuItem(new ScrapeTrackerAction(dms[0])));
        return menu;
    }

    private static com.frostwire.bittorrent.BTDownload[] getSingleSelectedDownloadManagers() {
        BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
        if (downloaders.length != 1) {
            return null;
        }
        ArrayList<com.frostwire.bittorrent.BTDownload> list = new ArrayList<>(downloaders.length);
        for (BTDownload downloader : downloaders) {
            if (downloader instanceof BittorrentDownload) {
                com.frostwire.bittorrent.BTDownload dm = ((BittorrentDownload) downloader).getDl();
                if (dm != null) {
                    list.add(dm);
                }
            }
        }
        if (list.isEmpty()) {
            return null;
        }
        return list.toArray(new com.frostwire.bittorrent.BTDownload[0]);
    }

    private static void addSpeedMenu(SkinMenu menuAdvanced,
                                     boolean downSpeedDisabled,
                                     boolean downSpeedUnlimited,
                                     long totalDownSpeed,
                                     long downSpeedSetMax,
                                     long maxDownload,
                                     boolean upSpeedDisabled,
                                     boolean upSpeedUnlimited,
                                     long totalUpSpeed,
                                     long upSpeedSetMax,
                                     long maxUpload,
                                     final int num_entries,
                                     final SpeedAdapter adapter) {
        final int menuEntries = 24;
        // advanced > Download Speed Menu //
        final SkinMenu menuDownSpeed = new SkinMenu(I18n.tr("Set Down Speed"));
        menuAdvanced.add(menuDownSpeed);
        final SkinMenuItem itemCurrentDownSpeed = new SkinMenuItem();
        itemCurrentDownSpeed.setEnabled(false);
        StringBuilder speedText = new StringBuilder();
        String separator = "";
        if (downSpeedDisabled) {
            speedText.append(I18n.tr("Disabled"));
            separator = " / ";
        }
        if (downSpeedUnlimited) {
            speedText.append(separator);
            speedText.append(I18n.tr("Unlimited"));
            separator = " / ";
        }
        if (totalDownSpeed > 0) {
            speedText.append(separator);
            speedText.append(formatByteCountToKiBEtcPerSec(totalDownSpeed));
        }
        itemCurrentDownSpeed.setText(speedText.toString());
        menuDownSpeed.add(itemCurrentDownSpeed);
        menuDownSpeed.addSeparator();
        final SkinMenuItem[] itemsDownSpeed = new SkinMenuItem[menuEntries];
        ActionListener itemsDownSpeedListener = e -> {
            if (e.getSource() != null && e.getSource() instanceof SkinMenuItem item) {
                int speed = item.getClientProperty("maxdl") == null ? 0 : (Integer) item.getClientProperty("maxdl");
                adapter.setDownSpeed(speed);
            }
        };
        itemsDownSpeed[1] = new SkinMenuItem();
        itemsDownSpeed[1].setText(I18n.tr("No limit"));
        itemsDownSpeed[1].putClientProperty("maxdl", 0);
        itemsDownSpeed[1].addActionListener(itemsDownSpeedListener);
        menuDownSpeed.add(itemsDownSpeed[1]);

        //using 1024KiB/s as the default limit when no limit set.
        if (maxDownload == 0) {
            if (downSpeedSetMax == 0) {
                maxDownload = 1024 * 1024;
            } else {
                maxDownload = 4 * (downSpeedSetMax / 1024) * 1024;
            }
        }
        for (int i = 2; i < menuEntries; i++) {
            itemsDownSpeed[i] = new SkinMenuItem();
            itemsDownSpeed[i].addActionListener(itemsDownSpeedListener);
            // dms.length has to be > 0 when hasSelection
            int limit = (int) (maxDownload / (10 * num_entries) * (menuEntries - i));
            itemsDownSpeed[i].setText(formatByteCountToKiBEtcPerSec((long) limit * num_entries));
            itemsDownSpeed[i].putClientProperty("maxdl", limit);
            menuDownSpeed.add(itemsDownSpeed[i]);
        }

        // advanced > Upload Speed Menu //
        final SkinMenu menuUpSpeed = new SkinMenu(I18n.tr("Set Up Speed"));
        menuAdvanced.add(menuUpSpeed);
        final SkinMenuItem itemCurrentUpSpeed = new SkinMenuItem();
        itemCurrentUpSpeed.setEnabled(false);
        separator = "";
        speedText = new StringBuilder();
        if (upSpeedDisabled) {
            speedText.append(I18n.tr("Disabled"));
            separator = " / ";
        }
        if (upSpeedUnlimited) {
            speedText.append(separator);
            speedText.append(I18n.tr("Unlimited"));
            separator = " / ";
        }
        if (totalUpSpeed > 0) {
            speedText.append(separator);
            speedText.append(formatByteCountToKiBEtcPerSec(totalUpSpeed));
        }
        itemCurrentUpSpeed.setText(speedText.toString());
        menuUpSpeed.add(itemCurrentUpSpeed);
        menuUpSpeed.addSeparator();
        final SkinMenuItem[] itemsUpSpeed = new SkinMenuItem[menuEntries];
        ActionListener itemsUpSpeedListener = e -> {
            if (e.getSource() != null && e.getSource() instanceof SkinMenuItem item) {
                int speed = item.getClientProperty("maxul") == null ? 0 : (Integer) item.getClientProperty("maxul");
                adapter.setUpSpeed(speed);
            }
        };
        itemsUpSpeed[1] = new SkinMenuItem();
        itemsUpSpeed[1].setText(I18n.tr("No limit"));
        itemsUpSpeed[1].putClientProperty("maxul", 0);
        itemsUpSpeed[1].addActionListener(itemsUpSpeedListener);
        menuUpSpeed.add(itemsUpSpeed[1]);

        //using 1024KiB/s as the default limit when no limit set.
        if (maxUpload == 0) {
            maxUpload = 1024 * 1024;
        } else {
            if (upSpeedSetMax == 0) {
                maxUpload = 200 * 1024;
            } else {
                maxUpload = 4 * (upSpeedSetMax / 1024) * 1024;
            }
        }
        for (int i = 2; i < menuEntries; i++) {
            itemsUpSpeed[i] = new SkinMenuItem();
            itemsUpSpeed[i].addActionListener(itemsUpSpeedListener);
            int limit = (int) (maxUpload / (10 * num_entries) * (menuEntries - i));
            itemsUpSpeed[i].setText(formatByteCountToKiBEtcPerSec((long) limit * num_entries));
            itemsUpSpeed[i].putClientProperty("maxul", limit);
            menuUpSpeed.add(itemsUpSpeed[i]);
        }
    }

    interface SpeedAdapter {
        void setUpSpeed(int val);

        void setDownSpeed(int val);
    }

    static class EditTrackersAction extends AbstractAction {
        private final com.frostwire.bittorrent.BTDownload dm;

        EditTrackersAction(com.frostwire.bittorrent.BTDownload dm) {
            this.dm = dm;
            putValue(Action.NAME, I18n.tr("Edit Trackers"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            new EditTrackerDialog(GUIMediator.getAppFrame(), dm).setVisible(true);
        }
    }

    static class UpdateTrackerAction extends AbstractAction {
        private final com.frostwire.bittorrent.BTDownload dm;

        UpdateTrackerAction(com.frostwire.bittorrent.BTDownload dm) {
            this.dm = dm;
            putValue(Action.NAME, I18n.tr("Update Tracker"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            BackgroundQueuedExecutorService.schedule(dm::requestTrackerAnnounce);
        }
    }

    static class ScrapeTrackerAction extends AbstractAction {
        private final com.frostwire.bittorrent.BTDownload dm;

        ScrapeTrackerAction(com.frostwire.bittorrent.BTDownload dm) {
            this.dm = dm;
            putValue(Action.NAME, I18n.tr("Scrape Tracker"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            new Thread(dm::requestTrackerScrape).start();
        }
    }

    static class CheckLocalDataAction extends AbstractAction {
        private final com.frostwire.bittorrent.BTDownload dm;

        CheckLocalDataAction(com.frostwire.bittorrent.BTDownload dm) {
            this.dm = dm;
            putValue(Action.NAME, I18n.tr("Check Local Data"));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            new Thread(dm::forceRecheck).start();
        }
    }

    private static final class EditTrackerDialog extends JDialog {
        private final com.frostwire.bittorrent.BTDownload dm;

        EditTrackerDialog(JFrame frame, com.frostwire.bittorrent.BTDownload dm) {
            super(frame);
            this.dm = dm;
            setupUI();
            setLocationRelativeTo(frame);
        }

        void setupUI() {
            setTitle(I18n.tr("Edit trackers"));
            Dimension dim = new Dimension(512, 400);
            setSize(dim);
            setMinimumSize(dim);
            setPreferredSize(dim);
            setResizable(false);
            setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            setModalityType(ModalityType.APPLICATION_MODAL);
            GUIUtils.addHideAction((JComponent) getContentPane());
            JPanel panel = new JPanel();
            panel.setLayout(new MigLayout("", "[grow]", //columns
                    "[top][center, grow][bottom]")); //rows
            JLabel labelTitle = new JLabel(I18n.tr("Edit trackers, one by line"));
            panel.add(labelTitle, "cell 0 0");
            final JTextArea textTrackers = new JTextArea();
            ThemeMediator.fixKeyStrokes(textTrackers);
            JScrollPane scrollPane = new JScrollPane(textTrackers);
            scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
            fillTrackers(textTrackers);
            panel.add(scrollPane, "cell 0 1, growx, growy");
            JButton buttonAccept = new JButton(I18n.tr("Accept"));
            buttonAccept.addActionListener(e -> changeTrackers(textTrackers.getText()));
            panel.add(buttonAccept, "cell 0 2, split 2, right");
            JButton buttonCancel = new JButton(I18n.tr("Cancel"));
            buttonCancel.addActionListener(e -> EditTrackerDialog.this.dispose());
            panel.add(buttonCancel);
            setContentPane(panel);
        }

        private void fillTrackers(JTextArea textTrackers) {
            Set<String> set = dm.trackers();
            for (String tracker : set) {
                if (!StringUtils.isNullOrEmpty(tracker, true)) {
                    textTrackers.append(tracker.trim() + System.lineSeparator());
                }
            }
        }

        private void changeTrackers(String text) {
            List<String> urls = Arrays.asList(text.split(System.lineSeparator()));
            if (!validateTrackersUrls(urls)) {
                JOptionPane.showMessageDialog(this, I18n.tr("Check again your tracker URL(s).\n"), I18n.tr("Invalid Tracker URL\n"), JOptionPane.ERROR_MESSAGE);
            } else {
                setTrackersUrls(urls);
                dispose();
            }
        }

        private boolean validateTrackersUrls(List<String> urls) {
            if (urls == null || urls.isEmpty()) {
                return false;
            }
            String patternStr = "^(https?|udp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
            Pattern pattern = Pattern.compile(patternStr);
            for (String tracker_url : urls) {
                if (tracker_url.trim().isEmpty()) {
                    continue;
                }
                Matcher matcher = pattern.matcher(tracker_url.trim());
                if (!matcher.matches()) {
                    return false;
                }
            }
            return true;
        }

        private void setTrackersUrls(List<String> urls) {
            dm.trackers(new HashSet<>(urls));
        }
    }
}