package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.actions.LimeAction;
import com.limegroup.gnutella.settings.UISettings;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

/**
 * A JButton that uses an Icon.
 */
public class IconButton extends JButton {
    private String message;
    private String iconName;
    private String rollOverIconName;
    private boolean horizontalText;
    private boolean useTransparentBackground;
    private int resizedWidth = -1;
    private int resizedHeight = -1;
    /**
     * The super constructors of JButton call {@link #updateUI()} before we
     * had a chance to set our values. So ignore these calls in
     * {@link #updateButton()}.
     */
    private final boolean initialized;
    private PropertyChangeListener listener = null;
    private boolean iconOnly;

    /**
     * @param text
     * @param iconName
     * @param horizontalTextPlacement - if true, text will be displayed to the right of the icon.
     */
    IconButton(String text, String iconName, boolean horizontalTextPlacement) {
        this(text, iconName);
        horizontalText = horizontalTextPlacement;
        useTransparentBackground = true;
    }

    /**
     * Constructs a new IconButton with the given text & icon name.
     */
    IconButton(String text, String iconName) {
        setRolloverEnabled(true);
        this.iconName = iconName;
        this.message = text;
        initialized = true;
        useTransparentBackground = true;
        updateButton();
    }

    /**
     * Constructs a new IconButton with the an icon only.
     */
    IconButton(String iconName) {
        setRolloverEnabled(true);
        this.iconName = iconName;
        this.message = "";
        this.iconOnly = true;
        initialized = true;
        useTransparentBackground = true;
        updateButton();
    }

    public IconButton(String iconName, int w, int h) {
        this(iconName);
        resizedWidth = w;
        resizedHeight = h;
        updateButton();
    }

    /**
     * Constructs an IconButton for an action.
     * <p>
     * Actions must provide a value for the key {@link LimeAction#ICON_NAME}
     * and can provide a short name which is shown below the icon with
     * {@link LimeAction#SHORT_NAME}. If the short name is not provided it'll
     * fall back on {@link Action#NAME}.
     *
     * @param action
     */
    public IconButton(Action action) {
        super(action);
        setRolloverEnabled(true);
        initialized = true;
        useTransparentBackground = true;
        updateButton();
    }

    /**
     * Overridden for internal reasons, no API changes.
     */
    public void setAction(Action a) {
        Action oldAction = getAction();
        if (oldAction != null) {
            oldAction.removePropertyChangeListener(getListener());
        }
        super.setAction(a);
        setButtonFromAction(a);
        a.addPropertyChangeListener(getListener());
    }

    protected void setHorizontalText(boolean useHorizontalText) {
        horizontalText = useHorizontalText;
    }

    protected void setUseTransparentBackground(boolean transparentBackground) {
        useTransparentBackground = transparentBackground;
    }

    private void setButtonFromAction(Action action) {
        iconName = (String) action.getValue(LimeAction.ICON_NAME);
        rollOverIconName = (String) action.getValue(LimeAction.ICON_NAME_ROLLOVER);
        message = (String) action.getValue(LimeAction.SHORT_NAME);
        // fall back on Action.NAME
        if (message == null) {
            message = (String) action.getValue(Action.NAME);
        }
        updateButton();
    }

    private PropertyChangeListener getListener() {
        if (listener == null) {
            listener = evt -> setButtonFromAction((Action) evt.getSource());
        }
        return listener;
    }

    /**
     * Updates the UI, possibly changing the icons or text.
     */
    public void updateUI() {
        super.updateUI();
        updateButton();
    }

    /**
     * Updates the text of the icon.
     */
    public void setText(String text) {
        message = text;
        updateButton();
    }

    /**
     * Updates the button.
     */
    private void updateButton() {
        if (!initialized)
            return;
        Icon icon = IconManager.instance().getIconForButton(iconName);
        if (icon == null) {
            super.setText(message);
            setVerticalTextPosition(SwingConstants.CENTER);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setContentAreaFilled(true);
            setBorderPainted(true);
            setOpaque(true);
        } else {
            if (resizedWidth > 0 && resizedHeight > 0) {
                icon = ImageManipulator.resize(icon, resizedWidth, resizedHeight);
            }
            setIcon(icon);
            Icon rollover = IconManager.instance().getIconForButton(rollOverIconName);
            if (rollover == null) {
                rollover = IconManager.instance().getRolloverIconForButton(iconName);
            }
            if (resizedHeight > 0 && resizedWidth > 0) {
                rollover = ImageManipulator.resize(rollover, resizedWidth, resizedHeight);
            }
            setRolloverIcon(rollover);
            if (!horizontalText) {
                setVerticalTextPosition(SwingConstants.BOTTOM);
                setHorizontalTextPosition(SwingConstants.CENTER);
            } else {
                setVerticalTextPosition(SwingConstants.CENTER);
                setHorizontalTextPosition(SwingConstants.TRAILING);
            }
            if (useTransparentBackground) {
                setBorderPainted(false);
                setOpaque(false);
                setContentAreaFilled(false);
            } else {
                setBorderPainted(true);
                setOpaque(false);
                setContentAreaFilled(true);
            }
            if (!iconOnly &&
                    UISettings.TEXT_WITH_ICONS.getValue() &&
                    message != null &&
                    message.length() > 0) {
                super.setText(message);
                setPreferredSize(null);
            } else {
                super.setText(null);
                int height = icon.getIconHeight();
                int width = icon.getIconWidth();
                if (message == null || message.length() > 0) {
                    height += 15;
                    width += 15;
                }
                setPreferredSize(new Dimension(height, width));
            }
        }
    }
}
