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

package com.limegroup.gnutella.gui.options;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.SettingsWarningManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the main options window that displays the various options
 * windows.<p>
 * <p>
 * This class also stores all of the main options panels to access
 * all of them regardless of how many there are or what their
 * specific type is.
 */
final class OptionsPaneManager {
    /**
     * Constant for the main panel of the options window.
     */
    private final JPanel MAIN_PANEL = new JPanel();
    /**
     * Constant for the <tt>CardLayout</tt> used in the main panel.
     */
    private final CardLayout CARD_LAYOUT = new CardLayout();
    /**
     * Constant for the <tt>ArrayList</tt> containing all of the visible
     * <tt>OptionsPane</tt> instances.
     */
    private final List<OptionsPane> OPTIONS_PANE_LIST = new ArrayList<>();
    /**
     * Stores the already created option panes by key.
     */
    private final Map<String, OptionsPane> panesByKey = new HashMap<>();
    /**
     * The factory which option panes are created from.
     */
    private final OptionsPaneFactory FACTORY = new OptionsPaneFactory();

    /**
     * The constructor sets the layout and adds all of the <tt>OptionPane</tt>
     * instances.
     */
    OptionsPaneManager() {
        MAIN_PANEL.setLayout(CARD_LAYOUT);
    }

    /**
     * Shows the options pane specified by its title.
     * <p>
     * Lazily creates the options pane if it was not shown before. Its options
     * are initialized before it is shown.
     *
     * @param node the name of the <code>Component</code> to show
     */
    public final void show(final OptionsTreeNode node) {
        if (!panesByKey.containsKey(node.getTitleKey())) {
            OptionsPane pane = FACTORY.createOptionsPane(node);
            pane.initOptions();
            addPane(pane);
            panesByKey.put(node.getTitleKey(), pane);
            // If this was the 'SAVED' key, then also load shared,
            // since setting save stuff requires that sharing be updated also.
            if (node.getTitleKey().equals(OptionsConstructor.SAVE_BASIC_KEY) && !panesByKey.containsKey(OptionsConstructor.SHARED_BASIC_KEY)) {
                OptionsPane shared = FACTORY.createOptionsPane(node);
                shared.initOptions();
                addPane(shared);
                panesByKey.put(node.getTitleKey(), shared);
            }
        }
        CARD_LAYOUT.show(MAIN_PANEL, node.getTitleKey());
    }

    /**
     * Sets the options for each <tt>OptionPane</tt> instance in the
     * <tt>ArrayList</tt> of <tt>OptionPane</tt>s when the window is shown.
     */
    public void initOptions() {
        for (OptionsPane op : OPTIONS_PANE_LIST) {
            op.initOptions();
        }
    }

    /**
     * Applies the current settings in the options windows, storing them
     * to disk.  This method delegates to the <tt>OptionsPaneManager</tt>.
     *
     * @throws IOException if the options could not be fully applied
     */
    public final void applyOptions() throws IOException {
        boolean restartRequired = false;
        for (OptionsPane op : OPTIONS_PANE_LIST) {
            restartRequired |= op.applyOptions();
        }
        if (restartRequired) {
            GUIMediator.showMessage(I18n.tr("One or more options will take effect the next time FrostWire is restarted."));
        }
        SettingsWarningManager.checkSettingsLoadSaveFailure();
    }

    /**
     * Determines if any of the panes are dirty.
     */
    public final boolean isDirty() {
        for (OptionsPane op : OPTIONS_PANE_LIST) {
            if (op.isDirty())
                return true;
        }
        return false;
    }

    /**
     * Returns the main <code>Component</code> for this class.
     *
     * @return a <code>Component</code> instance that is the main component
     * for this class.
     */
    public final Component getComponent() {
        return MAIN_PANEL;
    }

    /**
     * Adds the speficied window to the CardLayout based on its title.
     *
     * @param pane the <code>OptionsPane</code> to add
     */
    private void addPane(final OptionsPane pane) {
        MAIN_PANEL.add(pane.getContainer(), pane.getName());
        OPTIONS_PANE_LIST.add(pane);
    }

    void reinitPane(String paneKey) {
        OptionsPane optionsPane = panesByKey.get(paneKey);
        if (optionsPane != null) {
            optionsPane.initOptions();
        }
    }
}
