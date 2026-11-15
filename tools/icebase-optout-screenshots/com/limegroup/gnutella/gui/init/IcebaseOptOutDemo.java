package com.limegroup.gnutella.gui.init;

import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IcebaseReportsText;
import com.limegroup.gnutella.gui.Line;
import com.limegroup.gnutella.gui.options.panes.IcebaseReportsPaneItem;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Screenshots for frostwire/frostwire#1565: the setup wizard's "Seeding & Publishing" page with the
 * Icebase reports choice, and the Options pane that turns it on or off. Builds only that page or
 * pane in its own frame and captures only that frame. Not part of the app.
 *
 * <p>Usage: IcebaseOptOutDemo wizard|options out.png [language tag, e.g. es or ar]
 */
public class IcebaseOptOutDemo {
  public static void main(String[] args) throws Exception {
    String what = args[0];
    File out = new File(args[1]);
    if (args.length > 2) {
      I18n.setLocale(java.util.Locale.forLanguageTag(args[2]));
    }
    ThemeMediator.loadThemeAtStartup(ThemeMediator.ThemeEnum.DEFAULT);
    JFrame[] frame = new JFrame[1];
    SwingUtilities.invokeAndWait(
        () -> {
          JFrame f = new JFrame();
          frame[0] = f;
          f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
          switch (what) {
            case "wizard" -> {
              f.setTitle(I18n.tr("FrostWire Setup Wizard"));
              f.setContentPane(wizard());
            }
            case "options" -> {
              f.setTitle(I18n.tr("Options") + " - " + IcebaseReportsText.optionsNodeTitle());
              f.setContentPane(options());
            }
            default -> throw new IllegalArgumentException(what);
          }
          f.pack();
          f.setLocation(40, 40);
          f.setVisible(true);
          f.toFront();
        });
    Thread.sleep(3000);
    SwingUtilities.invokeAndWait(() -> report(frame[0].getContentPane(), ""));
    Rectangle bounds = frame[0].getBounds();
    BufferedImage shot = new Robot().createScreenCapture(bounds);
    ImageIO.write(shot, "png", out);
    System.out.println("wrote " + out + " " + bounds);
    System.exit(0);
  }

  /** The wizard's page area, its separator line and a button row, at the dialog's fixed size. */
  private static JComponent wizard() {
    SetupManager manager = new SetupManager(() -> null);
    SeedingPublishingWindow page = new SeedingPublishingWindow(manager);
    page.createWindow();
    JPanel setupPanel = new JPanel();
    setupPanel.setLayout(new BoxLayout(setupPanel, BoxLayout.Y_AXIS));
    setupPanel.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, 0));
    setupPanel.add(page);
    setupPanel.add(Box.createVerticalStrut(17));
    setupPanel.add(new Line());
    JPanel bottomRow = new JPanel();
    bottomRow.setLayout(new BoxLayout(bottomRow, BoxLayout.X_AXIS));
    bottomRow.add(Box.createHorizontalGlue());
    bottomRow.add(new JButton(I18n.tr("Back")));
    bottomRow.add(Box.createHorizontalStrut(6));
    bottomRow.add(new JButton(I18n.tr("Next")));
    bottomRow.add(Box.createHorizontalStrut(6));
    bottomRow.add(new JButton(I18n.tr("Cancel")));
    bottomRow.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
    setupPanel.add(bottomRow);
    JPanel container = new JPanel(new BorderLayout());
    container.add(setupPanel, BorderLayout.CENTER);
    System.out.println("wizard page preferred size: " + page.getPreferredSize());
    container.setPreferredSize(new Dimension(SetupWindow.SETUP_WIDTH, SetupWindow.SETUP_HEIGHT));
    return container;
  }

  /** The pane as the Options dialog shows it, on the right of the tree. */
  private static JComponent options() {
    IcebaseReportsPaneItem item = new IcebaseReportsPaneItem();
    item.initOptions();
    JPanel container = new JPanel(new BorderLayout());
    container.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    container.add(item.getContainer(), BorderLayout.NORTH);
    container.setPreferredSize(new Dimension(560, 380));
    return container;
  }

  private static void report(Component c, String indent) {
    if (indent.length() > 8) return;
    System.out.println(indent + c.getClass().getSimpleName() + " " + c.getBounds());
    if (c instanceof Container container) {
      for (Component child : container.getComponents()) {
        report(child, indent + "  ");
      }
    }
  }
}
