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

package com.frostwire.gui.components.slides;

import com.limegroup.gnutella.gui.GUIMediator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

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
            ImageCache.instance().getImage(new java.net.URI(controller.getSlide().imageSrc).toURL(), (url, image, fromCache, fail) -> GUIMediator.safeInvokeLater(() -> {
                if (image != null) {
                    imageLabel.setIcon(new ImageIcon(image));
                    imageLabel.setBounds(0, 0, image.getWidth(), image.getHeight());
                    overlayControls.setBounds(0, 0, image.getWidth(), image.getHeight());
                }
            }));
        } catch (MalformedURLException | URISyntaxException e) {
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
