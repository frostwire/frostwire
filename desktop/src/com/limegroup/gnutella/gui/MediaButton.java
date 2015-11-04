/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 
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

package com.limegroup.gnutella.gui;

import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingConstants;

/**
 * This class is really just a hack to make it easier to get the media player 
 * buttons to display correctly.
 * 
 * @author gubatron
 * @author aldenml
 */
public final class MediaButton extends JButton implements LongPressable {

    private String tipText;
    private String upName;
    private String downName;
    private ActionListener longPressActionListener = null;

    public MediaButton(String tipText, String upName, String downName) {
        init(tipText,upName,downName);
        this.addMouseListener(new LongPressMouseAdapter(this));
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
        if (upName != null) {
            ImageIcon upIcon = GUIMediator.getThemeImage(upName);
            setIcon(upIcon);
        }
        setHorizontalAlignment(SwingConstants.CENTER);
        if (downName != null) {
            ImageIcon downIcon = GUIMediator.getThemeImage(downName);
            setPressedIcon(downIcon);
            setRolloverIcon(downIcon);
        }
        //        setPreferredSize(new Dimension(
        //            getIcon().getIconWidth(), getIcon().getIconHeight()));
        setMargin(new Insets(0, 0, 0, 0));
        setBorder(null);
        setToolTipText(tipText);
    }
    
    /**
     * Enable this media button to act upon a long press by giving it an action listener here.
     * Set it to null, and it'll stop responding to long press events.
     * @param actionListener
     */
    public final void setLongPressActionListener(ActionListener actionListener) {
        longPressActionListener = actionListener;
    }

    @Override
    public void onLongPress(MouseEvent e) {
        if (longPressActionListener != null) {
            longPressActionListener.actionPerformed(null); 
        }
    }
}