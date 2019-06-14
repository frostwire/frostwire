/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui.init;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.*;
import com.limegroup.gnutella.gui.search.DitherPanel;
import com.limegroup.gnutella.gui.search.Ditherer;
import com.limegroup.gnutella.util.FrostWireUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

class IntentPanel extends JPanel {
    private final JRadioButton mightUseButton;
    private final JRadioButton willNotButton;

    public IntentPanel() {
        mightUseButton = new JRadioButton();
        willNotButton = new JRadioButton();
        ButtonGroup bg = new ButtonGroup();
        bg.add(mightUseButton);
        bg.add(willNotButton);
        setBorder(BorderFactory.createLineBorder(ThemeMediator.LIGHT_BORDER_COLOR));
        setBackground(GUIUtils.hexToColor("F7F7F7"));
        //setBorder(BorderFactory.createCompoundBorder(
        //        BorderFactory.createLineBorder(GUIUtils.hexToColor("C8C8C8"), 1),
        //        BorderFactory.createLineBorder(GUIUtils.hexToColor("FBFBFB"), 3)));
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        JLabel almostDone = new JLabel(I18n.tr("You're almost done!"));
        JLabel stateIntent = new JLabel(I18n.tr("State your intent below to start using FrostWire") + " " + FrostWireUtils.getFrostWireVersion());
        Line line = new Line();
        MultiLineLabel description = new MultiLineLabel(I18n.tr("FrostWire is a peer-to-peer program for sharing authorized files only.  Installing and using the program does not constitute a license for obtaining or distributing unauthorized content."), 500);
        URLLabel findMore = new URLLabel("http://www.frostwire.com/?id=terms", I18n.tr("Find out more..."));
        Ditherer ditherer = new Ditherer(GUIUtils.hexToColor("E2E2E2"), GUIUtils.hexToColor("ECECEC"), Ditherer.Y_AXIS, new Ditherer.PolygonShader(2f));
        DitherPanel willNot = new DitherPanel(ditherer);
        willNot.setLayout(new GridBagLayout());
        gbc.fill = GridBagConstraints.NONE;
        gbc.insets = new Insets(10, 10, 10, 10);
        willNotButton.setText("<html><div display=\"block\" color=\"#515151\" size=\"13\">" + I18n.tr("I <b>will not</b> use FrostWire {0} for copyright infringement.", FrostWireUtils.getFrostWireVersion()) + "</div></html>");
        willNotButton.setOpaque(false);
        willNotButton.setIconTextGap(10);
        willNot.add(willNotButton, gbc);
        willNot.setBorder(BorderFactory.createEtchedBorder(GUIUtils.hexToColor("C8C8C8"), GUIUtils.hexToColor("FBFBFB")));
        DitherPanel mightUse = new DitherPanel(ditherer);
        mightUse.setLayout(new GridBagLayout());
        mightUseButton.setText("<html><div display=\"block\" color=\"#515151\" size=\"13\">" + I18n.tr("I <b>might use</b> FrostWire {0} for copyright infringement.", FrostWireUtils.getFrostWireVersion()) + "</div></html>");
        mightUseButton.setOpaque(false);
        mightUseButton.setIconTextGap(10);
        mightUse.add(mightUseButton, gbc);
        mightUse.setBorder(BorderFactory.createEtchedBorder(GUIUtils.hexToColor("C8C8C8"), GUIUtils.hexToColor("FBFBFB")));
        almostDone.setFont(almostDone.getFont().deriveFont(24f));
        almostDone.setForeground(GUIUtils.hexToColor("0086CA"));
        stateIntent.setFont(stateIntent.getFont().deriveFont(16f));
        stateIntent.setForeground(GUIUtils.hexToColor("333333"));
        description.setFont(description.getFont().deriveFont(14f));
        description.setForeground(GUIUtils.hexToColor("333333"));
        line.setColor(GUIUtils.hexToColor("C8C8C8"));
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.gridwidth = 3;
        gbc.gridx = 1;
        add(almostDone, gbc);
        add(stateIntent, gbc);
        gbc.insets = new Insets(10, 0, 10, 0);
        add(line, gbc);
        gbc.insets = new Insets(0, 0, 5, 0);
        add(description, gbc);
        gbc.insets = new Insets(0, 0, 0, 0);
        add(findMore, gbc);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridwidth = 1;
        gbc.gridx = 2;
        gbc.insets = new Insets(20, 70, 0, 0);
        add(willNot, gbc);
        gbc.insets = new Insets(13, 70, 0, 0);
        // add(mightUse, gbc);
    }

    boolean hasSelection() {
        return willNotButton.isSelected() || mightUseButton.isSelected();
    }

    boolean isWillNot() {
        return willNotButton.isSelected();
    }

    void addButtonListener(ActionListener changeListener) {
        willNotButton.addActionListener(changeListener);
        mightUseButton.addActionListener(changeListener);
    }
}
