/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.limegroup.gnutella.gui.options.panes;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.BittorrentSettings;

import javax.swing.*;

/**
 * Option pane for controlling whether to prompt for save location when adding torrents.
 */
public final class TorrentSaveLocationPromptPaneItem extends AbstractPaneItem {
    private final static String TITLE = I18n.tr("Torrent Save Location");
    private final static String LABEL = I18n.tr("Configure how the save location is selected when adding torrents.");
    
    private final JCheckBox promptCheckBox;

    public TorrentSaveLocationPromptPaneItem() {
        super(TITLE, LABEL);
        
        promptCheckBox = new JCheckBox(I18n.tr("Always prompt for save location when adding torrents"));
        add(promptCheckBox);
    }

    @Override
    public void initOptions() {
        promptCheckBox.setSelected(BittorrentSettings.PROMPT_FOR_SAVE_LOCATION_ON_TORRENT_ADD.getValue());
    }

    @Override
    public boolean applyOptions() {
        boolean newValue = promptCheckBox.isSelected();
        boolean isDirty = newValue != BittorrentSettings.PROMPT_FOR_SAVE_LOCATION_ON_TORRENT_ADD.getValue();
        
        if (isDirty) {
            BittorrentSettings.PROMPT_FOR_SAVE_LOCATION_ON_TORRENT_ADD.setValue(newValue);
        }
        
        return isDirty;
    }

    @Override
    public boolean isDirty() {
        return promptCheckBox.isSelected() != BittorrentSettings.PROMPT_FOR_SAVE_LOCATION_ON_TORRENT_ADD.getValue();
    }
}
