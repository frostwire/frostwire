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

package com.frostwire.gui.theme;

import com.limegroup.gnutella.gui.I18n;
import net.miginfocom.swing.MigLayout;
import com.frostwire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * A non blocking InputDialog meant to replace JOptionPane.showInputDialog() which's
 * mouse event handling breaks under MacOSX (ESC key and mouse clicks don't work for mac)
 *
 * @author gubatron
 * @author aldenml
 */
public final class FrostwireInputDialog extends JDialog {
    private final String message;
    private final String textViewText;
    private final Icon icon;
    private final JTextField textView;
    private final DialogFinishedListener listener;
    private JButton buttonCancel;

    private FrostwireInputDialog(Frame parentFrame,
                                 String windowTitle,
                                 String message,
                                 String textViewText,
                                 Icon icon,
                                 boolean modal,
                                 DialogFinishedListener listener) {
        super(parentFrame, windowTitle, modal);
        this.message = message;
        this.textViewText = textViewText;
        textView = new JTextField(textViewText != null ? textViewText : "");
        this.icon = icon;
        this.listener = listener;
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        initContentPane();
    }

    public static void showInputDialog(Frame parentComponent,
                                       String message,
                                       String title,
                                       Icon icon,
                                       String initialSelectionValue,
                                       DialogFinishedListener listener) {
        FrostwireInputDialog dialog = new FrostwireInputDialog(
                parentComponent,
                title,
                message,
                initialSelectionValue,
                icon,
                false,
                listener);
        dialog.setVisible(true);
    }

    private void initContentPane() {
        Dimension defaultDimension = new Dimension(500, 140);
        setMinimumSize(defaultDimension);
        setPreferredSize(defaultDimension);
        setResizable(false);
        Container contentPane = getContentPane();
        MigLayout layout = new MigLayout("insets 0, gap 5, fillx");
        contentPane.setLayout(layout);
        if (textViewText != null) {
            textView.selectAll();
        }
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton buttonOk = new JButton(I18n.tr("Ok"));
        buttonCancel = new JButton(I18n.tr("Cancel"));
        if (OSUtils.isAnyMac()) {
            buttonPanel.add(buttonCancel);
            buttonPanel.add(buttonOk);
        } else {
            buttonPanel.add(buttonOk);
            buttonPanel.add(buttonCancel);
        }
        textView.addKeyListener(getKeyListener());
        initButtonListeners(buttonOk, false);
        initButtonListeners(buttonCancel, true);
        JLabel messageLabel = new JLabel(message);
        messageLabel.setBounds(10, 10, defaultDimension.width - 40, 24);
        if (icon != null) {
            messageLabel.setIcon(icon);
            messageLabel.setHorizontalAlignment(SwingConstants.LEFT);
        }
        contentPane.add(messageLabel, "growx, gap 10 10 15 5, wrap");
        contentPane.add(textView, "growx, gap 10 10 0 5, wrap, height 24!");
        contentPane.add(buttonPanel, "gapright 5, align right");
        pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds((screenSize.width - defaultDimension.width) / 2,
                (screenSize.height - defaultDimension.height) / 2,
                defaultDimension.width, defaultDimension.height);
    }

    private void initButtonListeners(JButton button, final boolean cancelled) {
        button.addKeyListener(getKeyListener());
        button.addActionListener(getActionListener(cancelled));
    }

    private KeyListener getKeyListener() {
        return new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    onButtonClicked(true);
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    onButtonClicked(e.getComponent() == buttonCancel);
                }
            }
        };
    }

    private ActionListener getActionListener(final boolean cancelled) {
        return e -> onButtonClicked(cancelled);
    }

    private void onButtonClicked(boolean cancelled) {
        final String inputValue = cancelled ? null : textView.getText();
        FrostwireInputDialog.this.dispose();
        if (cancelled) {
            listener.onDialogCancelled();
        } else {
            listener.onDialogOk(inputValue);
        }
    }
}