package com.limegroup.gnutella.gui;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.limewire.util.Version;
import org.limewire.util.VersionFormatException;
import org.limewire.util.VersionUtils;

import com.limegroup.gnutella.settings.QuestionsHandler;
import com.limegroup.gnutella.util.Launcher;

class JavaVersionNotice {
   
    static final String REQUIRED = "1.5.0";

    static final String JAVA_16_BETA_VERSION = "1.6.0-beta";
    
    static final String JAVA_16_RC_VERSION = "1.6.0-rc";
    
    private static final String URL = "http://www.frostwire.com/download";

    public static boolean upgradeRequired(String javaVersion) {
        try {
            Version rq = new Version(REQUIRED);
            Version cr = new Version(javaVersion);
            if (cr.compareTo(rq) < 0) {
                return true;
            }
        } catch (VersionFormatException ignored) {
        }
        return false;
    }

    public static void showUpgradeRequiredDialog() {
        new UpgradeRequiredDialog().setVisible(true);
    }

    /**
     * Returns a dialog if an upgrade of Java is recommended for
     * <code>javaVersion</code>. The dialog has not been made visible when
     * returned.
     * 
     * @param javaVersion the version string of Java to check
     * @return null, if an upgrade is not recommended or the user opted out of
     *         getting upgrade notifications for <code>javaVersion</code>
     * @see VersionUtils#getJavaVersion()
     */
    public static JDialog getUpgradeRecommendedDialog(String javaVersion) {
        if (QuestionsHandler.LAST_CHECKED_JAVA_VERSION.getValue().equals(javaVersion)) {
            // the user opted out of warnings for that Java version
            return null;
        }

        if (javaVersion.startsWith(JAVA_16_BETA_VERSION)
                || javaVersion.startsWith(JAVA_16_RC_VERSION)) {
            String text = I18n.tr(I18n.tr("You are currently using a beta or pre-release version of Java 1.6.0. This version is known to have caused problems with FrostWire. Please upgrade to the final 1.6.0 release.\n"));
            return new UpgradeRecommendedDialog(text);
        }
        
        return null;
    }

    private static class UpgradeRequiredDialog extends JDialog {

        /**
         * 
         */
        private static final long serialVersionUID = 6248367339958751670L;

        private UpgradeRequiredDialog() {
            setTitle(I18n.tr("Upgrade Java"));
            setSize(new Dimension(100, 300));
            setModal(true);

            JPanel mainPanel = new JPanel();
            mainPanel
                    .setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

            String text = String.format(I18n.tr("FrostWire requires Java %s or higher in order to run. " +
            "You are currently running an out-of-date version of Java \n" +
            "Please visit %s in order to upgrade your version of Java"), REQUIRED, URL)+ "\n\n" +
            I18n.tr("Current Java Version:") + " " + VersionUtils.getJavaVersion() + "\n" +
            I18n.tr("Required Java Version:") + " " + REQUIRED + "\n\n";
            MultiLineLabel label = new MultiLineLabel(text, 500);

            JPanel labelPanel = new JPanel();
            labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
            labelPanel.add(Box.createHorizontalGlue());
            labelPanel.add(label);

            JPanel buttonPanel = new JPanel();
            JButton now = new JButton(I18n.tr("Upgrade Java"));
            now.setToolTipText(I18n.tr("Visit") + " " + URL);
            now.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        Launcher.openURL(URL);
                    } catch (IOException iox) {
                        openURLFailed();
                    }
                    System.exit(1);
                }
            });

            JButton later = new JButton(I18n.tr("Upgrade Later"));
            later.setToolTipText(I18n.tr("Exit FrostWire"));
            later.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    System.exit(1);
                }
            });

            buttonPanel.add(now);
            buttonPanel.add(later);

            mainPanel.add(labelPanel);
            mainPanel.add(buttonPanel);

            getContentPane().add(mainPanel);
            pack();

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Dimension dialogSize = getSize();
            setLocation((screenSize.width - dialogSize.width) / 2,
                    (screenSize.height - dialogSize.height) / 2);
        }

        private void openURLFailed() {
            JOptionPane
                    .showMessageDialog(this,
                            "To update, please direct your web-browser to "
                                    + URL + ".", "Unable to open browser",
                            JOptionPane.ERROR_MESSAGE);
        }
        
    }
    
    private static class UpgradeRecommendedDialog extends JDialog {

        /**
         * 
         */
        private static final long serialVersionUID = -6983561620290780547L;

        private UpgradeRecommendedDialog(String text) {
            setTitle(I18n.tr("Upgrade Java"));
            setSize(new Dimension(100, 500));
            setModal(true);

            JPanel mainPanel = new JPanel();
            mainPanel
                    .setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

            MultiLineLabel label = new MultiLineLabel(text, 500);

            JPanel labelPanel = new JPanel();
            labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
            labelPanel.add(Box.createHorizontalGlue());
            labelPanel.add(label);

            JPanel buttonPanel = new JPanel();
            JButton nowButton = new JButton(I18n.tr("More Information"));
            nowButton.setToolTipText(I18n.tr("Visit {0}", URL));
            nowButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    dispose();
                    GUIMediator.openURL(URL);
                }
            });

            JButton laterButton = new JButton(I18n.tr("Remind Me Later"));
            laterButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    dispose();
                }
            });

            JButton doNotShowAgainButton = new JButton(I18n.tr("Do not Show Again"));
            doNotShowAgainButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    QuestionsHandler.LAST_CHECKED_JAVA_VERSION.setValue(VersionUtils.getJavaVersion());
                    dispose();
                }
            });

            buttonPanel.add(nowButton);
            buttonPanel.add(laterButton);
            buttonPanel.add(doNotShowAgainButton);

            mainPanel.add(labelPanel);
            mainPanel.add(GUIMediator.getVerticalSeparator());
            mainPanel.add(buttonPanel);

            getContentPane().add(mainPanel);
            pack();

            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            Dimension dialogSize = getSize();
            setLocation((screenSize.width - dialogSize.width) / 2,
                    (screenSize.height - dialogSize.height) / 2);
        }

    }
}
