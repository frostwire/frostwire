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

import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.limegroup.gnutella.gui.I18n;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

public final class TransferDetailGeneral extends JPanel implements TransferDetailComponent.TransferDetailPanel {
    private final JProgressBar completionPercentageProgressbar;
    private final JLabel timeElapsedLabel;
    private final JLabel torrentNameLabel;
    private final JLabel completionPercentageLabel;
    private final JLabel timeLeftLabel;
    private final JLabel downSpeedLabel;
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
    private BittorrentDownload bittorrentDownload;

    TransferDetailGeneral() {
        super(new MigLayout("insets 0px 0px 0px 0px, fill, debug"));
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
        upperPanel.add(completionPercentageProgressbar = new JProgressBar(),"gapleft 15px, gapright 15px, span 5, growx");

        add(upperPanel, "top, growx, growprioy 0, wrap");

        // 2nd Section (TRANSFER)
        JPanel midPanel = new JPanel(new MigLayout("insets 15px 15px 0px 15px, fillx"));

        // time elapsed, time left, download speed
        midPanel.add(new JLabel(I18n.tr("Time elapsed")), "left");
        midPanel.add(timeElapsedLabel = new JLabel(),"left");
        midPanel.add(new JLabel(I18n.tr("Time left")), "left");
        midPanel.add(timeLeftLabel = new JLabel(), "left");
        midPanel.add(new JLabel(I18n.tr("Download speed")),"left");
        midPanel.add(downSpeedLabel = new JLabel(),"left, wrap");

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
        JPanel lowerPanel = new JPanel(new MigLayout("insets 15px 15px 0px 15px, fill"));
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
    public void updateData(BittorrentDownload btDownload) {

    }
}