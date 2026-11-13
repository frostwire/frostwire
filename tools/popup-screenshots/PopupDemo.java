import com.frostwire.gui.theme.ThemeMediator;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

/**
 * Screenshots for frostwire/frostwire#1552: FrostWire's skin with a menu, a right-click menu or a
 * tooltip open, with popups heavyweight (as before) or lightweight (as after). Not part of the app.
 * Usage: PopupDemo heavy|light menu|context|tooltip out.png
 */
public class PopupDemo {
  public static void main(String[] args) throws Exception {
    boolean heavy = args[0].equals("heavy");
    String what = args[1];
    File out = new File(args[2]);
    ThemeMediator.loadThemeAtStartup(ThemeMediator.ThemeEnum.DEFAULT);
    if (heavy) { // what Main.main() and MenuMediator did before
      ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
      JPopupMenu.setDefaultLightWeightPopupEnabled(false);
    }
    JFrame[] frame = new JFrame[1];
    SwingUtilities.invokeAndWait(() -> {
      JFrame f = new JFrame("FrostWire popups: " + (heavy ? "heavyweight (before)" : "lightweight (after)"));
      frame[0] = f;
      JMenuBar bar = new JMenuBar();
      JMenu file = new JMenu("File");
      file.add(new JMenuItem("Open .Torrent or Magnet"));
      file.add(new JMenuItem("Create New Torrent"));
      file.addSeparator();
      file.add(new JMenuItem("Exit"));
      bar.add(file);
      bar.add(new JMenu("View"));
      bar.add(new JMenu("Help"));
      f.setJMenuBar(bar);
      DefaultTableModel model = new DefaultTableModel(new Object[] {"Name", "Size", "Status"}, 0);
      model.addRow(new Object[] {"ubuntu-26.04-desktop-amd64.iso", "6.1 GB", "Seeding"});
      model.addRow(new Object[] {"Creative Commons album", "552 MB", "Downloading 42%"});
      model.addRow(new Object[] {"big_buck_bunny_1080p.mp4", "885 MB", "Finished"});
      JTable table = new JTable(model);
      table.setToolTipText("Seeding: 12 peers, 1.2 MB/s up");
      f.add(new JScrollPane(table));
      f.setSize(640, 360);
      f.setLocation(60, 60);
      f.setVisible(true);
      f.toFront();
      new javax.swing.Timer(1500, e -> {
        ((javax.swing.Timer) e.getSource()).stop();
        switch (what) {
          case "menu" -> file.doClick();
          case "context" -> {
            JPopupMenu ctx = new JPopupMenu();
            ctx.add(new JMenuItem("Resume Download"));
            ctx.add(new JMenuItem("Pause Download"));
            ctx.add(new JMenuItem("Remove"));
            ctx.addSeparator();
            ctx.add(new JMenuItem("Copy Magnet"));
            ctx.show(table, 240, 40);
          }
          case "tooltip" -> {
            ToolTipManager.sharedInstance().setInitialDelay(0);
            ToolTipManager.sharedInstance().setDismissDelay(60000);
            long now = System.currentTimeMillis();
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_ENTERED, now, 0, 200, 30, 0, false));
            table.dispatchEvent(new MouseEvent(table, MouseEvent.MOUSE_MOVED, now, 0, 200, 30, 0, false));
          }
          default -> throw new IllegalArgumentException(what);
        }
      }).start();
    });
    Thread.sleep(4000);
    Rectangle bounds = frame[0].getBounds();
    BufferedImage shot = new Robot().createScreenCapture(bounds);
    ImageIO.write(shot, "png", out);
    System.out.println("wrote " + out + " " + bounds);
    System.exit(0);
  }
}
