package com.frostwire.gui.tabs;

import javax.swing.*;
import java.beans.PropertyChangeListener;

/**
 * This interface outlines the required functionality of any of the
 * primary tabs in the main application window.
 */
public interface Tab {
    /**
     * Returns the `JComponent` instance containing all of the
     * UI elements for the tab.
     *
     * @return the `JComponent` intance containing  all of the
     * UI elements for the tab
     */
    JComponent getComponent();

    /**
     * Returns the title of the tab as it's displayed to the user.
     *
     * @return the title of the tab as it's displayed to the user
     */
    String getTitle();

    /**
     * Returns the tooltip text for the tab.
     *
     * @return the tooltip text for the tab
     */
    String getToolTip();

    /**
     * Returns the `Icon` instance for the tab.
     *
     * @return the `Icon` instance for the tab
     */
    Icon getIcon();

    /**
     * Adds a listener to property changes on this tab.
     */
    @SuppressWarnings("unused")
    void addPropertyChangeListener(PropertyChangeListener listener);
}
