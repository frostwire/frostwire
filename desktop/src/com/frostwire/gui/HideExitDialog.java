/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.gui;

import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ApplicationSettings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * @author gubatron
 * @author aldenml
 */
public class HideExitDialog extends JDialog {
    public static final int NONE = 0;
    public static final int HIDE = 1;
    private static final int EXIT = 2;
    private JCheckBox _checkBox;
    private int _result;

    public HideExitDialog(JFrame frame) {
        super(frame, I18n.tr("Do you want to hide FrostWire?"));
        _result = NONE;
        setupUI();
        setLocationRelativeTo(frame);
    }

    private void setupUI() {
        setResizable(false);
        getContentPane().setLayout(new GridBagLayout());
        GridBagConstraints c;
        JLabel _label = new JLabel("<html>" + I18n.tr("Closing the FrostWire window will only hide the application") + "<p>" + I18n.tr("This way file transfers may continue in the background.") + "</html>");
        c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 5, 5, 5);
        getContentPane().add(_label, c);
        _checkBox = new JCheckBox(I18n.tr("Don't show this again"));
        _checkBox.setSelected(true);
        c = new GridBagConstraints();
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 5, 5, 5);
        getContentPane().add(_checkBox, c);
        // hide button
        JButton _buttonHide = new JButton(I18n.tr("Hide"));
        _buttonHide.addActionListener(this::buttonHide_actionPerformed);
        c = new GridBagConstraints();
        c.insets = new Insets(4, 430, 8, 4);
        c.fill = GridBagConstraints.NONE;
        c.gridwidth = GridBagConstraints.RELATIVE;
        c.anchor = GridBagConstraints.EAST;
        c.ipadx = 20;
        getContentPane().add(_buttonHide, c);
        // exit button
        JButton _buttonExit = new JButton(I18n.tr("Exit"));
        _buttonExit.addActionListener(this::buttonExit_actionPerformed);
        c = new GridBagConstraints();
        c.insets = new Insets(4, 0, 8, 6);
        c.fill = GridBagConstraints.NONE;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.anchor = GridBagConstraints.EAST;
        c.ipadx = 18;
        getContentPane().add(_buttonExit, c);
        pack();
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setModalityType(ModalityType.APPLICATION_MODAL);
        getRootPane().setDefaultButton(_buttonHide);
        GUIUtils.addHideAction((JComponent) getContentPane());
    }

    private void buttonHide_actionPerformed(ActionEvent e) {
        _result = HIDE;
        if (_checkBox.isSelected()) {
            ApplicationSettings.MINIMIZE_TO_TRAY.setValue(true);
            ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.setValue(false);
        }
        GUIUtils.getDisposeAction().actionPerformed(e);
    }

    private void buttonExit_actionPerformed(ActionEvent e) {
        _result = EXIT;
        if (_checkBox.isSelected()) {
            ApplicationSettings.MINIMIZE_TO_TRAY.setValue(false);
            ApplicationSettings.SHOW_HIDE_EXIT_DIALOG.setValue(false);
        }
        GUIUtils.getDisposeAction().actionPerformed(e);
    }

    public int getResult() {
        return _result;
    }
}
