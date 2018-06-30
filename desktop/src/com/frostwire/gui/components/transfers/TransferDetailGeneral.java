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
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

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
    private final JLabel saveLocationLabel;
    private final JLabel infoHashLabel;
    private final JButton copyInfoHashButton;
    private final JLabel magnetURLLabel;
    private final JButton copyMagnetURLButton;
    private final JLabel createdOnLabel;
    private final JLabel commentLabel;

    // TODO: Icons for Copy to clipboard buttons
    // TODO: Add Pieces
    // TODO: Don't unselect transfers when pausing/resuming
    // TODO: Play with font-sizes

    private ActionListener copyInfoHashActionListener;
    private ActionListener copyMagnetURLActionListener;

    TransferDetailGeneral() {
        super(new MigLayout("insets 0px 0px 0px 0px, fill"));
        setBackground(Color.WHITE);
        // Upper panel with Name, Percentage labels [future share button]
        // progress bar
        // slightly darker background color (0xf3f5f7)

        JPanel upperPanel = new JPanel(new MigLayout("insets 15px 15px 15px 15px, fillx"));
        upperPanel.setBackground(new Color(0xf3f5f7));
        upperPanel.add(new JLabel("<html><b>" + I18n.tr("Name") + "</b></html>"), "left, gapleft 15px, gapright 15px");
        upperPanel.add(torrentNameLabel = new JLabel(""), "left, gapright 15px");
        upperPanel.add(new JLabel("|"), "left, gapright 15px");
        upperPanel.add(completionPercentageLabel = new JLabel("<html><b>0%</b></html>"),"left, gapright 5px");
        upperPanel.add(new JLabel("<html><b>" + I18n.tr("complete") + "</b></html>"), "left, pushx, wrap");
        upperPanel.add(completionPercentageProgressbar = new JProgressBar(),"span 5, growx");

        add(upperPanel, "top, growx, growprioy 0, wrap");

        // 2nd Section (TRANSFER)
        JPanel midPanel = new JPanel(new MigLayout("insets 15px 15px 0px 15px, fillx"));

        // time elapsed, time left, download speed
        midPanel.add(new JLabel(I18n.tr("Time elapsed")), "left");
        midPanel.add(timeElapsedLabel = new JLabel(),"left");
        midPanel.add(new JLabel(I18n.tr("Time left")), "left");
        midPanel.add(timeLeftLabel = new JLabel(), "left");
        midPanel.add(new JLabel(I18n.tr("Download speed")),"left");
        midPanel.add(downloadSpeedLabel = new JLabel(),"left, wrap");

        // Downloaded, status, download speed limit
        midPanel.add(new JLabel(I18n.tr("Downloaded")), "left");
        midPanel.add(downloadedLabel = new JLabel(), "left");
        midPanel.add(new JLabel(I18n.tr("Status")), "left");
        midPanel.add(statusLabel = new JLabel(), "left");
        midPanel.add(new JLabel(I18n.tr("Download speed limit")),"left");
        midPanel.add(downloadSpeedLimitLabel = new JLabel(), "left, wrap");

        // Uploaded, seeds, upload speed
        midPanel.add(new JLabel(I18n.tr("Uploaded")), "left");
        midPanel.add(uploadedLabel = new JLabel(), "left");
        midPanel.add(new JLabel(I18n.tr("Seeds")), "left");
        midPanel.add(seedsLabel = new JLabel(), "left");
        midPanel.add(new JLabel(I18n.tr("Upload speed")), "left");
        midPanel.add(uploadSpeedLabel = new JLabel(), "left, wrap");

        // Total Size, Peers, Upload speed limit
        // Share Ratio
        midPanel.add(new JLabel(I18n.tr("Total size")), "left");
        midPanel.add(totalSizeLabel = new JLabel(), "left");
        midPanel.add(new JLabel(I18n.tr("Peers")), "left");
        midPanel.add(peersLabel = new JLabel(), "left");
        midPanel.add(new JLabel(I18n.tr("Upload speed limit")), "left");
        midPanel.add(uploadSpeedLimitLabel = new JLabel(), "left, wrap");
        midPanel.add(new JLabel(I18n.tr("Share ratio")), "left");
        midPanel.add(shareRatioLabel = new JLabel(), "wrap");

        add(midPanel, "growx, growprioy 0, wrap");

        // 3rd Section, "GENERAL"
        // Save location
        // InfoHash
        // Magnet URL
        // Created On
        // Comment
        JPanel lowerPanel = new JPanel(new MigLayout("insets 15px 15px 15px 15px, fill"));
        lowerPanel.add(new JLabel(I18n.tr("Save location")), "left");
        lowerPanel.add(saveLocationLabel = new JLabel(), "left, growx, span 2, wrap");

        lowerPanel.add(new JLabel(I18n.tr("InfoHash")), "left");
        lowerPanel.add(infoHashLabel = new JLabel(), "left");
        lowerPanel.add(copyInfoHashButton = new JButton(I18n.tr("Copy")),"left, pushx, wrap");

        lowerPanel.add(new JLabel(I18n.tr("Magnet URL")), "left");
        lowerPanel.add(magnetURLLabel = new JLabel(), "left");
        lowerPanel.add(copyMagnetURLButton = new JButton(I18n.tr("Copy")),"left, pushx, wrap");

        lowerPanel.add(new JLabel(I18n.tr("Created On")), "left");
        lowerPanel.add(createdOnLabel = new JLabel(), "left, growx, span 2, wrap");

        lowerPanel.add(new JLabel(I18n.tr("Comment")), "left");
        lowerPanel.add(commentLabel = new JLabel(), "left, growx, span 2, wrap");

        add(lowerPanel, "grow");
    }

    @Override
    public void updateData(BittorrentDownload guiBtDownload) {
        if (guiBtDownload == null) {
            return;
        }

        BTDownload btDownload = guiBtDownload.getDl();
        TorrentHandle torrentHandle = btDownload.getTorrentHandle();
        TorrentStatus status = torrentHandle.status();
        TorrentInfo torrentInfo = torrentHandle.torrentFile();

        torrentNameLabel.setText(btDownload.getName());
        //"<html><b>0%</b></html>"
        int progress = btDownload.getProgress();
        completionPercentageLabel.setText("<html><b>" + progress + "%</b></html>");
        completionPercentageProgressbar.setMaximum(100);
        completionPercentageProgressbar.setValue(progress);

        timeElapsedLabel.setText(seconds2time(status.activeDuration()/1000));
        timeLeftLabel.setText(seconds2time(guiBtDownload.getETA()));
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

        infoHashLabel.setText(btDownload.getInfoHash());
        if (copyInfoHashActionListener != null) {
            copyInfoHashButton.removeActionListener(copyInfoHashActionListener);
        }
        copyInfoHashActionListener = e -> GUIMediator.setClipboardContent(btDownload.getInfoHash());
        copyInfoHashButton.addActionListener(copyInfoHashActionListener);

        String magnetURI = btDownload.magnetUri();
        if (magnetURI.length() > 50) {
            magnetURLLabel.setText(magnetURI.substring(0,49) + "...");
        } else {
            magnetURLLabel.setText(magnetURI);
        }
        if (copyMagnetURLActionListener != null) {
            copyMagnetURLButton.removeActionListener(copyMagnetURLActionListener);
        }
        copyMagnetURLActionListener = e -> GUIMediator.setClipboardContent(magnetURI);
        copyMagnetURLButton.addActionListener(copyMagnetURLActionListener);

        createdOnLabel.setText(btDownload.getCreated().toString());
        commentLabel.setText(torrentInfo.comment());
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
            time.append(Long.toString(days));
            time.append(":");
            if (hours < 10)
                time.append("0");
        }
        if (days != 0 || hours != 0) {
            time.append(Long.toString(hours));
            time.append(":");
            if (minutes < 10)
                time.append("0");
        }
        time.append(Long.toString(minutes));
        time.append(":");
        if (seconds < 10)
            time.append("0");
        time.append(Long.toString(seconds));
        return time.toString();
    }
}