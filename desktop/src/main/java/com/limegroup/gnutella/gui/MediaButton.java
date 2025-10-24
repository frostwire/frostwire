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

package com.limegroup.gnutella.gui;

import com.frostwire.util.StringUtils;

import javax.swing.*;
import java.awt.*;

/**
 * This class is really just a hack to make it easier to get the media player
 * buttons to display correctly.
 *
 * @author gubatron
 * @author aldenml
 */
public final class MediaButton extends JButton {
    private String tipText;
    private String upName;
    private String downName;

    public MediaButton(String tipText, String upName, String downName) {
        init(tipText, upName, downName);
    }

    public void init(String tipText, String upName, String downName) {
        this.tipText = tipText;
        this.upName = upName;
        this.downName = downName;
        setupUI();
    }

    private void setupUI() {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setRolloverEnabled(true);
        if (!StringUtils.isNullOrEmpty(upName)) {
            ImageIcon upIcon = GUIMediator.getThemeImage(upName);
            setIcon(upIcon);
        } else {
            setIcon(null);
        }
        setHorizontalAlignment(SwingConstants.CENTER);
        if (!StringUtils.isNullOrEmpty(downName)) {
            ImageIcon downIcon = GUIMediator.getThemeImage(downName);
            setPressedIcon(downIcon);
            setRolloverIcon(downIcon);
        } else {
            setPressedIcon(null);
            setRolloverIcon(null);
        }
        //        setPreferredSize(new Dimension(
        //            getIcon().getIconWidth(), getIcon().getIconHeight()));
        setMargin(new Insets(0, 0, 0, 0));
        setBorder(null);
        setToolTipText(tipText);
    }
}
