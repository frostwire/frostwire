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
import org.limewire.setting.StringArraySetting;

/**
 * Settings for filters
 */
public class FilterSettings extends LimeProps {
    /**
     * Sets whether or not search results including "adult content" are
     * banned.
     */
    public static final BooleanSetting FILTER_ADULT =
            FACTORY.createBooleanSetting("FILTER_ADULT", false);
    /**
     * An array of words that the user has banned from appearing in
     * search results.
     */
    public static final StringArraySetting BANNED_WORDS =
            FACTORY.createStringArraySetting("BANNED_WORDS", new String[0]);

    private FilterSettings() {
    }
}
