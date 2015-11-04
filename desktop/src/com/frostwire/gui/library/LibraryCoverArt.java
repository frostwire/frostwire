/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.gui.library;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.swing.JPanel;

import com.frostwire.gui.library.tags.TagsReader;
import com.limegroup.gnutella.gui.GUIMediator;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class LibraryCoverArt extends JPanel {

    private final BufferedImage background;
    private final Image defaultCoverArt;

    private Image coverArtImage;
    private File file;

    public LibraryCoverArt() {
        background = new BufferedImage(350, 350, BufferedImage.TYPE_INT_ARGB);
        defaultCoverArt = GUIMediator.getThemeImage("default_cover_art").getImage();
        setFile(null);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                setPrivateImage(coverArtImage);
            }
        });
    }

    /**
     * Async
     * @param file
     */
    public void setFile(final File file) {
        if (this.file != null && file != null && this.file.equals(file)) {
            return;
        }
        this.file = file;
        Thread t = new Thread(new Runnable() {
            public void run() {
                Image image = retrieveImage(file);
                if (file != null && file.equals(LibraryCoverArt.this.file)) {
                    setPrivateImage(image);
                }
            }
        }, "Cover Art extract");
        t.setDaemon(true);
        t.start();
    }

    public void setDefault() {
        this.file = null;
        new Thread(new Runnable() {
            public void run() {
                Image image = retrieveImage(file);
                setPrivateImage(image);
            }
        }).start();
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        g.drawImage(background, 0, 0, null);
    }

    /**
     * Synchronous.
     * @param file
     * @return
     */
    private Image retrieveImage(File file) {
        if (file == null) {
            return defaultCoverArt;
        }
        Image image = new TagsReader(file).getArtwork();

        return image;
    }

    private void setPrivateImage(Image image) {
        coverArtImage = image;

        if (coverArtImage == null) {
            coverArtImage = defaultCoverArt;
        }

        Graphics2D g2 = background.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        g2.setBackground(new Color(255, 255, 255, 0));
        g2.clearRect(0, 0, getWidth(), getHeight());

        g2.drawImage(coverArtImage, 0, 0, getWidth(), getHeight(), null);
        g2.dispose();

        repaint();
        getToolkit().sync();
    }
}