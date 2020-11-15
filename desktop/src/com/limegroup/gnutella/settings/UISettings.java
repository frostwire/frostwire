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

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.IntSetting;
import org.limewire.setting.LongSetting;
import org.limewire.setting.StringSetting;

import java.awt.*;

/**
 * Settings to deal with UI.
 */
public final class UISettings extends LimeProps {
    /**
     * Setting for autocompletion
     */
    public static final BooleanSetting AUTOCOMPLETE_ENABLED =
            FACTORY.createBooleanSetting("AUTOCOMPLETE_ENABLED", true);
    /**
     * Setting for using small icons.
     */
    public static final BooleanSetting SMALL_ICONS =
            FACTORY.createBooleanSetting("UI_SMALL_ICONS", isResolutionLow());
    /**
     * Setting for displaying text under icons.
     */
    public static final BooleanSetting TEXT_WITH_ICONS =
            FACTORY.createBooleanSetting("UI_TEXT_WITH_ICONS", true);
    public static final IntSetting UI_LIBRARY_MAIN_DIVIDER_LOCATION =
            FACTORY.createIntSetting("UI_LIBRARY_MAIN_DIVIDER_LOCATION", -1);
    public static final IntSetting UI_LIBRARY_EXPLORER_DIVIDER_POSITION =
            FACTORY.createIntSetting("UI_LIBRARY_EXPLORER_DIVIDER_POSITION", 168);
    /**
     * Setting for the divider location between incoming query monitors and
     * upload panel.
     */
    public static final IntSetting UI_TRANSFERS_DIVIDER_LOCATION =
            FACTORY.createIntSetting("UI_TRANSFERS_DIVIDER_LOCATION", 400);
    /**
     * Setting for if native icons should be pre-loaded.
     */
    public static final BooleanSetting PRELOAD_NATIVE_ICONS =
            FACTORY.createBooleanSetting("PRELOAD_NATIVE_ICONS", true);
    /**
     * Setting to persist the width of the options dialog if the dialog
     * was resized by the user.
     */
    public static final IntSetting UI_OPTIONS_DIALOG_WIDTH =
            FACTORY.createIntSetting("UI_OPTIONS_DIALOG_WIDTH", 844);
    /**
     * Setting to persist the height of the options dialog if the dialog
     * was resized by the user.
     */
    public static final IntSetting UI_OPTIONS_DIALOG_HEIGHT =
            FACTORY.createIntSetting("UI_OPTIONS_DIALOG_HEIGHT", 670);
    /**
     * Setting that globally enables or disables notifications.
     */
    public static final BooleanSetting SHOW_NOTIFICATIONS =
            FACTORY.createBooleanSetting("SHOW_NOTIFICATIONS", true);
    /**
     * Use Classic Search/Transfer tab, or new Search, Transfer tabs
     */
    public static final BooleanSetting UI_SEARCH_TRANSFERS_SPLIT_VIEW =
            FACTORY.createBooleanSetting("UI_SEARCH_TRANSFERS_SPLIT_VIEW", false);
    // See com.limegroup.gnutella.gui.GUIConstants.Feature enum for available experimental features and their states.
    public static final BooleanSetting ALPHA_FEATURES_ENABLED = FACTORY.createBooleanSetting("ALPHA_FEATURES_ENABLED", false);
    public static final BooleanSetting BETA_FEATURES_ENABLED = FACTORY.createBooleanSetting("BETA_FEATURES_ENABLED", true);
    public static final LongSetting LAST_FEEDBACK_SENT_TIMESTAMP = FACTORY.createLongSetting("LAST_FEEDBACK_SENT_TIMESTAMP", 0);
    /**
     * GENERAL -> "G" by default
     */
    public static final StringSetting LAST_SELECTED_TRANSFER_DETAIL_JPANEL = FACTORY.createStringSetting("LAST_SELECTED_TRANSFER_DETAIL_JPANEL", "G");

    private UISettings() {
    }

    /**
     * For people with bad eyes.
     */
    private static boolean isResolutionLow() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        return screenSize.width <= 800 || screenSize.height <= 600;
    }
}
