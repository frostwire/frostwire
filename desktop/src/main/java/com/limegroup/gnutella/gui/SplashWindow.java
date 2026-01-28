/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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
import com.limegroup.gnutella.gui.util.BackgroundExecutorService;
import org.limewire.collection.FixedsizeForgetfulHashMap;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
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
     * Cache for OS icons to avoid file I/O during painting.
     * Limited to 20 entries to prevent unbounded growth.
     */
    private final Map<String, BufferedImage> osIconCache = new FixedsizeForgetfulHashMap<>(20);

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

        // Preload OS icons to avoid file I/O during painting
        BackgroundExecutorService.schedule(this::preloadOSIcons);

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

    private void preloadOSIcons() {
        // Preload all OS icons into cache to avoid file I/O during paint
        try {
            preloadOSIcon("windows", OSUtils.isWindows());
            preloadOSIcon("android", false);
            preloadOSIcon("mac", OSUtils.isMacOSX());
            preloadOSIcon("linux", OSUtils.isLinux());
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void preloadOSIcon(String osName, boolean on) {
        String prefix = "org/limewire/gui/images/";
        String suffix = "_desktop_splash.png";
        String on_off = on ? "on" : "off";
        String cacheKey = osName + "_" + on_off;
        synchronized (osIconCache) {
            if (!osIconCache.containsKey(cacheKey)) {
                try {
                    URL iconURL = ClassLoader.getSystemResource(prefix + osName + "_" + on_off + suffix);
                    if (iconURL != null) {
                        BufferedImage img = ImageIO.read(iconURL);
                        if (img != null) {
                            osIconCache.put(cacheKey, img);
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
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

    private void paintOSIcon(String osName, boolean on, int x, @SuppressWarnings("SameParameterValue") int y, Graphics g) {
        String on_off = on ? "on" : "off";
        String cacheKey = osName + "_" + on_off;
        BufferedImage img;
        synchronized (osIconCache) {
            img = osIconCache.get(cacheKey);
        }
        if (img != null) {
            g.drawImage(img, x, y, null);
        }
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
            glassPane.repaint();
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

