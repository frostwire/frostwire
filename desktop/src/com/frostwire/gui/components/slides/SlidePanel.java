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

package com.frostwire.gui.components.slides;

import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * The Slide panel which has the image and controls.
 * Contained by {@link MultimediaSlideshowPanel}
 *
 * @author gubatron
 * @author aldenml
 */
class SlidePanel extends JPanel {
    private final int index;
    private final SlidePanelController controller;
    private JLabel imageLabel;
    private SlideControlsOverlay overlayControls;

    public SlidePanel(Slide slide, int index) {
        this.index = index;
        controller = new SlidePanelController(slide);
        setupUI();
    }

    private void setupUI() {
        setLayout(new BorderLayout());
        setupImageArea();
    }

    private void setupImageArea() {
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setMinimumSize(new Dimension(717, 380));
        layeredPane.setPreferredSize(new Dimension(717, 380));
        //layeredPane.setMaximumSize(new Dimension(717,380));
        layeredPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                overlayControls.setVisible(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!imageLabel.getBounds().contains(e.getPoint())) {
                    overlayControls.setVisible(false);
                }
            }
        });
        imageLabel = new JLabel();
        overlayControls = new SlideControlsOverlay(controller);
        overlayControls.setVisible(false);
        if (controller.getSlide().method != Slide.SLIDE_DOWNLOAD_METHOD_OPEN_URL) {
            layeredPane.add(overlayControls, Integer.valueOf(1));
        } else {
            imageLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    //System.out.println(e);
                    //System.out.println(controller.getSlide().method);
                    if (controller.getSlide().method == Slide.SLIDE_DOWNLOAD_METHOD_OPEN_URL) {
                        controller.downloadSlide();
                    }
                }
            });
        }
        layeredPane.add(imageLabel, Integer.valueOf(0));
        add(layeredPane, BorderLayout.CENTER);
        try {
            ImageCache.instance().getImage(new URL(controller.getSlide().imageSrc), (url, image, fromCache, fail) -> GUIMediator.safeInvokeLater(() -> {
                if (image != null) {
                    imageLabel.setIcon(new ImageIcon(image));
                    imageLabel.setBounds(0, 0, image.getWidth(), image.getHeight());
                    overlayControls.setBounds(0, 0, image.getWidth(), image.getHeight());
                }
            }));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    public int getIndex() {
        return index;
    }

    public boolean isOverlayVisible() {
        return overlayControls.isVisible();
    }

    public Slide getSlide() {
        return controller.getSlide();
    }
}
