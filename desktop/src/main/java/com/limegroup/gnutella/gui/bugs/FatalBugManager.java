package com.limegroup.gnutella.gui.bugs;

import com.limegroup.gnutella.gui.CrashReportSpooler;
import com.limegroup.gnutella.gui.LimeWireModule;
import com.limegroup.gnutella.gui.LocalClientInfoFactory;
import com.limegroup.gnutella.gui.MultiLineLabel;
import com.limegroup.gnutella.gui.SplashWindow;
import java.awt.*;
import javax.swing.*;

/** A bare-bones bug manager, for fatal errors. */
public final class FatalBugManager {
  private FatalBugManager() {}

  /** Handles a fatal bug. */
  public static void handleFatalBug(Throwable bug) {
    CrashReportSpooler.recordSync(bug);
    bug.printStackTrace();
    // Build the LocalClientInfo out of the info ...
    LocalClientInfoFactory factoryToUse =
        LimeWireModule.instance()
            .getLimeWireGUIModule()
            .getLimeWireGUI()
            .getLocalClientInfoFactory();
    final LocalClientInfo info =
        factoryToUse.createLocalClientInfo(bug, Thread.currentThread().getName(), null, true);
    SwingUtilities.invokeLater(() -> reviewBug(info));
  }

  private static String warning() {
    return "You are using FrostWire. www.frostwire.com";
  }

  /** Reviews the bug. */
  private static void reviewBug(final LocalClientInfo info) {
    final JDialog DIALOG = new JDialog();
    DIALOG.setTitle("Fatal Error");
    final Dimension DIALOG_DIMENSION = new Dimension(100, 300);
    DIALOG.setSize(DIALOG_DIMENSION);
    JPanel mainPanel = new JPanel();
    mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
    MultiLineLabel label =
        new MultiLineLabel(
            warning()
                + "\n\n"
                + "FrostWire has encountered a fatal internal error and will now exit. "
                + "This is generally caused by a corrupted installation.  Please try "
                + "downloading and installing FrostWire again.\n\n"
                + "To aid with debugging, please click 'Send' to notify FrostWire about the problem. "
                + "If desired, you can click 'Review' to look at the information that will be sent. "
                + "If the problem persists, please visit www.frostwire.com and click the 'Support' "
                + "link.\n\n"
                + "Thank You.",
            400);
    JPanel labelPanel = new JPanel();
    labelPanel.setLayout(new BoxLayout(labelPanel, BoxLayout.X_AXIS));
    labelPanel.add(Box.createHorizontalGlue());
    labelPanel.add(label);
    JPanel buttonPanel = new JPanel();
    JButton sendButton = new JButton("Send");
    sendButton.addActionListener(
        e -> {
          // The anonymous report was already spooled by recordSync on entry;
          // there is no legacy servlet to contact anymore.
          DIALOG.dispose();
          System.exit(1);
        });
    JButton reviewButton = new JButton("Review");
    reviewButton.addActionListener(
        e -> {
          JTextArea textArea = new JTextArea(info.toBugReport());
          textArea.setColumns(50);
          textArea.setEditable(false);
          textArea.selectAll();
          textArea.copy();
          textArea.setCaretPosition(0);
          JScrollPane scroller = new JScrollPane(textArea);
          scroller.setBorder(BorderFactory.createEtchedBorder());
          scroller.setPreferredSize(new Dimension(500, 200));
          showMessage(DIALOG, scroller);
        });
    JButton discardButton = new JButton("Discard");
    discardButton.addActionListener(
        e -> {
          DIALOG.dispose();
          System.exit(1);
        });
    buttonPanel.add(sendButton);
    buttonPanel.add(reviewButton);
    buttonPanel.add(discardButton);
    mainPanel.add(labelPanel);
    mainPanel.add(buttonPanel);
    DIALOG.getContentPane().add(mainPanel);
    prepareDialog(DIALOG);
    try {
      SplashWindow.instance().setVisible(false);
    } catch (Throwable ignore) {
    }
    DIALOG.toFront();
  }

  public static void prepareDialog(JDialog dialog) {
    dialog.pack();
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension dialogSize = dialog.getSize();
    dialog.setLocation(
        (screenSize.width - dialogSize.width) / 2, (screenSize.height - dialogSize.height) / 2);
    dialog.setVisible(true);
  }

  /** Shows a message. */
  private static void showMessage(Component parent, Component toDisplay) {
    JOptionPane.showMessageDialog(
        parent, toDisplay, "Fatal Error - Review", JOptionPane.INFORMATION_MESSAGE);
  }
}
