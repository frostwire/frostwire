/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa), Jose Molina (@votaguz)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui.components.transfers;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.gui.bittorrent.BTDownloadDataLine;
import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
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
        // Upper panel with Name, Percentage labels [future share button]
        // progress bar
        // slightly darker background color (0xf3f5f7)
        JPanel upperPanel = new JPanel(new MigLayout("insets 17px, gap 0 5px, fill"));
        upperPanel.setBackground(new Color(0xf3f5f7));
        upperPanel.setOpaque(true);
        upperPanel.add(new JLabel("<html><b>" + I18n.tr("Name") + ":</b></html>"), "left, gapleft 2px, gapright 10px");
        upperPanel.add(torrentNameLabel = new JLabel(""), "left, gapright 10px");
        upperPanel.add(new JLabel("|"), "left, gapright 10px");
        upperPanel.add(completionPercentageLabel = new JLabel("<html><b>0%</b></html>"), "left, gapright 5px");
        upperPanel.add(new JLabel("<html><b>" + I18n.tr("complete") + "</b></html>"), "left, pushx, wrap");
        upperPanel.add(completionPercentageProgressbar = new JProgressBar(), "span 5, growx");
        // 2nd Section (TRANSFER)
        JPanel midPanel = new JPanel(new MigLayout("insets 18px, gap 5px 5px"));
        midPanel.setBackground(Color.WHITE);
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
        midPanel.add(new JGrayLabel(I18n.tr("Download limit") + ":"), "split 2, gapright 10");
        midPanel.add(downloadSpeedLimitLabel = new JLabel(), "gapright 50, wrap");
        // TODO: Add settings_gray button and dialog to adjust download speed limit
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
        midPanel.add(new JGrayLabel(I18n.tr("Upload limit") + ":"), "split 2, gapright 10");
        midPanel.add(uploadSpeedLimitLabel = new JLabel(), "gapright 50, wrap");
        // TODO: Add settings_gray button and dialog to adjust upload speed limit
        // 3rd Section, "GENERAL"
        // Save location
        // InfoHash
        // Magnet URL
        // Created On
        // Comment
        JPanel lowerPanel = new JPanel(new MigLayout("insets 5px 18px 18px 18px, gap 5px 5px"));
        lowerPanel.setBackground(Color.WHITE);
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
        BTDownload btDownload = guiBtDownload.getDl();
        TorrentHandle torrentHandle = btDownload.getTorrentHandle();
        if (!torrentHandle.isValid()) {
            return;
        }
        TorrentStatus status = torrentHandle.status();
        TorrentInfo torrentInfo = torrentHandle.torrentFile();
        torrentNameLabel.setText(btDownload.getName());
        int progress = btDownload.getProgress();
        completionPercentageLabel.setText("<html><b>" + progress + "%</b></html>");
        completionPercentageProgressbar.setMaximum(100);
        completionPercentageProgressbar.setValue(progress);
        timeElapsedLabel.setText(seconds2time(status.activeDuration() / 1000));
        if (btDownload.getState().equals(TransferState.DOWNLOADING)) {
            timeLeftLabel.setText(seconds2time(guiBtDownload.getETA()));
        } else {
            timeLeftLabel.setText("");
        }
        downloadSpeedLabel.setText(GUIUtils.getBytesInHuman(btDownload.getDownloadSpeed()));
        downloadedLabel.setText(GUIUtils.getBytesInHuman(btDownload.getTotalBytesReceived()));
        statusLabel.setText(BTDownloadDataLine.TRANSFER_STATE_STRING_MAP.get(btDownload.getState()));
        downloadSpeedLimitLabel.setText(GUIUtils.getBytesInHuman(btDownload.getDownloadRateLimit()));
        uploadedLabel.setText(GUIUtils.getBytesInHuman(btDownload.getTotalBytesSent()));
        seedsLabel.setText(String.format("%d %s %s %d %s",
                btDownload.getConnectedSeeds(),
                I18n.tr("connected"),
                I18n.tr("of"),
                btDownload.getTotalSeeds(),
                I18n.tr("total")));
        uploadSpeedLabel.setText(GUIUtils.getBytesInHuman(btDownload.getUploadSpeed()));
        totalSizeLabel.setText(GUIUtils.getBytesInHuman(btDownload.getSize()));
        peersLabel.setText(String.format("%d %s %s %d %s",
                btDownload.getConnectedPeers(),
                I18n.tr("connected"),
                I18n.tr("of"),
                btDownload.getTotalPeers(),
                I18n.tr("total")));
        uploadSpeedLimitLabel.setText(GUIUtils.getBytesInHuman(btDownload.getUploadRateLimit()));
        shareRatioLabel.setText(guiBtDownload.getShareRatio());
        saveLocationLabel.setText(guiBtDownload.getSaveLocation().getAbsolutePath());
        MouseListener[] mouseListeners = saveLocationGrayLabel.getMouseListeners();
        if (mouseListeners != null && mouseListeners.length > 0) {
            for (MouseListener l : mouseListeners) {
                saveLocationGrayLabel.removeMouseListener(l);
            }
        }
        saveLocationGrayLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                GUIMediator.launchExplorer(new File(guiBtDownload.getDl().getSavePath(), guiBtDownload.getName()));
            }
        });
        infoHashLabel.setText(btDownload.getInfoHash());
        if (copyInfoHashActionListener != null) {
            copyInfoHashButton.removeActionListener(copyInfoHashActionListener);
        }
        copyInfoHashActionListener = e -> GUIMediator.setClipboardContent(btDownload.getInfoHash());
        copyInfoHashButton.addActionListener(copyInfoHashActionListener);
        String magnetURI = btDownload.magnetUri();
        if (magnetURI.length() > 50) {
            magnetURLLabel.setText(magnetURI.substring(0, 49) + "...");
        } else {
            magnetURLLabel.setText(magnetURI);
        }
        if (copyMagnetURLActionListener != null) {
            copyMagnetURLButton.removeActionListener(copyMagnetURLActionListener);
        }
        copyMagnetURLActionListener = e -> GUIMediator.setClipboardContent(magnetURI);
        copyMagnetURLButton.addActionListener(copyMagnetURLActionListener);
        createdOnLabel.setText(btDownload.getCreated().toString());
        commentLabel.setText("<html><body><p style='width: 600px;'>" + torrentInfo.comment() + "</p></body></html>");
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
            setForeground(Color.GRAY);
        }
    }
}
