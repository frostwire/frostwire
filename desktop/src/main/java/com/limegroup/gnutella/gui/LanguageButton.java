/*
 *     Created by Angel Leon (@gubatron)
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

import com.frostwire.gui.theme.IconRepainter;
import com.limegroup.gnutella.settings.ApplicationSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class LanguageButton extends JPanel {
    private final JButton bHeader;

    LanguageButton() {
        bHeader = new JButton();
        SwingUtilities.invokeLater(this::updateLanguageFlag);
        //when pressed displays a dialog that allows you to change the language.
        ActionListener languageButtonListener = e -> {
            LanguageWindow lw = new LanguageWindow();
            GUIUtils.centerOnScreen(lw);
            lw.setVisible(true);
        };
        bHeader.addActionListener(languageButtonListener);
        MouseListener languageMouseListener = new MouseAdapter() {
            //simulate active cursor, we could choose another cursor though
            public void mouseEntered(MouseEvent e) {
                e.getComponent().setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            //go back to normal
            public void mouseExited(MouseEvent e) {
                e.getComponent().setCursor(Cursor.getDefaultCursor());
            }
        };
        bHeader.addMouseListener(languageMouseListener);
        zeroInsets(this);
        setBorder(null);
        adjustSizes(bHeader);
        add(bHeader);
    }

    private static void setSizes(JButton b) {
        Dimension d = new Dimension(28, 16);
        b.setMaximumSize(d);
        b.setMinimumSize(d);
        b.setPreferredSize(d);
    }

    private static void zeroInsets(JComponent jc) {
        Insets insets = jc.getInsets();
        insets.left = 0;
        insets.right = 0;
        insets.top = 0;
        insets.bottom = 0;
    }

    void updateLanguageFlag() {
        bHeader.setContentAreaFilled(false);
        bHeader.setBorderPainted(false);
        bHeader.setOpaque(false);
        ImageIcon flag = LanguageFlagFactory.getFlag(ApplicationSettings.COUNTRY.getValue(),
                ApplicationSettings.LANGUAGE.getValue(),
                true);
        flag = (ImageIcon) IconRepainter.brightenIfDarkTheme(flag);
        bHeader.setIcon(flag);
        String tip = GUIMediator.getLocale().getDisplayName();
        bHeader.setToolTipText(tip);
        setToolTipText(tip);
    }

    /**
     * We override addMouseListener to pass the StatusBar MouseListener
     * to our internal Button.
     */
    @Override
    public void addMouseListener(MouseListener m) {
        bHeader.addMouseListener(m);
    }

    private void adjustSizes(JComponent jc) {
        zeroInsets(jc);
        setSizes((JButton) jc);
    }
}
