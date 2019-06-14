/*
 * File    : ViewUtils.java
 * Created : 24-Oct-2003
 * By      : parg
 *
 * Copyright (C) 2004, 2005, 2006 Aelitis SAS, All rights Reserved
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * AELITIS, SAS au capital de 46,603.30 euros,
 * 8 Allee Lenotre, La Grille Royale, 78600 Le Mesnil le Roi, France.
 */

package com.frostwire.gui.bittorrent;

import com.frostwire.alexandria.Library;
import com.frostwire.alexandria.Playlist;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.gui.bittorrent.BTDownloadActions.AddToPlaylistAction;
import com.frostwire.gui.bittorrent.BTDownloadActions.CreateNewPlaylistAction;
import com.frostwire.gui.library.LibraryMediator;
import com.frostwire.gui.library.LibraryUtils;
import com.frostwire.gui.player.MediaPlayer;
import com.frostwire.gui.theme.SkinMenu;
import com.frostwire.gui.theme.SkinMenuItem;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import net.miginfocom.swing.MigLayout;
import org.gudy.azureus2.core3.util.DisplayFormatters;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class BTDownloadMediatorAdvancedMenuFactory {
    private static final Logger LOG = Logger.getLogger(BTDownloadMediatorAdvancedMenuFactory.class);

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
        return menuAdvanced;
    }

    static SkinMenu createAddToPlaylistSubMenu() {
        BTDownload[] downloaders = BTDownloadMediator.instance().getSelectedDownloaders();
        if (downloaders.length == 0) {
            return null;
        }
        for (BTDownload dler : downloaders) {
            if (!dler.isCompleted()) {
                return null;
            }
            File saveLocation = dler.getSaveLocation();
            if (saveLocation.isDirectory()) {
                //If the file(s) is(are) inside a folder
                if (!LibraryUtils.directoryContainsAudio(saveLocation)) {
                    return null;
                }
            } else if (!MediaPlayer.isPlayableFile(saveLocation)) {
                return null;
            }
        }
        SkinMenu menu = new SkinMenu(I18n.tr("Add to playlist"));
        menu.add(new SkinMenuItem(new CreateNewPlaylistAction()));
        Library library = LibraryMediator.getLibrary();
        List<Playlist> playlists = library.getPlaylists();
        if (playlists.size() > 0) {
            menu.addSeparator();
            for (Playlist playlist : library.getPlaylists()) {
                menu.add(new SkinMenuItem(new AddToPlaylistAction(playlist)));
            }
        }
        return menu;
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
        if (list.size() == 0) {
            return null;
        }
        return list.toArray(new com.frostwire.bittorrent.BTDownload[0]);
    }

    private static void addSpeedMenu(SkinMenu menuAdvanced, boolean downSpeedDisabled, boolean downSpeedUnlimited, long totalDownSpeed,
                                     long downSpeedSetMax, long maxDownload, boolean upSpeedDisabled, boolean upSpeedUnlimited, long totalUpSpeed, long upSpeedSetMax, long maxUpload, final int num_entries,
                                     final SpeedAdapter adapter) {
        // advanced > Download Speed Menu //
        final SkinMenu menuDownSpeed = new SkinMenu(I18n.tr("Set Down Speed"));
        menuAdvanced.add(menuDownSpeed);
        final SkinMenuItem itemCurrentDownSpeed = new SkinMenuItem();
        itemCurrentDownSpeed.setEnabled(false);
        StringBuffer speedText = new StringBuffer();
        String separator = "";
        //itemDownSpeed.                   
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
            speedText.append(DisplayFormatters.formatByteCountToKiBEtcPerSec(totalDownSpeed));
        }
        itemCurrentDownSpeed.setText(speedText.toString());
        menuDownSpeed.add(itemCurrentDownSpeed);
        menuDownSpeed.addSeparator();
        final SkinMenuItem[] itemsDownSpeed = new SkinMenuItem[12];
        ActionListener itemsDownSpeedListener = e -> {
            if (e.getSource() != null && e.getSource() instanceof SkinMenuItem) {
                SkinMenuItem item = (SkinMenuItem) e.getSource();
                int speed = item.getClientProperty("maxdl") == null ? 0 : (Integer) item.getClientProperty("maxdl");
                adapter.setDownSpeed(speed);
            }
        };
        itemsDownSpeed[1] = new SkinMenuItem();
        itemsDownSpeed[1].setText(I18n.tr("No limit"));
        itemsDownSpeed[1].putClientProperty("maxdl", 0);
        itemsDownSpeed[1].addActionListener(itemsDownSpeedListener);
        menuDownSpeed.add(itemsDownSpeed[1]);
        if (true) {
            //using 200KiB/s as the default limit when no limit set.
            if (maxDownload == 0) {
                if (downSpeedSetMax == 0) {
                    maxDownload = 200 * 1024;
                } else {
                    maxDownload = 4 * (downSpeedSetMax / 1024) * 1024;
                }
            }
            for (int i = 2; i < 12; i++) {
                itemsDownSpeed[i] = new SkinMenuItem();
                itemsDownSpeed[i].addActionListener(itemsDownSpeedListener);
                // dms.length has to be > 0 when hasSelection
                int limit = (int) (maxDownload / (10 * num_entries) * (12 - i));
                itemsDownSpeed[i].setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(limit * num_entries));
                itemsDownSpeed[i].putClientProperty("maxdl", limit);
                menuDownSpeed.add(itemsDownSpeed[i]);
            }
        }
        // ---
        //        menuDownSpeed.addSeparator();
        //
        //        final SkinMenuItem itemDownSpeedManualSingle = new SkinMenuItem();
        //        itemDownSpeedManualSingle.setText(I18n.tr("Manual..."));
        //        itemDownSpeedManualSingle.addActionListener(new ActionListener() {
        //            public void actionPerformed(ActionEvent e) {
        //                //int speed_value = getManualSpeedValue(shell, true);
        //                //if (speed_value > 0) {adapter.setDownSpeed(speed_value);}
        //            }
        //        });
        //        menuDownSpeed.add(itemDownSpeedManualSingle);
        //        if (num_entries > 1) {
        //            final MenuItem itemDownSpeedManualShared = new MenuItem(menuDownSpeed, SWT.PUSH);
        //            Messages.setLanguageText(itemDownSpeedManualShared, isTorrentContext?"MyTorrentsView.menu.manual.shared_torrents":"MyTorrentsView.menu.manual.shared_peers");
        //            itemDownSpeedManualShared.addSelectionListener(new SelectionAdapter() {
        //                public void widgetSelected(SelectionEvent e) {
        //                    int speed_value = getManualSharedSpeedValue(shell, true, num_entries);
        //                    if (speed_value > 0) {adapter.setDownSpeed(speed_value);}
        //                }
        //            });
        //        }
        // advanced >Upload Speed Menu //
        final SkinMenu menuUpSpeed = new SkinMenu(I18n.tr("Set Up Speed"));
        menuAdvanced.add(menuUpSpeed);
        final SkinMenuItem itemCurrentUpSpeed = new SkinMenuItem();
        itemCurrentUpSpeed.setEnabled(false);
        separator = "";
        speedText = new StringBuffer();
        //itemUpSpeed.                   
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
            speedText.append(DisplayFormatters.formatByteCountToKiBEtcPerSec(totalUpSpeed));
        }
        itemCurrentUpSpeed.setText(speedText.toString());
        menuUpSpeed.add(itemCurrentUpSpeed);
        // ---
        menuUpSpeed.addSeparator();
        final SkinMenuItem[] itemsUpSpeed = new SkinMenuItem[12];
        ActionListener itemsUpSpeedListener = e -> {
            if (e.getSource() != null && e.getSource() instanceof SkinMenuItem) {
                SkinMenuItem item = (SkinMenuItem) e.getSource();
                int speed = item.getClientProperty("maxul") == null ? 0 : (Integer) item.getClientProperty("maxul");
                adapter.setUpSpeed(speed);
            }
        };
        itemsUpSpeed[1] = new SkinMenuItem();
        itemsUpSpeed[1].setText(I18n.tr("No limit"));
        itemsUpSpeed[1].putClientProperty("maxul", 0);
        itemsUpSpeed[1].addActionListener(itemsUpSpeedListener);
        menuUpSpeed.add(itemsUpSpeed[1]);
        if (true) {
            //using 75KiB/s as the default limit when no limit set.
            if (maxUpload == 0) {
                maxUpload = 75 * 1024;
            } else {
                if (upSpeedSetMax == 0) {
                    maxUpload = 200 * 1024;
                } else {
                    maxUpload = 4 * (upSpeedSetMax / 1024) * 1024;
                }
            }
            for (int i = 2; i < 12; i++) {
                itemsUpSpeed[i] = new SkinMenuItem();
                itemsUpSpeed[i].addActionListener(itemsUpSpeedListener);
                int limit = (int) (maxUpload / (10 * num_entries) * (12 - i));
                itemsUpSpeed[i].setText(DisplayFormatters.formatByteCountToKiBEtcPerSec(limit * num_entries));
                itemsUpSpeed[i].putClientProperty("maxul", limit);
                menuUpSpeed.add(itemsUpSpeed[i]);
            }
        }
        //        menuUpSpeed.addSeparator();
        //
        //        final SkinMenuItem itemUpSpeedManualSingle = new SkinMenuItem();
        //        itemUpSpeedManualSingle.setText(I18n.tr("Manual..."));
        //        itemUpSpeedManualSingle.addActionListener(new ActionListener() {
        //            public void actionPerformed(ActionEvent e) {
        //                //int speed_value = getManualSpeedValue(shell, false);
        //                //if (speed_value > 0) {adapter.setUpSpeed(speed_value);}
        //            }
        //        });
        //        menuUpSpeed.add(itemUpSpeedManualSingle);
        //        if (num_entries > 1) {
        //            final MenuItem itemUpSpeedManualShared = new MenuItem(menuUpSpeed, SWT.PUSH);
        //            Messages.setLanguageText(itemUpSpeedManualShared, isTorrentContext?"MyTorrentsView.menu.manual.shared_torrents":"MyTorrentsView.menu.manual.shared_peers" );
        //            itemUpSpeedManualShared.addSelectionListener(new SelectionAdapter() {
        //                public void widgetSelected(SelectionEvent e) {
        //                    int speed_value = getManualSharedSpeedValue(shell, false, num_entries);
        //                    if (speed_value > 0) {adapter.setUpSpeed(speed_value);}
        //                }
        //            });
        //        }
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
            new Thread(dm::requestTrackerAnnounce).start();
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
            if (urls == null || urls.size() == 0) {
                return false;
            }
            String patternStr = "^(https?|udp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
            Pattern pattern = Pattern.compile(patternStr);
            for (String tracker_url : urls) {
                if (tracker_url.trim().equals("")) {
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
