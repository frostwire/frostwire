package com.limegroup.gnutella.gui.notify;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.limegroup.gnutella.gui.GUIUtils;
import com.limegroup.gnutella.gui.URLLabel;

/**
 * Displays the notification icon and message in a <code>JLabel</code>. The
 * notification actions are rendered as clickable links underneath the label.
 */
public class DefaultNotificationRenderer implements NotificationRenderer {

    private static final int TEXT_WIDTH = 200;

    private JPanel panel;

    private JLabel messageLabel;

    private JPanel actionPanel;

    public DefaultNotificationRenderer() {
        panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setOpaque(false);

        JPanel messagePanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        messagePanel.setOpaque(false);
        messageLabel = new JLabel();
        messageLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        messagePanel.add(messageLabel);
        panel.add(messagePanel, BorderLayout.CENTER);
        
        actionPanel = new JPanel();
        actionPanel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        actionPanel.setOpaque(false);
                       
        panel.add(actionPanel, BorderLayout.SOUTH);
    }

    public Component getNotificationRendererComponent(
            NotificationWindow window, Object value, int index) {
        if (!(value instanceof Notification)) {
            messageLabel.setIcon(null);
            messageLabel.setText(GUIUtils.restrictWidth((value != null) ? value.toString() : "", TEXT_WIDTH));
            actionPanel.removeAll();
        } else {
            Notification notification = (Notification) value;

            messageLabel.setIcon(notification.getIcon());
            messageLabel.setText(GUIUtils.restrictWidth(notification.getMessage(), TEXT_WIDTH));
            messageLabel.setMinimumSize(messageLabel.getPreferredSize());
            
            actionPanel.removeAll();
            
            if (notification.useVerticleActionOrientation()) {
                actionPanel.setLayout(new BoxLayout(actionPanel, BoxLayout.Y_AXIS));
                actionPanel.add(Box.createVerticalStrut(10));
            }
            else {
                actionPanel.setLayout(new FlowLayout(FlowLayout.LEADING, 0, 0));
            }
            
            if (notification.getActions() != null) {
                for (Action action : notification.getActions()) {
                    URLLabel urlLabel = new URLLabel(action);
                    urlLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
                     
                    actionPanel.add(urlLabel);
                    actionPanel.add(notification.useVerticleActionOrientation() 
                            ? Box.createVerticalStrut(5) : Box.createHorizontalStrut(5));
                }
            }
        }
        return panel;
    }

}
