package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.bugs.LocalClientInfo;
import com.limegroup.gnutella.util.FrostWireUtils;
import net.miginfocom.swing.MigLayout;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;


public class SendFeedbackDialog {
    private final JDialog DIALOG;

    SendFeedbackDialog() {
        DIALOG = new JDialog(GUIMediator.getAppFrame());
        DIALOG.setModal(true);
        DIALOG.setTitle(I18n.tr("Send Feedback"));
        DIALOG.setMinimumSize(new Dimension(750, 400));
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
        JTextArea messageTextArea = new JTextArea(I18n.tr("How can we make FrostWire better?"));

        messageTextArea.setRows(5);
        messageTextArea.setSize(new Dimension(380, 300));
        messageTextArea.setLineWrap(true);
        messageTextArea.setWrapStyleWord(true);

        feedbackPanel.add(messageTextArea, "spanx 2, growx, wrap");

        // Optional email
//        feedbackPanel.add(new JLabel(I18n.tr("Email (Optional)")), "spanx 2, wrap");
        feedbackPanel.add(new JTextField(I18n.tr("Where we can contact you?")), "spanx 2, growx, wrap");

        // System Information
        feedbackPanel.add(new JLabel(I18n.tr("System Information")),"spanx 2, wrap");
        JTextArea systemInformationTextArea = new JTextArea(getSytemInformation());
        systemInformationTextArea.setLineWrap(true);
        systemInformationTextArea.setEditable(false);
        systemInformationTextArea.setEnabled(false);
        systemInformationTextArea.setAutoscrolls(true);
        feedbackPanel.add(systemInformationTextArea, "spanx 2, grow, wrap");


        // Buttons
        JButton sendButton = new JButton(I18n.tr("Send"));
        sendButton.addActionListener(this::onSendClicked);
        feedbackPanel.add(sendButton, "pushx 1.0, alignx right");

        JButton cancelButton = new JButton(I18n.tr("Cancel"));
        cancelButton.addActionListener(GUIUtils.getDisposeAction());
        feedbackPanel.add(cancelButton, "alignx right");

        baseContentPane.add(feedbackPanel, "grow, wrap");
    }

    void showDialog() {
        GUIUtils.centerOnScreen(DIALOG);
        DIALOG.setVisible(true);
    }

    public static void main(String[] args) {
        SendFeedbackDialog sendFeedbackDialog = new SendFeedbackDialog();
        sendFeedbackDialog.showDialog();
    }

    private String getSytemInformation(){
        LocalClientInfo mock = new LocalClientInfo(new Throwable("mock"), "", "", false);
        return mock.getBasicSystemInfo()[0].toString();
    }

    @SuppressWarnings("unused")
    private void onSendClicked(ActionEvent e) {
        System.out.println("On Send button clicked");
    }

}
