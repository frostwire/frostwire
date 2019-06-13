/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

import com.frostwire.gui.library.tags.TagsReader;
import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;

/**
 * @author gubatron
 * @author aldenml
 */
public final class LibraryCoverArtPanel extends JPanel {
    private final BufferedImage background;
    private final Image defaultCoverArt;
    private Image coverArtImage;
    private TagsReader tagsReader;

    LibraryCoverArtPanel() {
        background = new BufferedImage(350, 350, BufferedImage.TYPE_INT_ARGB);
        defaultCoverArt = GUIMediator.getThemeImage("default_cover_art").getImage();
        setTagsReader(null);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                setPrivateImage(coverArtImage);
            }
        });
    }

    public LibraryCoverArtPanel setTagsReader(TagsReader reader) {
        if (this.tagsReader != null && reader != null && this.tagsReader.equals(reader)) {
            return this;
        }
        this.tagsReader = reader;
        return this;
    }

    public void setDefault() {
        this.tagsReader = null;
        LibraryUtils.getExecutor().submit(() -> {
            Image image = retrieveImage();
            setPrivateImage(image);
        });
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        g.drawImage(background, 0, 0, null);
    }

    public void asyncRetrieveImage() {
        if (tagsReader == null) {
            System.err.println("LibraryCoverArtPanel.asyncFetchImage() aborted. No tagsReader set. Check your logic");
            return;
        }
        LibraryUtils.getExecutor().submit(() -> {
            Image image = retrieveImage();
            if (tagsReader.getFile() != null) {
                setPrivateImage(image);
            }
        });
    }

    /**
     * Synchronous.
     */
    private Image retrieveImage() {
        if (tagsReader == null || tagsReader.getFile() == null) {
            return defaultCoverArt;
        }
        return tagsReader.getArtwork();
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
