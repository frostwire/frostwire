/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.components.transfers;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.gui.bittorrent.BTDownloadDataLine;
import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.gui.theme.ThemeMediator;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconButton;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import com.frostwire.util.SafeText;

import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;

public final class TransferDetailGeneral extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private final JProgressBar completionPercentageProgressbar;
    private final JLabel timeElapsedLabel;
    private final JLabel torrentNameLabel;
    private final JLabel completionPercentageLabel;
    private final JLabel timeLeftLabel;
    private final JLabel downloadSpeedLabel;
    private final JLabel downloadedLabel;
    private final JLabel statusLabel;
    private final JLabel downloadSpeedLimitLabel;
    private final JLabel uploadedLabel;
    private final JLabel seedsLabel;
    private final JLabel uploadSpeedLabel;
    private final JLabel totalSizeLabel;
    private final JLabel peersLabel;
    private final JLabel uploadSpeedLimitLabel;
    private final JLabel shareRatioLabel;
    private final JGrayLabel saveLocationGrayLabel;
    private final JLabel saveLocationLabel;
    private final JLabel infoHashLabel;
    private final JButton copyInfoHashButton;
    private final JLabel magnetURLLabel;
    private final JButton copyMagnetURLButton;
    private final JLabel createdOnLabel;
    private final JLabel commentLabel;
    private ActionListener copyInfoHashActionListener;
    private ActionListener copyMagnetURLActionListener;
    private BTDownload btDownload;

    TransferDetailGeneral() {
        //MigLayout Notes:
        // insets -> padding for the layout
        // gap -> space/margin _between cells_ in the layout, if you have
        //        a different background in inner components than the
        //        container, the opaque background of the container will leak in between
        //        cells
        //
        // API inconsistencies:
        // (Layout) insets <top/all left bottom right>
        // (Layout) gap <x y>
        // (Component) gap <left right top bottom> (FML)
        // (Component) pad <top left bottom right> (like insets, why not just re-use insets, FML)
        super(new MigLayout("insets 0 0 0 0, gap 0 0, fillx"));

        Color defaultBG = UIManager.getColor("Panel.background");
        Color defaultFG = UIManager.getColor("Label.foreground");

        Color backgroundColor = ThemeMediator.isLightThemeOn() ?
                defaultBG : ThemeMediator.APP_REALLY_DARK_COLOR;
        Color foregroundColor = ThemeMediator.isLightThemeOn() ?
                defaultFG : ThemeMediator.APP_REALLY_DARK_COLOR;

        setForeground(foregroundColor);
        setBackground(backgroundColor);
        setOpaque(true);
        // Upper panel with Name, Percentage labels [future share button]
        // progress bar
        // slightly darker background color (0xf3f5f7)
        JPanel upperPanel = new JPanel(new MigLayout("insets 17px, gap 0 5px, fill"));
        //upperPanel.setBackground(new Color(0xf3f5f7));
        upperPanel.setForeground(foregroundColor);
        upperPanel.setBackground(backgroundColor);
        upperPanel.setOpaque(true);
        // Create labels without HTML content to avoid EDT violation
        JLabel nameLabel = new JLabel();
        JLabel completeLabel = new JLabel();
        upperPanel.add(nameLabel, "left, gapleft 2px, gapright 10px");
        upperPanel.add(torrentNameLabel = new JLabel(""), "left, gapright 10px");
        upperPanel.add(new JLabel("|"), "left, gapright 10px");
        upperPanel.add(completionPercentageLabel = new JLabel(), "left, gapright 5px");
        upperPanel.add(completeLabel, "left, pushx, wrap");
        upperPanel.add(completionPercentageProgressbar = new JProgressBar(), "span 5, growx");
        // Defer HTML content to avoid EDT violation
        // HTML rendering triggers expensive font metrics calculations (>2 second EDT block)
        SwingUtilities.invokeLater(() -> {
            nameLabel.setText("<html><b>" + I18n.tr("Name") + ":</b></html>");
            completionPercentageLabel.setText("<html><b>0%</b></html>");
            completeLabel.setText("<html><b>" + I18n.tr("complete") + "</b></html>");
        });
        // 2nd Section (TRANSFER)
        JPanel midPanel = new JPanel(new MigLayout("insets 18px, gap 5px 5px"));
        midPanel.setForeground(foregroundColor);
        midPanel.setBackground(backgroundColor.brighter());
        midPanel.setOpaque(true);
        // time elapsed, time left, download speed, status
        midPanel.add(new JGrayLabel(I18n.tr("Time elapsed") + ":"), "split 2, gapright 10");
        midPanel.add(timeElapsedLabel = new JLabel(), "gapright 50");
        midPanel.add(new JGrayLabel(I18n.tr("Time left") + ":"), "split 2, gapright 10");
        midPanel.add(timeLeftLabel = new JLabel(), "gapright 50");
        midPanel.add(new JGrayLabel(I18n.tr("Download speed") + ":"), "split 2, gapright 10");
        midPanel.add(downloadSpeedLabel = new JLabel(), "gapright 50");
        midPanel.add(new JGrayLabel(I18n.tr("Status") + ":"), "split 2, gapright 10");
        midPanel.add(statusLabel = new JLabel(), "gapright 50, wrap");
        // Downloaded, seeds, download limit
        midPanel.add(new JGrayLabel(I18n.tr("Downloaded") + ":"), "split 2, gapright 10");
        midPanel.add(downloadedLabel = new JLabel(), "gapright 50");
        midPanel.add(new JGrayLabel(I18n.tr("Seeds") + ":"), "split 2, gapright 10");
        midPanel.add(seedsLabel = new JLabel(), "gapright 50");
        midPanel.add(new JGrayLabel(I18n.tr("Download limit") + ":"), "split 3, gapright 10");
        midPanel.add(downloadSpeedLimitLabel = new JLabel(), "gapright 5");
        IconButton downloadSpeedLimitButton = new IconButton("SPEED_LIMIT_SETTINGS", 20, 20);
        downloadSpeedLimitButton.setToolTipText(I18n.tr("Adjust download speed limit"));
        downloadSpeedLimitButton.addActionListener(e -> onAdjustDownloadSpeedLimit());
        midPanel.add(downloadSpeedLimitButton, "gapright 50, wrap");
        // Uploaded, peers, upload speed
        midPanel.add(new JGrayLabel(I18n.tr("Uploaded") + ":"), "split 2, gapright 10");
        midPanel.add(uploadedLabel = new JLabel(), "gapright 50");
        midPanel.add(new JGrayLabel(I18n.tr("Peers") + ":"), "split 2, gapright 10");
        midPanel.add(peersLabel = new JLabel(), "gapright 50");
        midPanel.add(new JGrayLabel(I18n.tr("Upload speed") + ":"), "split 2, gapright 10");
        midPanel.add(uploadSpeedLabel = new JLabel(), "gapright 50, wrap");
        // Total Size, share ratio, upload limit
        midPanel.add(new JGrayLabel(I18n.tr("Total size") + ":"), "split 2, gapright 10");
        midPanel.add(totalSizeLabel = new JLabel(), "gapright 50");
        midPanel.add(new JGrayLabel(I18n.tr("Share ratio")), "split 2, gapright 10");
        midPanel.add(shareRatioLabel = new JLabel(), "gapright 50");
        midPanel.add(new JGrayLabel(I18n.tr("Upload limit") + ":"), "split 3, gapright 10");
        midPanel.add(uploadSpeedLimitLabel = new JLabel(), "gapright 5");
        IconButton uploadSpeedLimitButton = new IconButton("SPEED_LIMIT_SETTINGS", 20, 20);
        uploadSpeedLimitButton.setToolTipText(I18n.tr("Adjust upload speed limit"));
        uploadSpeedLimitButton.addActionListener(e -> onAdjustUploadSpeedLimit());
        midPanel.add(uploadSpeedLimitButton, "gapright 50, wrap");
        // 3rd Section, "GENERAL"
        // Save location
        // InfoHash
        // Magnet URL
        // Created On
        // Comment
        JPanel lowerPanel = new JPanel(new MigLayout("insets 5px 18px 18px 18px, gap 5px 5px"));
        lowerPanel.setForeground(foregroundColor);
        lowerPanel.setBackground(backgroundColor.brighter());
        lowerPanel.setOpaque(true);
        saveLocationGrayLabel = new JGrayLabel("<html><a href=\"#\">" + I18n.tr("Save location") + "</a>:</html>");
        lowerPanel.add(saveLocationGrayLabel, "split 2, gapright 10");
        lowerPanel.add(saveLocationLabel = new JLabel(), "gapright 60, wrap");
        final ImageIcon copy_paste_gray = GUIMediator.getThemeImage("copy_paste_gray.png");
        final ImageIcon copy_paste = GUIMediator.getThemeImage("copy_paste.png");
        lowerPanel.add(new JGrayLabel(I18n.tr("InfoHash") + ":"), "split 3, gapright 10");
        lowerPanel.add(infoHashLabel = new JLabel(), "gapright 10");
        lowerPanel.add(copyInfoHashButton = new JButton(copy_paste_gray), "wrap");
        lowerPanel.add(new JGrayLabel(I18n.tr("Magnet URL") + ":"), "split 3, gapright 10");
        lowerPanel.add(magnetURLLabel = new JLabel(), "gapright 10");
        lowerPanel.add(copyMagnetURLButton = new JButton(copy_paste_gray), "wrap");
        lowerPanel.add(new JGrayLabel(I18n.tr("Created On") + ":"), "split 2, gapright 10");
        lowerPanel.add(createdOnLabel = new JLabel(), "gapright 60, wrap");
        lowerPanel.add(new JGrayLabel(I18n.tr("Comment") + ":"), "split 2, gapright 10");
        lowerPanel.add(commentLabel = new JLabel(), "gapright 60, wrap");
        copyInfoHashButton.setPressedIcon(copy_paste);
        copyInfoHashButton.setContentAreaFilled(false);
        copyMagnetURLButton.setContentAreaFilled(false);
        copyMagnetURLButton.setPressedIcon(copy_paste);
        JPanel lowerPortionWhiteBGContainer = new JPanel(new MigLayout("insets 0 0 0 0, gap 0 0, fill"));
        lowerPortionWhiteBGContainer.setOpaque(true);
        // Empty border for margins, and line border for visual delimiter
        lowerPortionWhiteBGContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(10, 10, 10, 10),
                BorderFactory.createLineBorder(new Color(0x9297a1))));
        lowerPortionWhiteBGContainer.add(upperPanel, "top, growx, gapbottom 5px, wrap");
        lowerPortionWhiteBGContainer.add(midPanel, "gap 0 0 0 0, growx, growprioy 0, wrap");
        lowerPortionWhiteBGContainer.add(lowerPanel, "gap 0 0 0 0, grow");
        JScrollPane scrollPane = new JScrollPane(lowerPortionWhiteBGContainer,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBackground(new Color(0xf3f5f7));
        scrollPane.setOpaque(true);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        scrollPane.setViewportBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        add(scrollPane, "grow");
        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
        if (verticalScrollBar != null) {
            // fixes black background issue when scrolling
            verticalScrollBar.addAdjustmentListener(e -> scrollPane.repaint());
        }
    }

    @Override
    public void updateData(BittorrentDownload guiBtDownload) {
        if (guiBtDownload == null) {
            return;
        }
        // Gather all data off the EDT — native JNI calls (status, torrentFile, getName, etc.)
        // can block for >2 seconds on large torrents
        SwingUtilities.invokeLater(() -> {
            BTDownload btDownload = guiBtDownload.getDl();
            TransferDetailGeneral.this.btDownload = btDownload;
            TorrentHandle torrentHandle = btDownload.getTorrentHandle();
            if (!torrentHandle.isValid()) {
                return;
            }
            TorrentStatus status = torrentHandle.status();
            TorrentInfo torrentInfo = torrentHandle.torrentFile();
            String name = SafeText.sanitize(btDownload.getName());
            int progress = btDownload.getProgress();
            long activeDuration = status.activeDuration() / 1000;
            TransferState state = btDownload.getState();
            long downloadSpeed = btDownload.getDownloadSpeed();
            long totalReceived = btDownload.getTotalBytesReceived();
            long downloadLimit = btDownload.getDownloadRateLimit();
            long totalSent = btDownload.getTotalBytesSent();
            int connectedSeeds = btDownload.getConnectedSeeds();
            int totalSeeds = btDownload.getTotalSeeds();
            long uploadSpeed = btDownload.getUploadSpeed();
            long size = btDownload.getSize();
            int connectedPeers = btDownload.getConnectedPeers();
            int totalPeers = btDownload.getTotalPeers();
            long uploadLimit = btDownload.getUploadRateLimit();
            String shareRatio = guiBtDownload.getShareRatio();
            String saveLocation = guiBtDownload.getSaveLocation().getAbsolutePath();
            String infoHash = btDownload.getInfoHash();
            String created = btDownload.getCreated().toString();
            String comment = torrentInfo.comment();
            long eta = guiBtDownload.getETA();

            GUIMediator.safeInvokeLater(() -> {
                torrentNameLabel.setText(name);
                completionPercentageLabel.setText("<html><b>" + progress + "%</b></html>");
                completionPercentageProgressbar.setMaximum(100);
                completionPercentageProgressbar.setValue(progress);
                timeElapsedLabel.setText(seconds2time(activeDuration));
                if (state.equals(TransferState.DOWNLOADING)) {
                    timeLeftLabel.setText(seconds2time(eta));
                } else {
                    timeLeftLabel.setText("");
                }
                downloadSpeedLabel.setText(GUIUtils.getBytesInHuman(downloadSpeed));
                downloadedLabel.setText(GUIUtils.getBytesInHuman(totalReceived));
                statusLabel.setText(BTDownloadDataLine.TRANSFER_STATE_STRING_MAP.get(state));
                downloadSpeedLimitLabel.setText(GUIUtils.getBytesInHuman(downloadLimit));
                uploadedLabel.setText(GUIUtils.getBytesInHuman(totalSent));
                seedsLabel.setText(String.format("%d %s %s %d %s",
                        connectedSeeds,
                        I18n.tr("connected"),
                        I18n.tr("of"),
                        totalSeeds,
                        I18n.tr("total")));
                uploadSpeedLabel.setText(GUIUtils.getBytesInHuman(uploadSpeed));
                totalSizeLabel.setText(GUIUtils.getBytesInHuman(size));
                peersLabel.setText(String.format("%d %s %s %d %s",
                        connectedPeers,
                        I18n.tr("connected"),
                        I18n.tr("of"),
                        totalPeers,
                        I18n.tr("total")));
                uploadSpeedLimitLabel.setText(GUIUtils.getBytesInHuman(uploadLimit));
                shareRatioLabel.setText(shareRatio);
                saveLocationLabel.setText(saveLocation);
                MouseListener[] mouseListeners = saveLocationGrayLabel.getMouseListeners();
                if (mouseListeners != null && mouseListeners.length > 0) {
                    for (MouseListener l : mouseListeners) {
                        saveLocationGrayLabel.removeMouseListener(l);
                    }
                }
                final BittorrentDownload finalGuiBtDownload = guiBtDownload;
                saveLocationGrayLabel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        GUIMediator.launchExplorer(new File(finalGuiBtDownload.getDl().getSavePath(), finalGuiBtDownload.getName()));
                    }
                });
                infoHashLabel.setText(infoHash);
                if (copyInfoHashActionListener != null) {
                    copyInfoHashButton.removeActionListener(copyInfoHashActionListener);
                }
                final String finalInfoHash = infoHash;
                copyInfoHashActionListener = e -> GUIMediator.setClipboardContent(finalInfoHash);
                copyInfoHashButton.addActionListener(copyInfoHashActionListener);
                final BTDownload finalBtDownload = btDownload;
                SwingUtilities.invokeLater(() -> {
                    String magnetURI = finalBtDownload.magnetUri();
                    if (magnetURI.length() > 50) {
                        magnetURLLabel.setText(magnetURI.substring(0, 49) + "...");
                    } else {
                        magnetURLLabel.setText(magnetURI);
                    }
                    ActionListener copyMagnetURLActionListener = e -> GUIMediator.setClipboardContent(magnetURI);
                    copyMagnetURLButton.addActionListener(copyMagnetURLActionListener);
                });
                createdOnLabel.setText(created);
                commentLabel.setText("<html><body><p style='width: 600px;'>" + SafeText.sanitize(comment) + "</p></body></html>");
            });
        });
    }

    private void onAdjustDownloadSpeedLimit() {
        if (btDownload == null) {
            return;
        }
        int currentKBps = btDownload.getDownloadRateLimit() / 1024;
        String input = (String) JOptionPane.showInputDialog(
                this,
                I18n.tr("Enter download speed limit in KB/s (0 = unlimited):"),
                I18n.tr("Download Speed Limit"),
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                currentKBps);
        if (input != null) {
            try {
                int kbps = Integer.parseInt(input.trim());
                if (kbps < 0) {
                    kbps = 0;
                }
                btDownload.setDownloadRateLimit(kbps * 1024);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void onAdjustUploadSpeedLimit() {
        if (btDownload == null) {
            return;
        }
        int currentKBps = btDownload.getUploadRateLimit() / 1024;
        String input = (String) JOptionPane.showInputDialog(
                this,
                I18n.tr("Enter upload speed limit in KB/s (0 = unlimited):"),
                I18n.tr("Upload Speed Limit"),
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                currentKBps);
        if (input != null) {
            try {
                int kbps = Integer.parseInt(input.trim());
                if (kbps < 0) {
                    kbps = 0;
                }
                btDownload.setUploadRateLimit(kbps * 1024);
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /**
     * Converts a value in seconds to:
     * "d:hh:mm:ss" where d=days, hh=hours, mm=minutes, ss=seconds, or
     * "h:mm:ss" where h=hours<24, mm=minutes, ss=seconds, or
     * "m:ss" where m=minutes<60, ss=seconds
     */
    private String seconds2time(long seconds) {
        if (seconds == -1) {
            return "∞";
        }
        long minutes = seconds / 60;
        seconds = seconds - minutes * 60;
        long hours = minutes / 60;
        minutes = minutes - hours * 60;
        long days = hours / 24;
        hours = hours - days * 24;
        // build the numbers into a string
        StringBuilder time = new StringBuilder();
        if (days != 0) {
            time.append(days);
            time.append(":");
            if (hours < 10)
                time.append("0");
        }
        if (days != 0 || hours != 0) {
            time.append(hours);
            time.append(":");
            if (minutes < 10)
                time.append("0");
        }
        time.append(minutes);
        time.append(":");
        if (seconds < 10)
            time.append("0");
        time.append(seconds);
        return time.toString();
    }

    private static class JGrayLabel extends JLabel {
        JGrayLabel(String html) {
            super(html);
            // Defer setForeground() to avoid EDT violation
            // Setting foreground on HTML content triggers HTML rendering (>2 second EDT block)
            SwingUtilities.invokeLater(() -> setForeground(Color.GRAY));
        }
    }
}
