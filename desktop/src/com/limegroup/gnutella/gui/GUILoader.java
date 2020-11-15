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

package com.limegroup.gnutella.gui;

import com.frostwire.desktop.DesktopPlatform;
import com.frostwire.platform.Platforms;
import com.limegroup.gnutella.gui.bugs.FatalBugManager;
import com.limegroup.gnutella.util.FrostWireUtils;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Properties;

/**
 * This class constructs an <tt>Initializer</tt> instance that constructs
 * all of the necessary classes for the application.
 */
class GUILoader {
    /**
     * Creates an <tt>Initializer</tt> instance that constructs the
     * necessary classes for the application.
     * <p/>
     * <p>Invoked by com.limegroup.gnutella.gui.Main by reflection.
     *
     * @param args  the array of command line arguments
     * @param frame the splash screen; null, if no splash is displayed
     */
    public static void load(String[] args, Frame frame) {
        try {
            Platforms.set(new DesktopPlatform());
            System.out.println("FrostWire version " + FrostWireUtils.getFrostWireVersion() + " build " + FrostWireUtils.getBuildNumber());
            System.out.println(System.getProperty("java.vm.name") + " " + System.getProperty("java.vendor") + " " + System.getProperty("java.version") + " " + System.getProperty("java.specification.vendor"));
            Initializer initializer = new Initializer();
            initializer.initialize(args, frame);
        } catch (Throwable err) {
            hideSplash(frame);
            try {
                FatalBugManager.handleFatalBug(err);
            } catch (Throwable t) {
                Throwable error = err;
                try {
                    t.initCause(err);
                    error = t;
                } catch (Throwable ignored) {
                }
                //System.out.println(t);       
                showCorruptionError(error);
                System.exit(1);
            }
        }
    }

    private static void hideSplash(Frame frame) {
        try {
            if (frame != null)
                frame.dispose();
            if (SplashWindow.isSplashConstructed())
                SplashWindow.instance().setVisible(false);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Display a standardly formatted internal error message
     * coming from the backend.
     *
     * @param err the <tt>Throwable</tt> object containing information
     *            about the error
     */
    private static void showCorruptionError(Throwable err) {
        err.printStackTrace();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("FrostWire version " + FrostWireUtils.getFrostWireVersion());
        pw.print("Java version ");
        pw.print(System.getProperty("java.version", "?"));
        pw.print(" from ");
        pw.println(System.getProperty("java.vendor", "?"));
        pw.print(System.getProperty("os.name", "?"));
        pw.print(" v. ");
        pw.print(System.getProperty("os.version", "?"));
        pw.print(" on ");
        pw.println(System.getProperty("os.arch", "?"));
        Runtime runtime = Runtime.getRuntime();
        pw.println("Free/total memory: "
                + runtime.freeMemory() + "/" + runtime.totalMemory());
        pw.println();
        err.printStackTrace(pw);
        pw.println();
        pw.println("STARTUP ERROR!");
        pw.println();
        File propsFile = new File(getUserSettingsDir(), "frostwire.props");
        Properties props = new Properties();
        try {
            FileInputStream fis = new FileInputStream(propsFile);
            props.load(fis);
            fis.close();
            // list the properties in the PrintWriter.
            props.list(pw);
        } catch (IOException ignored) {
        }
        pw.flush();
        displayError(sw.toString());
    }

    /**
     * Gets the settings directory without using CommonUtils.
     */
    private static File getUserSettingsDir() {
        File dir = new File(System.getProperty("user.home"));
        String os = System.getProperty("os.name").toLowerCase();
        if (os.startsWith("mac os") && os.endsWith("x"))
            return new File(dir, "/Library/Preferences/FrostWire");
        else
            return new File(dir, ".frostwire");
    }

    /**
     * Displays an internal error with specialized formatting.
     */
    private static void displayError(String error) {
        System.out.println("Error: " + error);
        final JDialog DIALOG = new JDialog();
        DIALOG.setModal(true);
        final Dimension DIALOG_DIMENSION = new Dimension(350, 200);
        final Dimension INNER_SIZE = new Dimension(300, 150);
        DIALOG.setSize(DIALOG_DIMENSION);
        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.setLayout(new BorderLayout());
        String instr0;
        String instr1;
        String instr2;
        String instr3;
        String instr4;
        String instr5;
        instr0 = "One or more necessary files appear to be invalid.";
        instr1 = "This is generally caused by a corrupted installation.";
        instr2 = "Please try downloading and installing FrostWire again.";
        instr3 = "If the problem persists, please visit www.frostwire.com ";
        instr4 = "and click the 'Support' link.  ";
        instr5 = "Thank you.";
        JLabel label0 = new JLabel(instr0);
        JLabel label1 = new JLabel(instr1);
        JLabel label2 = new JLabel(instr2);
        JLabel label3 = new JLabel(instr3);
        JLabel label4 = new JLabel(instr4);
        JLabel label5 = new JLabel(instr5);
        JPanel labelPanel = new JPanel();
        JPanel innerLabelPanel = new JPanel();
        labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
        innerLabelPanel.setLayout(new BoxLayout(innerLabelPanel, BoxLayout.Y_AXIS));
        innerLabelPanel.add(label0);
        innerLabelPanel.add(label1);
        innerLabelPanel.add(label2);
        innerLabelPanel.add(label3);
        innerLabelPanel.add(label4);
        innerLabelPanel.add(label5);
        innerLabelPanel.add(Box.createVerticalStrut(6));
        labelPanel.add(innerLabelPanel);
        labelPanel.add(Box.createHorizontalGlue());
        final JTextArea textArea = new JTextArea(error);
        textArea.selectAll();
        textArea.copy();
        textArea.setColumns(50);
        textArea.setEditable(false);
        JScrollPane scroller = new JScrollPane(textArea);
        scroller.setBorder(BorderFactory.createEtchedBorder());
        scroller.setPreferredSize(INNER_SIZE);
        JPanel buttonPanel = new JPanel();
        JButton copyButton = new JButton("Copy Report");
        copyButton.addActionListener(e -> {
            textArea.selectAll();
            textArea.copy();
        });
        JButton quitButton = new JButton("Ok");
        quitButton.addActionListener(e -> DIALOG.dispose());
        buttonPanel.add(copyButton);
        buttonPanel.add(quitButton);
        mainPanel.add(labelPanel, BorderLayout.NORTH);
        mainPanel.add(scroller, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        DIALOG.getContentPane().add(mainPanel);
        DIALOG.pack();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension dialogSize = DIALOG.getSize();
        DIALOG.setLocation((screenSize.width - dialogSize.width) / 2,
                (screenSize.height - dialogSize.height) / 2);
        DIALOG.setVisible(true);
    }
}
