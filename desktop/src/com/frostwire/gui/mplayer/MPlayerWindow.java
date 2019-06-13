/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Erich Pleny (erichpleny)
 * Copyright (c) 2012-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.mplayer;

import com.frostwire.gui.player.*;
import com.frostwire.mplayer.MediaPlaybackState;
import com.limegroup.gnutella.gui.LimeJFrame;
import org.limewire.util.OSUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.util.List;

public class MPlayerWindow extends JFrame {
    private static final long serialVersionUID = -9154474667503959284L;
    private static final int HIDE_DELAY = 3000;
    private final MediaPlayer player;
    private final ScreenSaverDisabler screenSaverDisabler;
    MPlayerComponent mplayerComponent;
    Component videoCanvas;
    private MPlayerOverlayControls overlayControls;
    private boolean isFullscreen = false;
    private AlphaAnimationThread animateAlphaThread;
    private Timer hideTimer;
    private Point2D prevMousePosition = null;
    private boolean handleVideoResize = true;
    private int visibleCounterFlag = 0;

    MPlayerWindow() {
        initializeUI();
        screenSaverDisabler = new ScreenSaverDisabler();
        player = MediaPlayer.instance();
        player.addMediaPlayerListener(new MediaPlayerAdapter() {
            @Override
            public void mediaOpened(MediaPlayer mediaPlayer, MediaSource mediaSource) {
                MPlayerWindow.this.setPlayerWindowTitle();
            }

            @Override
            public void stateChange(MediaPlayer audioPlayer, MediaPlaybackState state) {
                if (state == MediaPlaybackState.Playing && handleVideoResize) {
                    handleVideoResize = false;
                    resizeCanvas();
                }
                if (state != MediaPlaybackState.Playing) {
                    handleVideoResize = true;
                }
            }
        });
    }

    public static MPlayerWindow createMPlayerWindow() {
        if (OSUtils.isWindows()) {
            return new MPlayerWindowWin32();
        } else if (OSUtils.isLinux()) {
            return new MPlayerWindowLinux();
        } else if (OSUtils.isMacOSX()) {
            return new MPlayerWindowOSX();
        } else {
            return null;
        }
    }

    public MediaPlayer getMediaPlayer() {
        return player;
    }

