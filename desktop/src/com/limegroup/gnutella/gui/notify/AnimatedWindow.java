/*
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

package com.limegroup.gnutella.gui.notify;

import java.awt.AWTException;
import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.Timer;
import javax.swing.UIManager;

import com.frostwire.logging.Logger;

public class AnimatedWindow extends JWindow {

    /**
     * 
     */
    private static final long serialVersionUID = 1764619610298463956L;

    private final static int ANIMATION_INTERVAL = 10;

    private final static int MOVE_PER_INTERVAL = 1;

    private final static float FADE_PER_INTERVAL = 0.015f;

    private static final Logger LOG = Logger.getLogger(AnimatedWindow.class);

    public static final Color TRANSPARENT = new Color(255, 255, 255, 0);

    public enum AnimationType { SHOW, HIDE };

    public enum AnimationMode {
        TOP_TO_BOTTOM, BOTTOM_TO_TOP, FADE;

        public boolean movesWindow() {
            switch (this) {
            case TOP_TO_BOTTOM:
            case BOTTOM_TO_TOP:
                return true;
            case FADE:
                return false;
            default:
                throw new RuntimeException("Unknown mode: " + this);
            }
        }

        public boolean needsBackgroundImage() {
            switch (this) {
            case FADE:
                return true;
            default:
                return false;
            }
        }

        public void initializeWindow(AnimatedWindow window, Point location) {
            Dimension preferredSize = window.getPreferredSize();

            switch (this) {
            case BOTTOM_TO_TOP:
                // minimizes flickering: the window is resized by calls to
                // setBounds() which immediately cause a resize of the peer window
                // filling the empty space with the peer background color before
                // paint() is invoked on any contained lightweight components.
                if (window.getContentBackground() != null) {
                    window.setBackground(window.getContentBackground());
                }

                // start animation with a small initial height
                window.setBounds(location.x, location.y + preferredSize.height
                        - window.getInitialHeight(), preferredSize.width,
                        window.getInitialHeight());
                window.setOpacity2(1.0f);
                break;
            case TOP_TO_BOTTOM:
                // see above
                if (window.getContentBackground() != null) {
                    window.setBackground(window.getContentBackground());
                }

                // see above
                window.setBounds(location.x, location.y, preferredSize.width,
                        window.getInitialHeight());
                window.setOpacity2(1.0f);
                break;
            case FADE:
                window.setBounds(location.x, location.y, preferredSize.width,
                        preferredSize.height);
                window.setOpacity2(0.0f);
                window.setBackground(TRANSPARENT);
                break;
            }
        }

        public boolean animateShow(AnimatedWindow window, Point location) {
            Dimension preferredSize = window.getPreferredSize();
            switch (this) {
            case BOTTOM_TO_TOP:
                if (window.getHeight() >= preferredSize.height) {
                    return true;
                }

                int newHeight = Math.min(
                        window.getHeight() + MOVE_PER_INTERVAL,
                        preferredSize.height);
                window.setBounds(location.x, location.y + preferredSize.height
                        - newHeight, preferredSize.width, newHeight);
                // invoking repaint() is not sufficient since
                // mainPanel needs to be resized
                window.validate();
                return false;
            case TOP_TO_BOTTOM:
                if (window.getHeight() >= preferredSize.height) {
                    return true;
                }

                window.setBounds(location.x, location.y, preferredSize.width,
                        Math.min(window.getHeight() + MOVE_PER_INTERVAL,
                                preferredSize.height));
                // invoking repaint() is not sufficient since
                // mainPanel needs to be resized
                window.validate();
                return false;
            case FADE:
                if (window.getOpacity2() >= 1.0f) {
                    return true;
                }
                window.setOpacity2(Math.min(window.getOpacity2()
                        + FADE_PER_INTERVAL, 1.0f));
                window.repaint();
                return false;
            default:
                return true;
            }
        }

        public boolean animateHide(AnimatedWindow window, Point location) {
            Dimension preferredSize = window.getPreferredSize();

            switch (this) {
            case BOTTOM_TO_TOP:
                if (window.getHeight() == 0) {
                    return true;
                }

                int newHeight = Math.max(
                        window.getHeight() - MOVE_PER_INTERVAL, 0);
                try {
                    window.setBounds(location.x, location.y + preferredSize.height
                            - newHeight, preferredSize.width, newHeight);
                } catch(IllegalArgumentException ignored) {
                    // See: LWC-1167.
                    LOG.warn("IAE setting bounds", ignored);
                }
                window.validate();
                return false;
            case TOP_TO_BOTTOM:
                if (window.getHeight() == 0) {
                    return true;
                }

                window.setSize(preferredSize.width, Math.max(window.getHeight()
                        - MOVE_PER_INTERVAL, 0));
                window.validate();
                return false;
            case FADE:
                if (window.getOpacity2() == 0.0f) {
                    return true;
                }
                window.setOpacity2(Math.max(window.getOpacity2()
                        - FADE_PER_INTERVAL, 0.0f));
                window.repaint();
                return false;
            default:
                return true;
            }
        }

        public void paint(AnimatedWindow window, Graphics g,
                BufferedImage backgroundImage, BufferedImage animationImage) {
            switch (this) {
            case BOTTOM_TO_TOP:
                g.drawImage(animationImage, 0, 0, null);
                break;
            case TOP_TO_BOTTOM:
                g.drawImage(animationImage, 0, window.getHeight()
                        - window.getPreferredSize().height, null);
                break;
            case FADE:
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, null);
                } else {
                    g.setColor(Color.white);
                    g.fillRect(0, 0, window.getWidth(), window.getHeight());
                }
                Graphics2D gFade = (Graphics2D) g.create();
                AlphaComposite newComposite = AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, window.getOpacity2());
                gFade.setComposite(newComposite);
                gFade.drawImage(animationImage, 0, 0, null);
                gFade.dispose();
                break;
            }
        }
    };

    /**
     * If true, the animation will display window from bottom to top. If false,
     * window will be displayed from top to bottom.
     */
    private AnimationMode mode = AnimationMode.BOTTOM_TO_TOP;

    /**
     * Timer used to for the show/hide animation.
     */
    private Timer animationTimer;

    private BufferedImage animationImage;

    private boolean hideOnClick;

    private int initialHeight;

    private Point finalLocation;

    private float opacity;

    private BufferedImage backgroundImage;

    private List<AnimatedWindowListener> listeners;

    private AnimationType currentAnimation;

    private Container contentPane;
    
    public AnimatedWindow(Window parent) {
        super(parent);
    }

    public Color getContentBackground() {
        return getContentPane().getBackground();
    }

    protected void autoHideWindow() {
        doHide();
    }

    public void doHide() {
        if (currentAnimation == AnimationType.HIDE) {
            return;
        }

        stopAnimations(true);

        final Point location = getLocation();

        prepareAnimation(location);
        currentAnimation = AnimationType.HIDE;

        animationTimer = new javax.swing.Timer(ANIMATION_INTERVAL,
                new ActionListener() {
                    public void actionPerformed(ActionEvent event) {
                        boolean done = mode.animateHide(AnimatedWindow.this,
                                location);
                        if (done) {
                            currentAnimation = null;
                            animationTimer.stop();

                            disposeAnimation();
                            
                            setVisible(false);
                            
                            fireAnimationCompleted(AnimationType.HIDE);
                        }
                    }
                });
        animationTimer.setRepeats(true);
        animationTimer.start();
    }

    public void doShow() {
        if (currentAnimation == AnimationType.SHOW) {
            return;
        }

        stopAnimations(true);

        final Dimension preferredSize = getPreferredSize();
        final Point location = getFinalLocation();

        prepareAnimation(location);

        if (!isVisible()) {
            mode.initializeWindow(this, location);

            this.setVisible(true);
        }

        currentAnimation = AnimationType.SHOW;

        animationTimer = new javax.swing.Timer(ANIMATION_INTERVAL,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        boolean done = mode.animateShow(AnimatedWindow.this,
                                location);
                        if (done) {
                            // make sure window is displayed at final location
                            if (location.y != getLocation().y
                                    || preferredSize.height != getSize()
                                            .getHeight()) {
                                setBounds(location.x, location.y,
                                        preferredSize.width,
                                        preferredSize.height);
                            }

                            animationImage = null;
                            
                            stopAnimations(false);

                            // ask Swing to paint the actual components
                            validate();
                            
                            fireAnimationCompleted(AnimationType.SHOW);
                        }
                    }
                });
        animationTimer.setRepeats(true);
        animationTimer.start();
    }

    public int getInitialHeight() {
        return initialHeight;
    }

    public void setInitialHeight(int initialHeight) {
        this.initialHeight = initialHeight;
    }

    private void prepareAnimation(Point location) {
        if (animationImage != null) {
            // already have an image, this could be the case if a hide animation
            // is started while a show animation is still in progress or the
            // other
            // way around
            return;
        }

        // reset window to original size to capture all content
        pack();
        Dimension size = getSize();

        if (mode.needsBackgroundImage() && backgroundImage == null) {
            try {
                Robot robot = new Robot();
                backgroundImage = robot.createScreenCapture(new Rectangle(
                        location, size));
            } catch (AWTException e) {
                LOG.warn("Could not capture background image", e);
                backgroundImage = null;
            }
        }

        animationImage = getGraphicsConfiguration().createCompatibleImage(
                size.width, size.height);
        Graphics grahpics = animationImage.getGraphics();
        getContentPane().paint(grahpics);
        grahpics.dispose();
    }

    private void disposeAnimation() {
        animationImage = null;
        backgroundImage = null;
    }

    /**
     * Returns the lower right corner of the screen.
     */
    public Point getDefaultParentLocation() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return new Point(screenSize.width - 1, screenSize.height - 1);
    }

    /**
     * Returns (0, 0).
     */
    public Dimension getDefaultParentSize() {
        return new Dimension(0, 0);
    }

    /**
     * Hides the window slowly using an animation.
     */
    public void hideWindow() {
        doHide();
    }

    /**
     * Hides the window immediately not using an animation.
     */
    public void hideWindowImmediately() {
        stopAnimations(true);

        setVisible(false);

        disposeAnimation();
    }

    @Override
    public Container getContentPane() {
        if (this.contentPane == null) {
            setContentPane(new JPanel());
        }
        return this.contentPane;
    }
    
    @Override
    public void setContentPane(Container contentPane) {
        this.contentPane = contentPane;
        JPanel panel = new JPanel(new BorderLayout()) {
            /**
             * 
             */
            private static final long serialVersionUID = 1025231305407376307L;

            @Override
            public void paint(Graphics g) {
                if (animationImage != null && isAnimationInProgress()) {
                    mode.paint(AnimatedWindow.this, g, backgroundImage,
                            animationImage);
                } else {
                    super.paint(g);
                }
            }
        };
        panel.setDoubleBuffered(true);
        panel.add(contentPane, BorderLayout.CENTER);
        super.setContentPane(panel);
    }

    public boolean isAnimationInProgress() {
        return currentAnimation != null;
    }

    public boolean isHideAnimationInProgress() {
        return currentAnimation == AnimationType.HIDE;
    }

    public boolean isShowAnimationInProgress() {
        return currentAnimation == AnimationType.SHOW;
    }

    private void stopAnimations(boolean notify) {
        if (currentAnimation == null) {
            return;
        }
        
        currentAnimation = null;

        if (animationTimer != null) {
            animationTimer.stop();
        }
        
        if (notify) {
            fireAnimationStopped(currentAnimation);
        }
    }

    protected void fireAnimationStopped(AnimationType type) {
        if (listeners != null) {
            AnimatedWindowEvent event = new AnimatedWindowEvent(this, type);
            for (AnimatedWindowListener listener : listeners) {
                listener.animationStopped(event);
            }
        }
    }


    protected void fireAnimationCompleted(AnimationType type) {
        if (listeners != null) {
            AnimatedWindowEvent event = new AnimatedWindowEvent(this, type);
            for (AnimatedWindowListener listener : listeners) {
                listener.animationCompleted(event);
            }
        }
    }

    public boolean getHideOnClick() {
        return hideOnClick;
    }

    public void setHideOnClick(boolean hideOnClick) {
        this.hideOnClick = hideOnClick;
    }

    public AnimationMode getMode() {
        return mode;
    }

    public void setMode(AnimationMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException();
        }

        this.mode = mode;
    }

    public static void main(String[] args) {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createLineBorder(Color.black, 2));
        JLabel label = new JLabel("Hello World");
        label.setIcon(UIManager.getIcon("FileView.computerIcon"));
        label.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        content.add(label, BorderLayout.CENTER);

        final AnimatedWindow window = new AnimatedWindow(null);
        window.setFinalLocation(new Point(200, 200));
        window.setContentPane(content);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 10,
                10));

        JButton button = new JButton("Bottom -> Top");
        buttonPanel.add(button);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                window.setMode(AnimationMode.BOTTOM_TO_TOP);
                if (!window.isVisible() || window.isHideAnimationInProgress()) {
                    window.doShow();
                } else {
                    window.doHide();
                }
            }
        });

        button = new JButton("Top -> Bottom");
        buttonPanel.add(button);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                window.setMode(AnimationMode.TOP_TO_BOTTOM);
                if (!window.isVisible() || window.isHideAnimationInProgress()) {
                    window.doShow();
                } else {
                    window.doHide();
                }
            }
        });

        button = new JButton("Fade");
        buttonPanel.add(button);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                window.setMode(AnimationMode.FADE);
                if (!window.isVisible() || window.isHideAnimationInProgress()) {
                    window.doShow();
                } else {
                    window.doHide();
                }
            }
        });

        JFrame app = new JFrame("AnimatedWindow Demo");
        app.setContentPane(buttonPanel);
        app.pack();
        app.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        app.setVisible(true);
    }

    public Point getFinalLocation() {
        return finalLocation;
    }

    public void setFinalLocation(Point finalLocation) {
        this.finalLocation = finalLocation;
    }

    public float getOpacity2() {
        return opacity;
    }

    public void setOpacity2(float opacity) {
        if (opacity < 0.0f || opacity > 1.0f) {
            throw new IllegalArgumentException();
        }

        this.opacity = opacity;
    }

    public void addAnimatedWindowListener(AnimatedWindowListener listener) {
        if (listeners == null) {
            listeners = new ArrayList<AnimatedWindowListener>();
        }
        listeners.add(listener);
    }
    
    public void removeAnimatedWindowListener(AnimatedWindowListener listener) {
        if (listeners != null) {
            listeners.remove(listener);
        }
    }
}