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
import java.awt.event.*;
import java.io.IOException;
import java.util.HashMap;


public class SendFeedbackDialog {
    private final JDialog DIALOG;
    private final JTextField emailTextField;
    private final JButton sendButton;
    private final JButton cancelButton;
    private final JTextField nameTextField;
    private JTextArea messageTextArea;
    final String FEEDBACK_HINT = I18n.tr("How can we make FrostWire better?") + "\n(" +
            I18n.tr("Please make sure your firewall or antivirus is not blocking FrostWire") + ")";
    final String SYSTEM_INFO = getSystemInformation();
    private static Pattern VALID_EMAIL_ADDRESS_REGEX = null;

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
        messageTextArea = new JTextArea(FEEDBACK_HINT);
        messageTextArea.selectAll();
        messageTextArea.setRows(7);
        messageTextArea.setSize(new Dimension(380, 400));
        messageTextArea.setLineWrap(true);
        messageTextArea.setWrapStyleWord(true);
        messageTextArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                onMessageTextAreaFirstClick();
            }
        });

        messageTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                onMessageTextAreaTyped();
            }
        });
        feedbackPanel.add(messageTextArea, "spanx 2, growx, wrap");

        // Optional email
        JPanel emailPanel = new JPanel(new MigLayout("fill, insets 0 0"));
        JLabel emailLabel = new JLabel(I18n.tr("Email (Optional)"));
        emailLabel.setEnabled(false);
        emailPanel.add(emailLabel, "shrink, align left");
        emailTextField = new JTextField();
        emailTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (messageTextArea.getText().equals(FEEDBACK_HINT)) {
                    messageTextArea.setText("");
                }
                onEmailTextFieldKeyTyped();
            }
        });
        emailPanel.add(emailTextField, "align left, pushx 1.0, growx");

        // Optional name
        JLabel nameLabel = new JLabel(I18n.tr("Your Name (Optional)"));
        nameLabel.setEnabled(false);
        emailPanel.add(nameLabel, "shrink, align left");
        nameTextField = new JTextField();
        nameTextField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                if (messageTextArea.getText().equals(FEEDBACK_HINT)) {
                    messageTextArea.setText("");
                }
            }
        });
        emailPanel.add(nameTextField, "align left, pushx 2.0, growx");

        feedbackPanel.add(emailPanel, "spanx 2, growx, wrap");

        // System Information
        JLabel systemInformationTextArea = new JLabel(SYSTEM_INFO);
        systemInformationTextArea.setEnabled(false);
        systemInformationTextArea.setAutoscrolls(true);

        feedbackPanel.add(systemInformationTextArea, "spanx 2, grow, wrap");

        // Buttons
        sendButton = new JButton(I18n.tr("Send"));
        sendButton.addActionListener(this::onSendClicked);
        sendButton.setEnabled(false);

        feedbackPanel.add(sendButton, "pushx 1.0, alignx right");

        cancelButton = new JButton(I18n.tr("Cancel"));
        cancelButton.addActionListener(GUIUtils.getDisposeAction());
        feedbackPanel.add(cancelButton, "alignx right");

        baseContentPane.add(feedbackPanel, "grow, wrap");
    }

    private void onMessageTextAreaTyped() {
        boolean feedbackMessageIsLongEnough = feedbackMessageIsLongEnough(messageTextArea.getText());
        sendButton.setEnabled(feedbackMessageIsLongEnough);
    }

    private boolean feedbackMessageIsLongEnough(String feedbackMessage) {
        return feedbackMessage.length() >= 16;
    }

    private void onEmailTextFieldKeyTyped() {
        if (emailTextField.getText().equals("")) {
            return;
        }
        boolean emailIsValid = validateEmail(emailTextField.getText());
        sendButton.setEnabled(emailIsValid && feedbackMessageIsLongEnough(messageTextArea.getText()));
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
        if (messageTextArea.getText().equals(FEEDBACK_HINT)) {
            messageTextArea.setText("");
        }
    }

    public void showDialog() {
        GUIUtils.centerOnScreen(DIALOG);
        DIALOG.setVisible(true);
    }

    public static void main(String[] args) {
        SendFeedbackDialog sendFeedbackDialog = new SendFeedbackDialog();
        sendFeedbackDialog.showDialog();
    }

    private static String getSystemInformation() {
        LocalClientInfo mock = new LocalClientInfo(new Throwable("mock"), "", "", false);
        String basicSystemInfo = mock.getBasicSystemInfo().sw.toString();
        return "<html>" + basicSystemInfo.replace("\n", "<br/>") + "</html>";
    }

    private void onSendClicked(ActionEvent e) {
        messageTextArea.setEnabled(false);
        emailTextField.setEnabled(false);
        sendButton.setText(I18n.tr("Sending..."));
        sendButton.setEnabled(false);
        cancelButton.setVisible(false);
        ThreadExecutor.startThread(() -> submitFeedbackAsync(messageTextArea.getText(), emailTextField.getText(), SYSTEM_INFO), "submitFeedbackAsync");
        DIALOG.dispose();
    }

    private void submitFeedbackAsync(String feedback, String email, String systemInfo) {
        HashMap<String, String> feedbackData = new HashMap<>();
        feedbackData.put("feedback", feedback);
        feedbackData.put("email", email);
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
