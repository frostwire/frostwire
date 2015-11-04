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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.MutableComboBoxModel;
import javax.swing.Timer;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;

import org.limewire.util.OSUtils;
import org.limewire.util.SystemUtils;

import com.frostwire.gui.theme.SkinHandler;
import com.limegroup.gnutella.gui.BoxPanel;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.search.Ditherer;

/**
 * An animated notification window that can display multiple notifications. The
 * window automatically scrolls through the messages and hides the winow if the
 * last message has been displayed for <code>timeout</code> milliseconds.
 * 
 * <p>
 * If the mouse enters the notification window the timer is stopped and no more
 * automatic scrolling or hiding will occur. If the mouse leaves the window the
 * timer is restarted. The window is closed instantly when it is clicked.
 * 
 * <p>
 * Buttons are provided in the top right corner to manually scroll through the
 * messages.
 * 
 * <p>
 * Based on JDIC GnomeTrayIconService.BalloonMessageWindow.
 */
public class NotificationWindow extends AnimatedWindow {

    private final static int DEFAULT_TIMEOUT = 6 * 1000;

    private JLabel titleLabel;

    /**
     * Automatically scrolls to next message / hides window after
     * <code>timeout</code>.
     */
    private Timer autoHideTimer;

    private String title;

    private JPanel mainPanel;

    private Icon titleIcon;

    private Point parentLocation;

    private Dimension parentSize;

    private BoxPanel topPanel;

    private ComboBoxModel<Object> model;

    private ModelListener modelListener = new ModelListener();

    private NotificationRenderer renderer = new DefaultNotificationRenderer();

    private Action previousNotificationAction = new PreviousNotificationAction();

    private Action nextNotificationAction = new NextNotificationAction();
    
    private Action closeAction = new CloseAction();

    private JLabel notificationIndexLabel;

    private Dimension locationOffset = new Dimension(0, 0);

    private boolean pendingScreenUpdate;

