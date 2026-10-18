package com.limegroup.gnutella.gui.dnd;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.IconManager;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.*;
import org.apache.commons.io.FilenameUtils;

/**
 * A simple visualizer for displaying drags.
 *
 * <p>Currently supports only a list of files.
 */
class TransferVisualizer {
  /** The pane to use for drawing the Drag image. */
  private static final CellRendererPane PANE = new CellRendererPane();

  /** The maximum width of the drag image. */
  private static final int IMAGE_WIDTH = 300;

  /** The height of each row in the drag image. */
  private static final int IMAGE_ROW_HEIGHT = 16;

  /** Rows drawn in the drag image; the last one mentions any files beyond it. */
  static final int MAX_IMAGE_ROWS = 8;

  /** The transferable this is wrapping. */
  private final Transferable t;

  TransferVisualizer(Transferable t) {
    this.t = t;
  }

  Image getImage() {
    if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return null;
    List<?> l = null;
    try {
      l = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
    } catch (UnsupportedFlavorException | IOException ufe) {
      return null;
    }
    // if no data, there's nothing to draw.
    if (l.size() == 0) return null;
    // This runs on the EDT when a drag starts: draw a few rows only, with cached or
    // per-extension icons, instead of a native icon lookup for every dragged file.
    int rows = rowCount(l.size());
    int height = IMAGE_ROW_HEIGHT * rows;
    BufferedImage buffer = new BufferedImage(IMAGE_WIDTH, height, BufferedImage.TYPE_INT_ARGB);
    Graphics g = buffer.getGraphics();
    JLabel label = new JLabel();
    label.setVerticalAlignment(SwingConstants.TOP);
    label.setOpaque(false);
    IconManager icons = IconManager.instance();
    int y = 0;
    for (int i = 0; i < rows; i++) {
      File f = (File) l.get(i);
      label.setIcon(
          icons.isIconForFileAvailable(f)
              ? icons.getIconForFile(f)
              : icons.getIconForExtension(FilenameUtils.getExtension(f.getName())));
      label.setText(rowText(f.getName(), i, rows, l.size()));
      PANE.paintComponent(g, label, null, 0, y, IMAGE_WIDTH, height - y);
      y += IMAGE_ROW_HEIGHT;
    }
    g.dispose();
    return buffer;
  }

  /** Number of rows the drag image shows for {@code fileCount} dragged files. */
  static int rowCount(int fileCount) {
    return Math.min(fileCount, MAX_IMAGE_ROWS);
  }

  /** The last row mentions the files that are not drawn. */
  static String rowText(String fileName, int row, int rows, int fileCount) {
    int hidden = fileCount - rows;
    if (row == rows - 1 && hidden > 0) {
      return fileName + " " + I18n.tr("(and {0} more)", hidden);
    }
    return fileName;
  }
}
