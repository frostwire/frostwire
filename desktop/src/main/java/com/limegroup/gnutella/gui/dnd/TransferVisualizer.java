package com.limegroup.gnutella.gui.dnd;

import com.limegroup.gnutella.gui.IconManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * A simple visualizer for displaying drags.
 * <p>
 * Currently supports only a list of files.
 */
class TransferVisualizer {
    /**
     * The pane to use for drawing the Drag image.
     */
    private static final CellRendererPane PANE = new CellRendererPane();
    /**
     * The maximum width of the drag image.
     */
    private static final int IMAGE_WIDTH = 300;
    /**
     * The height of each row in the drag image.
     */
    private static final int IMAGE_ROW_HEIGHT = 16;
    /**
     * The transferable this is wrapping.
     */
    private final Transferable t;

    TransferVisualizer(Transferable t) {
        this.t = t;
    }

    Image getImage() {
        if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
            return null;
        List<?> l = null;
        try {
            l = (List<?>) t.getTransferData(DataFlavor.javaFileListFlavor);
        } catch (UnsupportedFlavorException | IOException ufe) {
            return null;
        }
        // if no data, there's nothing to draw.
        if (l.size() == 0)
            return null;
        int height = IMAGE_ROW_HEIGHT * l.size();
        BufferedImage buffer = new BufferedImage(
                IMAGE_WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = buffer.getGraphics();
        JLabel label = new JLabel();
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setOpaque(false);
        int y = 0;
        for (Object o : l) {
            File f = (File) o;
            Icon icon = IconManager.instance().getIconForFile(f);
            label.setIcon(icon);
            label.setText(f.getName());
            PANE.paintComponent(g, label, null, 0, y, IMAGE_WIDTH, height - y);
            y += IMAGE_ROW_HEIGHT;
        }
        g.dispose();
        return buffer;
    }
}
