package com.limegroup.gnutella.gui.dnd;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.swing.CellRendererPane;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

import com.limegroup.gnutella.gui.IconManager;

/**
 * A simple visualizer for displaying drags.
 * 
 * Currently supports only a list of files.
 */
class TransferVisualizer {
    
    /** The pane to use for drawing the Drag image. */
    private static final CellRendererPane PANE = new CellRendererPane();
    
    /** The maximum width of the drag image. */
    private static final int IMAGE_WIDTH = 300;
    
    /** The height of each row in the drag image. */
    private static final int IMAGE_ROW_HEIGHT = 16;
    
    /** The transferable this is wrapping. */
    private final Transferable t;

    TransferVisualizer(Transferable t) {
        this.t = t;
    }

    Image getImage() {
        if(!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
            return null;
            
        List<?> l = null;
        try {
            l = (List<?>)t.getTransferData(DataFlavor.javaFileListFlavor);
        } catch(UnsupportedFlavorException ufe) {
            return null;
        } catch(IOException ioe) {
            return null;
        }
        
        // if no data, there's nothing to draw.
        if(l.size() == 0)
            return null;

        int height = IMAGE_ROW_HEIGHT * l.size();
        BufferedImage buffer = new BufferedImage(
                    IMAGE_WIDTH, height, BufferedImage.TYPE_INT_ARGB);
        Graphics g = buffer.getGraphics();
        JLabel label = new JLabel();
        label.setVerticalAlignment(SwingConstants.TOP);
        label.setOpaque(false);
        int y = 0;
        for(Iterator<?> i = l.iterator(); i.hasNext(); ) {
            File f = (File)i.next();
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
