/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.

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

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.SharingSettings;

import javax.swing.*;
import java.awt.*;

public class TorrentSeedingSettingComponent extends JPanel {
    private final boolean _precheck;
    private final boolean _border;
    private JRadioButton seedingRadioButton;
    private JRadioButton notSeedingRadioButton;

    /**
     * @param precheck - whether or not to pre-select one of the radio buttons.
     */
    public TorrentSeedingSettingComponent(boolean precheck, boolean useBorder) {
        _precheck = precheck;
        _border = useBorder;
        setupUI();
    }

    private void setupUI() {
        setLayout(new GridBagLayout());
        if (_border) {
            setBorder(ThemeMediator.createTitledBorder(I18n.tr("Seeding Settings")));
        }
        initOptionButtons();
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.insets = new Insets(5, 5, 5, 5);
        c.weightx = 1.0;
        add(seedingRadioButton, c);
        add(notSeedingRadioButton, c);
    }

    private void initOptionButtons() {
        seedingRadioButton = new JRadioButton("<html>" + I18n.tr("<strong>Seed finished downloads.</strong> BitTorrent users on the internet will be able<br/>to download file chunks of the data your torrents seed. (Recommended)") + "</html>");
        notSeedingRadioButton = new JRadioButton("<html>" + I18n.tr("<strong>Don't seed finished downloads.</strong> BitTorrent users on the internet may<br/>only download file chunks of that torrent from you while you're downloading its<br/>data files. <strong>Some trackers will penalize this Leeching behavior</strong>.") + "</html>");
        ButtonGroup radioGroup = new ButtonGroup();
        radioGroup.add(seedingRadioButton);
        radioGroup.add(notSeedingRadioButton);
        if (_precheck) {
            if (SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
                seedingRadioButton.setSelected(true);
                notSeedingRadioButton.setSelected(false);
            } else {
                notSeedingRadioButton.setSelected(true);
                seedingRadioButton.setSelected(false);
            }
        }
    }

    public boolean wantsSeeding() {
        return seedingRadioButton.isSelected();
    }

    public boolean hasOneBeenSelected() {
        return seedingRadioButton.isSelected() || notSeedingRadioButton.isSelected();
    }
}
