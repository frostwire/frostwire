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
    private JLabel torrentNameLabel;
    private JLabel completionPercentageLabel;
    private BittorrentDownload bittorrentDownload;

    TransferDetailGeneral() {
        super(new MigLayout("insets 5px 5px 5px 5px, fill, debug"));
        setBackground(Color.WHITE);
        // Upper panel with Name, Percentage labels [future share button]
        // progress bar
        // slightly darker background color (0xf3f5f7)

        JPanel upperPanel = new JPanel(new MigLayout("insets 0 0 0 0, fillx, debug"));
        upperPanel.setBackground(new Color(0xf3f5f7));
        upperPanel.add(new JLabel("<html><b>" + I18n.tr("Name") + ":</b></html>"), "left, gapleft 15px, gapright 15px");
        upperPanel.add(torrentNameLabel = new JLabel(""), "left, gapright 15px");
        upperPanel.add(new JLabel("|"), "left, gapright 15px");
        upperPanel.add(completionPercentageLabel = new JLabel("<html><b>0%</b></html>"),"left, gapright 5px");
        upperPanel.add(new JLabel("<html><b>" + I18n.tr("complete") + "</b></html>"), "left, wrap");
        upperPanel.add(completionPercentageProgressbar = new JProgressBar(),"gapleft 15px, gapright 15px, span 5, growx");

        add(upperPanel, "top, growx, growprioy 0, wrap");

        // 2nd Section
        JPanel midPanel = new JPanel();
    }

    @Override
    public void updateData(BittorrentDownload btDownload) {

    }
}