    public NotificationWindow(Window parent, ComboBoxModel<Object> model) {
        super(parent);

        setModel(model);

        initialize();

        autoHideTimer = new javax.swing.Timer(DEFAULT_TIMEOUT,
                new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        showNextNotificationOrHideWindow();
                    }
                });
        // timer scrolls through all messages before hiding the window and needs
        // to repeat therefore
        autoHideTimer.setRepeats(true);
        
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(final MouseEvent e) {
                //hideWindowImmediately();
            }

            public void mouseEntered(MouseEvent e) {
                stopAutoHideTimer();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                startAutoHideTimer();
            }
        });
        
        addAnimatedWindowListener(new AnimatedWindowListener() {
            public void animationCompleted(AnimatedWindowEvent event) {
                if (event.getAnimationType() == AnimationType.SHOW) {
                    if (pendingScreenUpdate) {
                        pendingScreenUpdate = false;
                        doNotificationLayout();
                    }
                    startAutoHideTimer();
                }
            }

            public void animationStarted(AnimatedWindowEvent event) {
            }

            public void animationStopped(AnimatedWindowEvent event) {
            }            
        });
    }
    
    public NotificationWindow(Window parent) {
        this(parent, new DefaultComboBoxModel<Object>());
    }

    /** Ensure this is always on top. */
    @Override
    public void addNotify() {
        super.addNotify();

        SystemUtils.setWindowTopMost(this);
    }

    public void addNotification(Object notification) {
        ((MutableComboBoxModel<Object>) model).addElement(notification);
    }

    private void doNotificationLayout() {
        if (isHideAnimationInProgress()) {
            // redisplay message
            pendingScreenUpdate = true;
            doShow();
            return;
        } else if (isShowAnimationInProgress()) {
            // update screen when animation is complete
            pendingScreenUpdate = true;
            return;
        }
        
        titleLabel.setIcon(getIcon());
        titleLabel.setText(getTitle());
        
        setInitialHeight(titleLabel.getPreferredSize().height + 10);
        
        int selectedIndex = getSelectedIndex();
        if (selectedIndex != -1) {
            notificationIndexLabel.setText((selectedIndex + 1) + " of "
                    + getModel().getSize());

            previousNotificationAction.setEnabled(selectedIndex > 0);
            nextNotificationAction
                    .setEnabled(selectedIndex < getNotficationCount() - 1);
        } else {
            notificationIndexLabel.setText("");

            previousNotificationAction.setEnabled(false);
            nextNotificationAction.setEnabled(false);
        }

        Component component = getRenderer().getNotificationRendererComponent(
                this, model.getSelectedItem(), selectedIndex);
        mainPanel.add(component, BorderLayout.CENTER);

        pack();
        ensureVisibility();
    }

    @Override
    public Point getFinalLocation() {
        final Point parentLocation = new Point(
                (getParentLocation() != null) ? getParentLocation()
                        : getDefaultParentLocation());
        final Rectangle screenBounds;
        final Insets screenInsets;

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        GraphicsConfiguration gc = getGraphicsConfiguration(parentLocation);
        if (gc != null) {
            screenInsets = toolkit.getScreenInsets(gc);
            screenBounds = gc.getBounds();
        } else {
            screenInsets = new Insets(0, 0, 0, 0);
            screenBounds = new Rectangle(toolkit.getScreenSize());
        }

        final int screenWidth = screenBounds.width - Math.abs(screenInsets.left+screenInsets.right);
        final int screenHeight = screenBounds.height - Math.abs(screenInsets.top+screenInsets.bottom);
        
        // adjust location
        final Dimension parentSize = new Dimension(
                (getParentSize() != null) ? getParentSize()
                        : getDefaultParentSize());
        parentLocation.x -= getLocationOffset().width;
        parentLocation.y -= getLocationOffset().height;
        parentSize.width += getLocationOffset().width * 2;
        parentSize.height += getLocationOffset().height * 2;

        final AnimationMode mode;
        final Dimension preferredSize = getPreferredSize();
        
        // determine animation mode and initialize location
        Point location = new Point();
        if (parentLocation.y - preferredSize.height > 0) {
            // sufficient space to display window above parent location
            mode = AnimationMode.BOTTOM_TO_TOP; 
            location.y = parentLocation.y - preferredSize.height;
        } else {
            mode = AnimationMode.TOP_TO_BOTTOM;
            location.y = parentLocation.y + parentSize.height;
        }
        location.x = parentLocation.x;
        
        // make sure window is displayed within screen bounds 
        if (location.x + preferredSize.width > screenBounds.x + screenWidth) {
            location.x = screenBounds.x + screenWidth -preferredSize.width - 1; 
        }
        if (location.y + preferredSize.height > screenBounds.y + screenHeight) {
            location.y = screenBounds.y + screenHeight - preferredSize.height - 1; 
        }
         
        if (location.x < screenBounds.x) {
            location.x = screenBounds.x;
        }
        if (location.y < screenBounds.y) {
            location.y = screenBounds.y;
        }

        if (OSUtils.isMacOSX()) {
            setMode(AnimationMode.FADE);
        } else {
            setMode(mode);
        }
        
        return location;
    }

    private GraphicsConfiguration getGraphicsConfiguration(Point location) {
        GraphicsEnvironment ge =
            GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screenDevices = ge.getScreenDevices();
        for (GraphicsDevice screenDevice : screenDevices) {
            if(screenDevice.getType() == GraphicsDevice.TYPE_RASTER_SCREEN) {
                GraphicsConfiguration gc = screenDevice.getDefaultConfiguration();
                if (gc.getBounds().contains(location)) {
                    return gc;
                }
            }
        }
        return null;
    }
    
    /**
     * Ensures that the window is displayed within the bounds of screen size.
     */
    private void ensureVisibility() {
        if (!isVisible()) {
            return;
        }

        setLocation(getFinalLocation());
    }

    public int getAutoHideTimeout() {
        return autoHideTimer.getDelay();
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

    public Icon getIcon() {
        return titleIcon;
    }

    public Dimension getLocationOffset() {
        return locationOffset;
    }

    public ComboBoxModel<Object> getModel() {
        return model;
    }

    public int getNotficationCount() {
        return model.getSize();
    }

    public Point getParentLocation() {
        return parentLocation;
    }

    public Dimension getParentSize() {
        return parentSize;
    }

    public NotificationRenderer getRenderer() {
        return renderer;
    }

    public int getSelectedIndex() {
        Object selectedItem = model.getSelectedItem();
        int size = model.getSize();
        for (int i = 0; i < size; i++) {
            Object item = model.getElementAt(i);
            if (item != null && item.equals(selectedItem)) {
                return i;
            }
        }
        return -1;
    }

    public Object getSelectedNotification() {
        return model.getSelectedItem();
    }

    public String getTitle() {
        return title;
    }

    /**
     * Hides the window slowly using an animation.
     */
    public void hideWindow() {
        doHide();
    }

    private void initialize() {
        setAlwaysOnTop(true);
        mainPanel = new MainPanel(new BorderLayout(0, 10)); 
        mainPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory
                .createLineBorder(Color.black, 2), BorderFactory
                .createEmptyBorder(10, 10, 10, 10)));
        mainPanel.setOpaque(true);
        setContentPane(mainPanel);

        // top panel that displays title and scroll buttons

        topPanel = new BoxPanel(BoxLayout.X_AXIS);
        topPanel.setOpaque(false);
        mainPanel.add(topPanel, BorderLayout.NORTH);

        titleLabel = new JLabel();
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        topPanel.add(titleLabel);

        topPanel.add(Box.createHorizontalStrut(10));
        topPanel.add(Box.createHorizontalGlue());

        // TODO use better icons and set rollover icon

        JButton previousNotificationButton = new IconButton(
                previousNotificationAction);
        previousNotificationButton.setIcon(GUIMediator
                .getThemeImage("notification-back_up.gif"));
        previousNotificationButton.setPressedIcon(GUIMediator
                .getThemeImage("notification-back_dn.gif"));
        topPanel.add(previousNotificationButton);
        topPanel.add(Box.createHorizontalStrut(3));

        notificationIndexLabel = new JLabel();
        notificationIndexLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        topPanel.add(notificationIndexLabel);
        topPanel.add(Box.createHorizontalStrut(3));

        JButton nextNotificationButton = new IconButton(nextNotificationAction);
        nextNotificationButton.setIcon(GUIMediator
                .getThemeImage("notification-forward_up.gif"));
        nextNotificationButton.setPressedIcon(GUIMediator
                .getThemeImage("notification-forward_dn.gif"));
        topPanel.add(nextNotificationButton);

        topPanel.add(Box.createHorizontalStrut(5));
        
        JButton closeButton = new IconButton(closeAction);
        closeButton.setIcon(GUIMediator.getThemeImage("kill.png"));
        closeButton.setRolloverIcon(GUIMediator.getThemeImage("kill_on.png"));
        topPanel.add(closeButton);
    }

    public void removeAllNotifications() {
        if (model instanceof DefaultComboBoxModel) {
            ((DefaultComboBoxModel<Object>) model).removeAllElements();
        } else {
            MutableComboBoxModel<Object> mutableModel = (MutableComboBoxModel<Object>) model;
            int size = model.getSize();
            for (int i = 0; i < size; i++) {
                Object item = model.getElementAt(0);
                mutableModel.removeElement(item);
            }
        }
    }

    /**
     * Removes a notification from the data model.
     * 
     * @param notification
     */
    public void removeNotification(Object notification) {
        ((MutableComboBoxModel<Object>) model).removeElement(notification);
    }

    /**
     * Sets the timeout for scrolling/hiding the notification window.
     * 
     * @param timeout timeout in milliseconds
     */
    public void setAutoHideTimeout(int timeout) {
        autoHideTimer.setDelay(timeout);
    }

    public void setLocationOffset(Dimension locationOffset) {
        this.locationOffset = locationOffset;
    }

    /**
     * Sets the icon displayed in the title.
     */
    public void setIcon(Icon icon) {
        this.titleIcon = icon;
        doNotificationLayout();
    }

    /**
     * Sets the underlying data model.
     */
    public void setModel(ComboBoxModel<Object> model) {
        ComboBoxModel<Object> oldModel = this.model;
        if (oldModel != null) {
            oldModel.removeListDataListener(modelListener);
        }

        this.model = model;
        model.addListDataListener(modelListener);

        firePropertyChange("model", oldModel, model);
    }

    /**
     * Sets the location of the parent component. If the parent component is
     * located at the upper border of the screen the notification window is
     * displayed underneath it. If it is located on the lower border or if there
     * is enough space to display the notification window it is displayed above
     * it.
     * 
     * @param parentLocation screen location of the parent component
     * @see #setParentSize(Dimension)
     */
    public void setParentLocation(Point parentLocation) {
        this.parentLocation = parentLocation;
    }

    /**
     * If the notification window is displayed underneath the parent component
     * the height of <code>parentSize</code> is added as an offset to
     * <code>parentLocation</code> to calculate the location of notification
     * window.
     */
    public void setParentSize(Dimension parentSize) {
        this.parentSize = parentSize;
    }

    public void setRenderer(NotificationRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Scrolls to <code>notification</code>.
     * 
     * @param notification
     */
    public void setSelectedNotification(Object notification) {
        model.setSelectedItem(notification);
    }

    public void setTitle(String title) {
        this.title = title;
        doNotificationLayout();
    }

    /**
     * Shows the window slowly using an animation.
     */
    public void showWindow() {
        if (isVisible()) {
            return;
        }

        doShow();
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) {
            removeAllNotifications();
            stopAutoHideTimer();
        }

        super.setVisible(visible);
    }
    
    public void showNextNotificationOrHideWindow() {
        if (!showNextNotification()) {
            doHide();
        }
    }

    /**
     * Scrolls to the next notification.
     * 
     * @return false, if the current notification is the last notification or if
     *         no notifications are displayed
     */
    public boolean showNextNotification() {
        int i = getSelectedIndex();
        if (i != -1 && i < getNotficationCount() - 1) {
            model.setSelectedItem(model.getElementAt(i + 1));
            return true;
        }
        return false;
    }

    /**
     * Scrolls to the previous notification.
     * 
     * @return false, if the current notification is the first notification or
     *         if no notifications are displayed
     */
    public boolean showPreviousNotification() {
        int i = getSelectedIndex();
        if (i != -1 && i > 0) {
            model.setSelectedItem(model.getElementAt(i - 1));
            return true;
        }
        return false;
    }

    /**
     * Starts the timer that scrolls notifications and hides the window.
     * 
     * @see #stopAutoHideTimer()
     */
    public void startAutoHideTimer() {
        autoHideTimer.start();
    }

    /**
     * Stops the timer that scrolls notifications and hides the window.
     * 
     * @see #startAutoHideTimer()
     */
    public void stopAutoHideTimer() {
        autoHideTimer.stop();
    }

    /**
     * A border-less button that is represented by an icon only.
     */
    private class IconButton extends JButton {

        private static final long serialVersionUID = -8670595793783875510L;

        public IconButton(Action action) {
            super(action);
            setText("");
        }

        @Override
        public void updateUI() {
            super.updateUI();

            setContentAreaFilled(false);
            // setBorderPainted(ThemeSettings.isNativeOSXTheme());
            setBorder(BorderFactory.createEmptyBorder());
            setFocusable(false);
            if (getIcon() != null) {
                setPreferredSize(new Dimension(getIcon().getIconWidth(),
                        getIcon().getIconHeight()));
            } else {
                setPreferredSize(null);
            }
            setMargin(new Insets(0, 0, 0, 0));
        }
    }

    /**
     * Updates the notification window when the underlying data model changes.
     */
    private class ModelListener implements ListDataListener {

        public void contentsChanged(ListDataEvent e) {
            doNotificationLayout();
        }

        public void intervalAdded(ListDataEvent e) {
            doNotificationLayout();
        }

        public void intervalRemoved(ListDataEvent e) {
            doNotificationLayout();
        }

    }

    private class NextNotificationAction extends AbstractAction {

        private static final long serialVersionUID = 9025502192496019505L;

        public NextNotificationAction() {
            putValue(Action.NAME, ">");
        }

        public void actionPerformed(ActionEvent e) {
            showNextNotification();
        }
    }

    private class PreviousNotificationAction extends AbstractAction {

        private static final long serialVersionUID = 8634646312072477295L;

        public PreviousNotificationAction() {
            putValue(Action.NAME, "<");
        }

        public void actionPerformed(ActionEvent e) {
            showPreviousNotification();
        }
    }

    private class CloseAction extends AbstractAction {

        private static final long serialVersionUID = -7970545406830868291L;

        public CloseAction() {
            putValue(Action.NAME, "X");
        }

        public void actionPerformed(ActionEvent e) {
            hideWindowImmediately();
        }
    }

    public class MainPanel extends JPanel {
        
        private static final long serialVersionUID = -5156001841794524934L;
        
        // used to paint background
        private Ditherer DITHERER = new Ditherer(62,
                SkinHandler.getSearchPanelBG1(),
                SkinHandler.getSearchPanelBG2());

        public MainPanel(LayoutManager layoutManager) {
            super(layoutManager);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            if (!DITHERER.getFromColor().equals(DITHERER.getToColor())) {
                Dimension size = getSize();
                DITHERER.draw(g, size.height, size.width);
            } else {
                super.paintComponent(g);
            }
        }

        @Override
        public void updateUI() {
            super.updateUI();
            DITHERER = new Ditherer(62,
                    SkinHandler.getSearchPanelBG1(),
                    SkinHandler.getSearchPanelBG2());
            setBackground(SkinHandler.getSearchPanelBG2());
        }
    }
}
