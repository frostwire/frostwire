/*
 *
 * Copyright (c) 1999-2003 Werner Randelshofer
 * Staldenmattweg 2, Immensee, CH-6405, Switzerland
 * All rights reserved.
 *
 * This material is provided "as is", with absolutely no warranty expressed
 * or implied. Any use is at your own risk.
 *
 * Permission to use or copy this software is hereby granted without fee,
 * provided this copyright notice is retained on all copies.
 */
package com.limegroup.gnutella.gui;

import java.awt.*;

/**
 * Splash Window to show an image during startup of an application.<p>
 * <p>
 * Usage:
 * <pre>
 * // open the splash window
 * Frame splashOwner = AWTSplashWindow.splash(anImage);
 *
 * // start the application
 * // ...
 *
 * // dispose the splash window by disposing the frame that owns the window.
 * splashOwner.dispose();
 * </pre>
 *
 * <p>To use the splash window as an about dialog write this:
 * <pre>
 *  new AWTSplashWindow(
 *      this,
 *      getToolkit().createImage(getClass().getResource("splash.png"))
 * ).show();
 * </pre>
 * <p>
 * The splash window disposes itself when the user clicks on it.
 *
 * @author Werner Randelshofer, Staldenmattweg 2, Immensee, CH-6405, Switzerland.
 * @version 1.3 2003-06-01 Revised.
 */
public class AWTSplashWindow extends Window {
    /**
     *
     */
    private static final long serialVersionUID = 720067738309164784L;
    private final Image splashImage;
    /**
     * This attribute indicates whether the method
     * paint(Graphics) has been called at least once since the
     * construction of this window.<br>
     * This attribute is used to notify method splash(Image)
     * that the window has been drawn at least once
     * by the AWT event dispatcher thread.<br>
     * This attribute acts like a latch. Once set to true,
     * it will never be changed back to false again.
     *
     * @see #paint
     * @see #splash
     */
    private volatile boolean paintCalled = false;

    /**
     * Constructs a splash window and centers it on the
     * screen. The user can click on the window to dispose it.
     *
     * @param owner       The frame owning the splash window.
     * @param splashImage The splashImage to be displayed.
     */
    private AWTSplashWindow(Frame owner, Image splashImage) {
        super(owner);
        this.splashImage = splashImage;
        // Center the window on the screen, and force the image to have a size,
        // otherwise paint will never be called.
        int imgWidth = splashImage.getWidth(this);
        if (imgWidth < 1)
            imgWidth = 1;
        int imgHeight = splashImage.getHeight(this);
        if (imgHeight < 1)
            imgHeight = 1;
        setSize(imgWidth, imgHeight);
        Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation(
                (screenDim.width - imgWidth) / 2,
                (screenDim.height - imgHeight) / 2
        );
    }

    /**
     * Constructs and displays a AWTSplashWindow.<p>
     * This method is useful for startup splashs.
     * Dispose the return frame to get rid of the splash window.<p>
     *
     * @param splashImage The image to be displayed.
     * @return Returns the frame that owns the AWTSplashWindow.
     */
    public static Frame splash(Image splashImage) {
        Frame f = new Frame();
        AWTSplashWindow w = new AWTSplashWindow(f, splashImage);
        // Show the window.
        w.toFront();
        w.setVisible(true);
        // Note: To make sure the user gets a chance to see the
        // splash window we wait until its paint method has been
        // called at least by the AWT event dispatcher thread.
        if (!EventQueue.isDispatchThread()) {
            synchronized (w) {
                while (!w.paintCalled) {
                    try {
                        w.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
        return f;
    }

    /**
     * Updates the display area of the window.
     */
    public void update(Graphics g) {
        // Note: Since the paint method is going to draw an
        // image that covers the complete area of the component we
        // do not fill the component with its background color
        // here. This avoids flickering.
        g.setColor(getForeground());
        paint(g);
    }

    /**
     * Paints the image on the window.
     */
    public void paint(Graphics g) {
        g.drawImage(splashImage, 0, 0, this);
        // Notify method splash that the window
        // has been painted.
        // Note: To improve performance we do not enter
        // the synchronized block unless we have to.
        if (!paintCalled) {
            paintCalled = true;
            synchronized (this) {
                notifyAll();
            }
        }
    }
}
