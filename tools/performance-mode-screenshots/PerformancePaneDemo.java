import com.frostwire.gui.PerformanceMode;
import com.frostwire.gui.theme.ThemeMediator;
import com.limegroup.gnutella.gui.options.panes.PerformancePaneItem;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Screenshots for frostwire/frostwire#1555: only the Options > Advanced > Performance pane, in a
 * frame of its own, captured with java.awt.Robot. Not part of the app.
 *
 * <p>Usage: PerformancePaneDemo default|dark lite|full out.png
 */
public class PerformancePaneDemo {
  public static void main(String[] args) throws Exception {
    ThemeMediator.ThemeEnum theme =
        args[0].equals("dark")
            ? ThemeMediator.ThemeEnum.DARK_FLAT_LAF
            : ThemeMediator.ThemeEnum.DEFAULT;
    boolean lite = args[1].equals("lite");
    File out = new File(args[2]);
    // Automatic, deciding as it would on a 2-CPU machine (Lite) or an 8-CPU one (Full).
    PerformanceMode.resolve(
        PerformanceMode.Choice.AUTOMATIC,
        lite
            ? new PerformanceMode.MachineFacts(2, 3800, 600, 45_000)
            : new PerformanceMode.MachineFacts(8, 16384, 8192, 2_500));
    ThemeMediator.loadThemeAtStartup(theme);
    JFrame[] frame = new JFrame[1];
    SwingUtilities.invokeAndWait(
        () -> {
          PerformancePaneItem pane = new PerformancePaneItem();
          pane.initOptions();
          JFrame f = new JFrame("Options > Advanced > Performance");
          frame[0] = f;
          JPanel content = new JPanel(new BorderLayout());
          content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
          content.add((Component) pane.getContainer(), BorderLayout.NORTH);
          f.setContentPane(content);
          f.setSize(720, 330);
          f.setLocation(60, 60);
          f.setVisible(true);
          f.toFront();
        });
    Thread.sleep(3000);
    Rectangle bounds = frame[0].getBounds();
    BufferedImage shot = new Robot().createScreenCapture(bounds);
    ImageIO.write(shot, "png", out);
    System.out.println("wrote " + out + " " + bounds);
    System.exit(0);
  }
}
