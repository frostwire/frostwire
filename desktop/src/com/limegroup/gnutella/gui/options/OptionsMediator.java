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

package com.limegroup.gnutella.gui.options;

import com.frostwire.bittorrent.BTEngine;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.ApplicationSettings;
import com.limegroup.gnutella.settings.LibrarySettings;
import org.limewire.setting.SettingsGroupManager;

import javax.swing.*;
import java.io.IOException;

/**
 * This class acts as a mediator for the different components of the options
 * window.  This class maintains references to the
 * <tt>OptionsTreeManager</tt> and <tt>OptionsPaneManager</tt>, the two
 * primary classes that it delegates to.
 */
public final class OptionsMediator {
    /**
     * Constant for the key for the root node in the tree.
     */
    final static String ROOT_NODE_KEY = "OPTIONS_ROOT_NODE";
    /**
     * Singleton constant for easy access to the options mediator.
     */
    private final static OptionsMediator INSTANCE = new OptionsMediator();
    /**
     * Constant for the class that manages the current options pane
     * displayed to the user.  (It is fine to construct this here since
     * they do not reference this class.)
     */
    private static OptionsPaneManager _paneManager = null;
    /**
     * Constant for the class that manages the current options tree
     * displayed to the user.  (It is fine to construct this here since
     * they do not reference this class.)
     */
    private static OptionsTreeManager _treeManager = null;
    /**
     * Class that handles constructing all of the elements of the options
     * windows.
     */
    private static OptionsConstructor _constructor = null;

    /**
     * Private constructor to ensure that this class cannot be constructed
     * from another class.  The constructor does very little to alleviate
     * construction conflicts with classes that may use the mediator.
     */
    private OptionsMediator() {
        GUIMediator.setSplashScreenString(I18n.tr("Loading Options Window..."));
    }

    /**
     * Singleton accessor for this class.
     *
     * @return the <tt>OptionsMediator</tt> instance
     */
    public static synchronized OptionsMediator instance() {
        return INSTANCE;
    }

    /**
     * Makes the options window either visible or not visible depending
     * on the boolean argument.
     *
     * @param visible <tt>boolean</tt> value specifying whether the
     *                options window should be made visible or not visible
     * @param key     the unique identifying key of the panel to show
     */
    public final void setOptionsVisible(boolean visible, final String key) {
        if (_constructor == null) {
            if (!visible)
                return;
            updateTheme();
        }
        _paneManager.initOptions();
        _constructor.setOptionsVisible(visible, key);
    }

    /**
     * Basically the inverse operation to {@link #updateTheme()}.
     * <p>
     * Is called from OptionsConstructor when the dialog is disposed.
     */
    final void disposeOptions() {
        _constructor = null;
        _paneManager = null;
        _treeManager = null;
    }

    /**
     * Returns true if the Options Box is visible.
     *
     * @return true if the Options Box is visible.
     */
    public final boolean isOptionsVisible() {
        if (_constructor == null)
            return false;
        return _constructor.isOptionsVisible();
    }

    /**
     * Makes the options window either visible or not visible depending
     * on the boolean argument.
     *
     * @param visible <tt>boolean</tt> value specifying whether the
     *                options window should be made visible or not visible
     */
    public final void setOptionsVisible(boolean visible) {
        setOptionsVisible(visible, null);
    }

    /**
     * Handles the selection of a new panel as the currently visible panel.
     *
     * @param node the node of the panel to show
     */
    public final void handleSelection(final OptionsTreeNode node) {
        _paneManager.show(node);
        if (_constructor.isOptionsVisible()) {
            ApplicationSettings.OPTIONS_LAST_SELECTED_KEY.setValue(node.getTitleKey());
        }
    }

    /**
     * Applies the current settings in the options windows, storing them
     * to disk.  This method delegates to the <tt>OptionsPaneManager</tt>.
     *
     * @throws IOException if the options could not be fully applied
     */
    public final void applyOptions() throws IOException {
        _paneManager.applyOptions();
        SettingsGroupManager.instance().save();
    }

    /**
     * Determines if any of the settings are dirty.
     */
    public final boolean isDirty() {
        if (_paneManager == null)
            return false;
        return _paneManager.isDirty();
    }

    /**
     * Reverts options to their defaults.
     */
    final void revertOptions() {
        SettingsGroupManager.instance().revertToDefault();
        BTEngine.getInstance().revertToDefaultConfiguration();
        LibrarySettings.setupInitialLibraryFolders();
        GUIMediator.showMessage(I18n.tr("One or more options will take effect the next time FrostWire is restarted."));
    }

    /**
     * Returns the main <tt>JDialog</tt> instance for the options window,
     * allowing other components to position themselves accordingly.
     *
     * @return the main options <tt>JDialog</tt> window
     */
    public JDialog getMainOptionsComponent() {
        if (_constructor == null)
            updateTheme();
        return _constructor.getMainOptionsComponent();
    }

    // Implements ThemeObserver interface
    private void updateTheme() {
        _paneManager = new OptionsPaneManager();
        _treeManager = new OptionsTreeManager();
        _constructor = new OptionsConstructor(_treeManager, _paneManager);
    }

    public void reinitPane(String paneKey) {
        _paneManager.reinitPane(paneKey);
    }
}