    private void initializeUI() {
        Dimension d = new Dimension(800, 600);
        // initialize auto-hide timer
        hideTimer = new Timer(HIDE_DELAY, arg0 -> MPlayerWindow.this.onHideTimerExpired());
        hideTimer.setRepeats(false);
        // initialize window
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setPlayerWindowTitle();
        setBackground(new Color(0, 0, 0));
        initWindowIcon();
        // initialize events
        addMouseMotionListener(new MPlayerMouseMotionAdapter());
        addComponentListener(new MPlayerComponentHandler());
        addWindowListener(new MPlayerWindowAdapter());
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new MPlayerKeyEventDispatcher());
        // initialize content pane & video canvas
        Container pane = getContentPane();
        pane.setBackground(Color.black);
        pane.setLayout(null);
        pane.setSize(d);
        pane.setPreferredSize(d);
        mplayerComponent = MPlayerComponentFactory.instance().createPlayerComponent();
        if (mplayerComponent == null) {
            throw new RuntimeException("MPlayerComponent instantiation isn't supported in your OS, or your OS hasn't correctly been detected by FrostWire");
        }
        videoCanvas = mplayerComponent.getComponent();
        videoCanvas.setBackground(Color.black);
        videoCanvas.setSize(d);
        videoCanvas.setPreferredSize(d);
        videoCanvas.addMouseMotionListener(new MPlayerMouseMotionAdapter());
        videoCanvas.addMouseListener(new MPlayerMouseAdapter());
        pane.add(videoCanvas);
        // adjust frame size
        pack();
        // initialize overlay controls
        overlayControls = new MPlayerOverlayControls(hideTimer);
        overlayControls.setVisible(false);
        overlayControls.setAlwaysOnTop(true);
        overlayControls.setIsFullscreen(isFullscreen);
        overlayControls.addMouseListener(new MPlayerMouseAdapter());
        overlayControls.addMouseMotionListener(new MPlayerMouseMotionAdapter());
        overlayControls.addWindowListener(new WindowAdapter() {
            @Override
            public void windowDeactivated(WindowEvent e) {
                if (!OSUtils.isWindows()) {
                    if (visibleCounterFlag > 0) {
                        hideOverlay(OSUtils.isMacOSX());
                    }
                    visibleCounterFlag++;
                }
            }
        });
        // initialize animation alpha thread
        animateAlphaThread = new AlphaAnimationThread(overlayControls);
        animateAlphaThread.setDaemon(true);
        animateAlphaThread.start();
    }

    /**
     * Gets the application icon from the main window and puts it on the player window.
     */
    private void initWindowIcon() {
        if (OSUtils.isMacOSX()) {
            //no need.
            return;
        }
        for (Window w : getWindows()) {
            if (w.getParent() == null && w instanceof LimeJFrame) {
                List<Image> iconImages = w.getIconImages();
                if (iconImages.size() > 0) {
                    Image image = iconImages.get(0);
                    setIconImage(image);
                    return;
                }
            }
        }
    }

    private void setPlayerWindowTitle() {
        MediaSource source = MediaPlayer.instance().getCurrentMedia();
        if (source != null) {
            setTitle("FrostWire Media Player -  " + source.getTitleText());
        } else {
            setTitle("FrostWire Media Player");
        }
    }

    /**
     * correctly set visibility and positioning of window and control overlay
     */
    @Override
    public void setVisible(boolean visible) {
        visibleCounterFlag = 0;
        if (isVisible() && visible) {
            visibleCounterFlag = 1;
        }
        if (visible != isVisible()) {
            super.setVisible(visible);
            overlayControls.setVisible(visible);
            if (visible) {
                centerOnScreen();
                positionOverlayControls();
                showOverlay(false);
            } else {
                hideOverlay(OSUtils.isMacOSX());
            }
        }
        if (visible) {
            // make sure window is on top of visible windows & has focus
            toFront();
            requestFocus();
            // disable screen saver
            screenSaverDisabler.start();
        } else {
            // enable screen saver
            screenSaverDisabler.stop();
        }
    }

    public void toggleFullScreen() {
        if (isVisible()) {
            isFullscreen = !isFullscreen;
            overlayControls.setIsFullscreen(isFullscreen);
            positionOverlayControls();
        }
    }

    public long getCanvasComponentHwnd() {
        return 0;
    }

    public long getHwnd() {
        return 0;
    }

    private void resizeCanvas() {
        Dimension videoSize = MediaPlayer.instance().getCurrentVideoSize();
        Dimension contentSize = getContentPane().getSize();
        if (contentSize == null || videoSize == null) {
            return; // can not resize until videoSize is available
        }
        Dimension canvasSize = new Dimension(contentSize);
        float targetAspectRatio = (float) videoSize.width / (float) videoSize.height;
        if (canvasSize.width / targetAspectRatio < contentSize.height) {
            canvasSize.height = (int) (canvasSize.width / targetAspectRatio);
        } else {
            canvasSize.width = (int) (canvasSize.height * targetAspectRatio);
        }
        Point tl = new Point();
        tl.x = (int) ((float) (contentSize.width - canvasSize.width) / 2.0f);
        tl.y = (int) ((float) (contentSize.height - canvasSize.height) / 2.0f);
        videoCanvas.setBounds(tl.x, tl.y, canvasSize.width, canvasSize.height);
    }

    /**
     * centers the window in the current screen
     */
    private void centerOnScreen() {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension window = getSize();
        Point pos = new Point((screen.width - window.width) / 2, (screen.height - window.height) / 2);
        setLocation(pos);
    }

    /**
     * positions the overlay control centered horizontally and 80% down vertically
     */
    private void positionOverlayControls() {
        if (isVisible()) {
            Dimension controlsSize = overlayControls.getSize();
            Dimension windowSize = getSize();
            Point windowPos = getLocationOnScreen();
            Point controlPos = new Point();
            controlPos.x = (int) ((windowSize.width - controlsSize.width) * 0.5 + windowPos.x);
            controlPos.y = (windowSize.height - controlsSize.height) - 20 + windowPos.y;
            overlayControls.setLocation(controlPos);
        }
    }

    private void onHideTimerExpired() {
        animateAlphaThread.animateToTransparent();
    }

    @Override
    public void dispose() {
        animateAlphaThread.setDisposed();
        super.dispose();
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.0f));
        super.paint(g2);
        g2.dispose();
    }

    private void showOverlay(boolean animate) {
        if (animate) {
            animateAlphaThread.animateToOpaque();
        } else {
            overlayControls.setVisible(true);
        }
        hideTimer.restart();
    }

    private void hideOverlay(boolean animate) {
        if (animate) {
            animateAlphaThread.animateToTransparent();
        } else {
            overlayControls.setVisible(false);
        }
        hideTimer.stop();
    }

    void showOverlayControls() {
        showOverlay(true);
    }

    private class MPlayerComponentHandler extends ComponentAdapter {
        @Override
        public void componentResized(ComponentEvent e) {
            resizeCanvas();
            positionOverlayControls();
        }

        @Override
        public void componentMoved(ComponentEvent e) {
            positionOverlayControls();
        }
    }

    private class MPlayerKeyEventDispatcher implements KeyEventDispatcher {
        @Override
        public boolean dispatchKeyEvent(KeyEvent e) {
            if (!isVisible()) {
                return false;
            }
            // limit keyboard processing for only when the MPlayerWindow is the focused window
            Window focusWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
            if (focusWindow != MPlayerWindow.this &&
                    focusWindow != overlayControls) {
                return false;
            }
            if (e.getID() == KeyEvent.KEY_PRESSED) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_P:
                    case KeyEvent.VK_SPACE:
                        MPlayerUIEventHandler.instance().onTogglePlayPausePressed();
                        return true;
                    case KeyEvent.VK_W:
                        if (OSUtils.isMacOSX() && e.isMetaDown()) {
                            player.stop();
                            MPlayerWindow.this.setVisible(false);
                            return true;
                        }
                    case KeyEvent.VK_F:
                        toggleFullScreen();
                        return true;
                    case KeyEvent.VK_RIGHT:
                    case KeyEvent.VK_PERIOD:
                        MPlayerUIEventHandler.instance().onFastForwardPressed();
                        return true;
                    case KeyEvent.VK_LEFT:
                    case KeyEvent.VK_COMMA:
                        MPlayerUIEventHandler.instance().onRewindPressed();
                        return true;
                    case KeyEvent.VK_UP:
                    case KeyEvent.VK_PLUS:
                        MPlayerUIEventHandler.instance().onVolumeIncremented();
                        return true;
                    case KeyEvent.VK_DOWN:
                    case KeyEvent.VK_MINUS:
                        MPlayerUIEventHandler.instance().onVolumeDecremented();
                        return true;
                    case KeyEvent.VK_ESCAPE:
                        if (isFullscreen) {
                            MPlayerUIEventHandler.instance().onToggleFullscreenPressed();
                        }
                }
                // shift + - for volume increment
                if (e.getKeyCode() == KeyEvent.VK_EQUALS) {
                    MPlayerUIEventHandler.instance().onVolumeIncremented();
                    return true;
                }
                // Alt+Enter, Ctrl+Enter full screen shortcuts - seen in other players.
                if ((e.isAltDown() || e.isMetaDown() || e.isControlDown()) && e.getKeyCode() == KeyEvent.VK_ENTER) {
                    MPlayerUIEventHandler.instance().onToggleFullscreenPressed();
                    return true;
                }
            }
            return false;
        }
    }

    private class MPlayerMouseAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 2) {
                MPlayerUIEventHandler.instance().onToggleFullscreenPressed();
            } else {
                showOverlay(false);
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            showOverlay(false);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            showOverlay(false);
        }
    }

    private class MPlayerMouseMotionAdapter extends MouseMotionAdapter {
        @Override
        public void mouseMoved(MouseEvent e) {
            if (MPlayerWindow.this.isActive()) {
                Point2D currMousePosition = e.getPoint();
                if (prevMousePosition == null) {
                    prevMousePosition = currMousePosition;
                }
                double distance = currMousePosition.distance(prevMousePosition);
                if (distance > 10) {
                    showOverlay(true);
                }
                prevMousePosition = currMousePosition;
            }
        }
    }

    private class MPlayerWindowAdapter extends WindowAdapter {
        @Override
        public void windowClosing(WindowEvent e) {
            player.stop();
        }

        @Override
        public void windowDeactivated(WindowEvent e) {
            if (OSUtils.isWindows()) {
                if (e.getOppositeWindow() == overlayControls) {
                    requestFocus();
                } else {
                    hideOverlay(OSUtils.isMacOSX());
                }
            }
        }

        @Override
        public void windowActivated(WindowEvent e) {
            if (e.getOppositeWindow() != overlayControls) {
                showOverlay(false);
            }
        }

        @Override
        public void windowIconified(WindowEvent e) {
            hideOverlay(OSUtils.isMacOSX());
        }

        @Override
        public void windowDeiconified(WindowEvent e) {
            showOverlay(false);
        }
    }
}
