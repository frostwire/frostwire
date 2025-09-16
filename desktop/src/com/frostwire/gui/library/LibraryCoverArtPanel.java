/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
