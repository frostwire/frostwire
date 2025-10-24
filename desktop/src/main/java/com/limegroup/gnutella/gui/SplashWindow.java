/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.OSUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Window that displays the splash screen.  This loads the splash screen
 * image, places it on the center of the screen, and allows dynamic
 * updating of the status text for loading the application.
 */
public final class SplashWindow {
    /**
     * The sole instance of the SplashWindow
     */
    private static SplashWindow INSTANCE;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    /**
     * Constant handle to the glass pane that handles drawing text
     * on top of the splash screen.
     */
    private volatile StatusComponent glassPane;
    /**
     * The JWindow the splash uses.
     */
    private volatile JWindow splashWindow;

    /**
     * Returns the single instance of the SplashWindow.
     */
    public static synchronized SplashWindow instance() {
        if (INSTANCE == null) {
            INSTANCE = new SplashWindow();
        }
        return INSTANCE;
    }

    /**
     * Determines if the splash is constructed.
     */
    static synchronized boolean isSplashConstructed() {
        return INSTANCE != null;
    }

    private void initialize() {
        glassPane = new StatusComponent(15);
        var splashLabel = new JLabel() {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                paintOSIcons(g);
            }
        };
        splashWindow = new JWindow();
        glassPane.setProgressPreferredSize(new Dimension(400, 17));
        glassPane.add(Box.createVerticalGlue(), 0);
        glassPane.add(Box.createVerticalStrut(6));
        //glassPane.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        URL imageURL = null;
        try {
            imageURL = Main.getChosenSplashURL();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (imageURL != null) {
            Image splashImage = null;
            try {
                splashImage = ImageIO.read(imageURL);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (splashImage != null) {
                int imgWidth = splashImage.getWidth(null);
                if (imgWidth < 1)
                    imgWidth = 1;
                int imgHeight = splashImage.getHeight(null);
                if (imgHeight < 1)
                    imgHeight = 1;
                Dimension size = new Dimension(imgWidth + 2, imgHeight + 2);
                splashWindow.setSize(size);
                Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
                splashWindow.setLocation((screenSize.width - size.width) / 2, (screenSize.height - size.height) / 2);
                splashLabel.setIcon(new ImageIcon(splashImage));
                splashWindow.getContentPane().add(splashLabel, BorderLayout.CENTER);
                splashWindow.setGlassPane(glassPane);
                splashWindow.pack();
            }
        }
    }

    private void paintOSIcons(Graphics g) {
        try {
            paintOSIcon("windows", OSUtils.isWindows(), 10, 10, g); //+33px to the right each. (icons are 28x28)
            paintOSIcon("android", false, 43, 10, g);
            paintOSIcon("mac", OSUtils.isMacOSX(), 76, 10, g);
            paintOSIcon("linux", OSUtils.isLinux(), 109, 10, g);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void paintOSIcon(String osName, boolean on, int x, @SuppressWarnings("SameParameterValue") int y, Graphics g) throws Throwable {
        String prefix = "org/limewire/gui/images/";
        String suffix = "_desktop_splash.png";
        String on_off = on ? "on" : "off";
        URL macIconURL = ClassLoader.getSystemResource(prefix + osName + "_" + on_off + suffix);
        BufferedImage img = ImageIO.read(macIconURL);
        g.drawImage(img, x, y, null);
    }

    /**
     * Sets the Splash Window to be visible.
     */
    public void begin() {
        if (initialized.getAndSet(true))
            return;
        runLater(() -> {
            initialize();
            splashWindow.toFront();
            splashWindow.setVisible(true);
            glassPane.setVisible(true);
            setStatusText(I18n.tr("Loading FrostWire..."));
        });
    }

    /**
     * Sets the loading status text to display in the splash
     * screen window.
     *
     * @param text the text to display
     */
    void setStatusText(final String text) {
        runLater(() -> {
            glassPane.setText(text);
            // force a redraw so the status is shown immediately,
            // even if we're currently in the Swing thread.
            glassPane.paintImmediately(0, 0, glassPane.getWidth(), glassPane.getHeight());
        });
    }

    private void runLater(Runnable runner) {
        if (initialized.get())
            GUIMediator.safeInvokeAndWait(runner);
    }

    public void dispose() {
        runLater(() -> splashWindow.dispose());
    }

    public boolean isShowing() {
        return splashWindow != null && splashWindow.isShowing();
    }

    public void setVisible(final boolean b) {
        runLater(() -> splashWindow.setVisible(b));
    }

    void toBack() {
        runLater(() -> splashWindow.toBack());
    }
}

