package com.limegroup.gnutella.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class ShutdownWindow extends JDialog {
    /**
     *
     */
    private static final long serialVersionUID = 446845150731872693L;
    private final ImageIcon backgroundImage;

    ShutdownWindow() {
        super(GUIMediator.getAppFrame());
        backgroundImage = ResourceManager.getImageFromResourcePath("org/limewire/gui/images/app_shutdown.jpg");
        setResizable(false);
        setTitle(I18n.tr("Shutting down FrostWire..."));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        JPanel backgroundPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.drawImage(backgroundImage.getImage(), 0, 0, null);
            }
        };
        backgroundPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                e.consume();
                GUIMediator.openURL("http://www.frostwire.com/android/?from=shutdown");
            }
        });
        backgroundPanel.setLayout(null);
        backgroundPanel.setSize(800, 500);
        add(backgroundPanel);
        Insets insets = backgroundPanel.getInsets();
        JLabel label = new JLabel(I18n.tr("Please wait while FrostWire shuts down..."));
        label.setFont(new Font("Dialog", Font.PLAIN, 16));
        Dimension labelPrefSize = label.getPreferredSize();
        backgroundPanel.add(label);
        label.setBounds(65 + insets.left, 400 + insets.top, labelPrefSize.width, labelPrefSize.height);
        JProgressBar bar = new LimeJProgressBar();
        bar.setIndeterminate(true);
        bar.setStringPainted(false);
        backgroundPanel.add(bar);
        bar.setBounds(55 + insets.left, 428 + insets.top, 680, 30);
        getContentPane().setPreferredSize(new Dimension(800, 500));
        pack();
    }

    public static void main(String[] args) {
        ShutdownWindow window = new ShutdownWindow();
        window.setVisible(true);
    }
}