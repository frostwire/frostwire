/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Marcelina Knitter (@marcelinkaaa), José Molina (@votaguz)
 * Copyright (c) 2011-2018 FrostWire(R). All rights reserved.

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

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.UserAgentGenerator;
import com.frostwire.util.http.HttpClient;
import com.limegroup.gnutella.gui.bugs.LocalClientInfo;
import com.limegroup.gnutella.settings.UISettings;
import net.miginfocom.swing.MigLayout;
import org.limewire.concurrent.ThreadExecutor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.HashMap;

public class SendFeedbackDialog {
    private static Pattern VALID_EMAIL_ADDRESS_REGEX = null;
    private final JDialog DIALOG;
    private final JTextField emailTextField;
    private final JButton sendButton;
    private final JButton cancelButton;
    private final JTextField userNameTextField;
    private final String FEEDBACK_HINT = I18n.tr("How can we make FrostWire better?") + "\n(" +
            I18n.tr("Please make sure your firewall or antivirus is not blocking FrostWire") + ")";
    private final JTextArea feedbackTextArea;

    SendFeedbackDialog() {
        DIALOG = new JDialog(GUIMediator.getAppFrame());
        DIALOG.setModal(true);
        DIALOG.setTitle(I18n.tr("Send Feedback"));
        DIALOG.setMinimumSize(new Dimension(750, 350));
        DIALOG.setResizable(false);
        DIALOG.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        DIALOG.pack();
        JComponent baseContentPane = (JComponent) DIALOG.getContentPane();
        GUIUtils.addHideAction(baseContentPane);
        baseContentPane.setLayout(new MigLayout("fill, insets 10 10"));
        baseContentPane.setBorder(
                BorderFactory.createEmptyBorder(GUIConstants.SEPARATOR, GUIConstants.SEPARATOR, GUIConstants.SEPARATOR,
                        GUIConstants.SEPARATOR));
        JPanel feedbackPanel = new JPanel(new MigLayout("fill, insets 10 10"));
        // Message
        feedbackTextArea = new JTextArea(FEEDBACK_HINT);
        feedbackTextArea.selectAll();
        feedbackTextArea.setRows(7);
        Dimension feedbackTextAreaDimensions = new Dimension(690, 130);
        feedbackTextArea.setMinimumSize(feedbackTextAreaDimensions);
        feedbackTextArea.setMaximumSize(feedbackTextAreaDimensions);
        feedbackTextArea.setLineWrap(true);
        feedbackTextArea.setWrapStyleWord(true);
        feedbackTextArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onMessageTextAreaFirstClick();
            }
        });
        feedbackTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                sendButtonRefresh();
            }
        });
        GUIUtils.fixInputMap(feedbackTextArea);
        JScrollPane feedbackTextAreaScrollPane = new JScrollPane(feedbackTextArea);
        feedbackTextAreaScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        feedbackPanel.add(feedbackTextAreaScrollPane, "spanx 2, growx, wrap");
        // Optional email
        JPanel contactInfoPanel = new JPanel(new MigLayout("fill, insets 0 0"));
        JLabel emailLabel = new JLabel(I18n.tr("Email (Optional)"));
        emailLabel.setEnabled(false);
        contactInfoPanel.add(emailLabel, "shrink, align left");
        emailTextField = new JTextField();
        emailTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (feedbackTextArea.getText().equals(FEEDBACK_HINT)) {
                    feedbackTextArea.setText("");
                }
                sendButtonRefresh();
            }
        });
        contactInfoPanel.add(emailTextField, "align left, pushx 1.0, growx");
        // Optional name
        JLabel nameLabel = new JLabel(I18n.tr("Your Name (Optional)"));
        nameLabel.setEnabled(false);
        contactInfoPanel.add(nameLabel, "shrink, align left");
        userNameTextField = new JTextField();
        userNameTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (feedbackTextArea.getText().equals(FEEDBACK_HINT)) {
                    feedbackTextArea.setText("");
                }
            }
        });
        contactInfoPanel.add(userNameTextField, "align left, pushx 2.0, growx");
        feedbackPanel.add(contactInfoPanel, "spanx 2, growx, wrap");
        // System Information
        JLabel systemInformationTextArea = new JLabel(getSystemInformation(true));
        systemInformationTextArea.setEnabled(false);
        feedbackPanel.add(systemInformationTextArea, "spanx 2, grow, wrap");
        // Buttons
        sendButton = new JButton(I18n.tr("Send"));
        sendButton.addActionListener(e -> onSendClicked());
        sendButton.setEnabled(false);
        feedbackPanel.add(sendButton, "pushx 1.0, alignx right");
        cancelButton = new JButton(I18n.tr("Cancel"));
        cancelButton.addActionListener(GUIUtils.getDisposeAction());
        feedbackPanel.add(cancelButton, "alignx right");
        baseContentPane.add(feedbackPanel, "grow, wrap");
    }

    private static String getSystemInformation(boolean useHTML) {
        LocalClientInfo mock = new LocalClientInfo(new Throwable("mock"), "", "", false);
        String basicSystemInfo = mock.getBasicSystemInfo().sw.toString();
        return (useHTML) ?
                "<html>" + basicSystemInfo.replace("\n", "<br/>") + "</html>" :
                basicSystemInfo;
    }

    public static void main(String[] args) {
        SendFeedbackDialog sendFeedbackDialog = new SendFeedbackDialog();
        sendFeedbackDialog.showDialog();
    }

    private void sendButtonRefresh() {
        boolean messageLongEnough = feedbackTextArea.getText().length() >= 15;
        String emailValue = emailTextField.getText().trim();
        boolean validEmailField = emailValue.length() == 0 || validateEmail(emailValue);
        sendButton.setEnabled(messageLongEnough && validEmailField);
    }

    private boolean validateEmail(String emailString) {
        if (VALID_EMAIL_ADDRESS_REGEX == null) {
            VALID_EMAIL_ADDRESS_REGEX = Pattern.compile(
                    "^[\\w!#$%&'*+/=?`{|}~^-]+(?:\\.[\\w!#$%&'*+/=?`{|}~^-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{1,6}$",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            );
        }
        Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(emailString);
        return matcher.matches();
    }

    private void onMessageTextAreaFirstClick() {
        if (feedbackTextArea.getText().equals(FEEDBACK_HINT)) {
            feedbackTextArea.setText("");
        }
    }

    void showDialog() {
        GUIUtils.centerOnScreen(DIALOG);
        DIALOG.setVisible(true);
    }

    private void onSendClicked() {
        feedbackTextArea.setEnabled(false);
        emailTextField.setEnabled(false);
        userNameTextField.setEnabled(false);
        sendButton.setText(I18n.tr("Sending") + "...");
        sendButton.setEnabled(false);
        cancelButton.setVisible(false);
        ThreadExecutor.startThread(() -> submitFeedbackAsync(feedbackTextArea.getText(), emailTextField.getText(), userNameTextField.getText(), getSystemInformation(false)), "submitFeedbackAsync");
        new Timer(800, e -> {
            sendButton.setText(I18n.tr("Thank you") + "!");
            new Timer(500, e1 -> DIALOG.dispose()).start();
        }).start();
    }

    private void submitFeedbackAsync(String feedback, String email, String name, String systemInfo) {
        HashMap<String, String> feedbackData = new HashMap<>();
        feedbackData.put("feedback", feedback);
        feedbackData.put("email", email);
        feedbackData.put("name", name);
        feedbackData.put("systemInfo", systemInfo);
        HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
        try {
            httpClient.post("http://installer.frostwire.com/feedback.php", 10000,
                    UserAgentGenerator.getUserAgent(), feedbackData);
            UISettings.LAST_FEEDBACK_SENT_TIMESTAMP.setValue(System.currentTimeMillis());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }
}
