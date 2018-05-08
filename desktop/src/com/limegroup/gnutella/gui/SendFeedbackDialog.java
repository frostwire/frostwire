package com.limegroup.gnutella.gui;

import net.miginfocom.swing.MigLayout;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.*;


public class SendFeedbackDialog {
    private final JDialog DIALOG;

    SendFeedbackDialog() {
        DIALOG = new JDialog(GUIMediator.getAppFrame());

        if (!OSUtils.isMacOSX())
            DIALOG.setModal(true);

        DIALOG.setPreferredSize(new Dimension(500, 500));
        DIALOG.setTitle(I18n.tr("Send Feedback"));
        DIALOG.setResizable(false);
        DIALOG.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        DIALOG.pack();

        JComponent baseContentPane = (JComponent) DIALOG.getContentPane();
        GUIUtils.addHideAction(baseContentPane);
        baseContentPane.setLayout(new MigLayout("debug"));
        baseContentPane.setBorder(
                BorderFactory.createEmptyBorder(GUIConstants.SEPARATOR, GUIConstants.SEPARATOR, GUIConstants.SEPARATOR,
                        GUIConstants.SEPARATOR));


        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new MigLayout("debug"));
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new MigLayout("debug"));

        JLabel emailLabel = new JLabel(I18n.tr("Email"));
        JTextField emailTextField = new JTextField(I18n.tr("Where we can contact you?"));

        JLabel messageLabel = new JLabel(I18n.tr("Message"));
        JTextArea messageTextArea = new JTextArea(I18n.tr("What do you have to say?"));
        messageTextArea.setSize(new Dimension(380, 300));

        JButton sendButton = new JButton(I18n.tr("Send"));
        sendButton.addActionListener(
                e -> {
                    // TODO: Send request to Frostwire servers.
                    System.out.println("SEEEEEENDDDDDING SOME FEEEEDBACK");
                }
        );

        JButton cancelButton = new JButton(I18n.tr("Cancel"));
        cancelButton.addActionListener(GUIUtils.getDisposeAction());

        // Adding components
        infoPanel.add(messageLabel, "wrap");
        infoPanel.add(messageTextArea, "wrap");
        infoPanel.add(emailLabel, "wrap");
        infoPanel.add(emailTextField);
        baseContentPane.add(infoPanel, "grow, wrap");

        buttonPanel.add(sendButton);
        buttonPanel.add(cancelButton);
        baseContentPane.add(buttonPanel, "grow");
    }

    void showDialog() {
        GUIUtils.centerOnScreen(DIALOG);
        DIALOG.setVisible(true);
    }

    public static void main(String[] args) {
        SendFeedbackDialog sendFeedbackDialog = new SendFeedbackDialog();
        sendFeedbackDialog.showDialog();
    }
}
