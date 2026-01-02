/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.limewire.setting;

import java.util.Properties;

/**
 * Provides an int setting value. As a subclass of <code>Setting</code>,
 * the setting has a key.
 * <p>
 * Create a <code>IntSetting</code> object with a
 * {@link SettingsFactory#createIntSetting(String, int)}.
 */
public final class IntSetting extends AbstractNumberSetting<Integer> {
    private int value;

    /**
     * Creates a new `IntSetting` instance with the specified
     * key and default value.
     */
    IntSetting(Properties defaultProps, Properties props, String key,
               int defaultInt) {
        super(defaultProps, props, key, String.valueOf(defaultInt),
                false, null, null);
    }

    /**
     * Returns the value of this setting.
     *
     * @return the value of this setting
     */
    public int getValue() {
        return value;
    }

    /**
     * Mutator for this setting.
     *
     * @param value the value to store
     */
    public void setValue(int value) {
        setValueInternal(String.valueOf(value));
    }

    /**
     * Load value from property string value
     *
     * @param sValue property string value
     */
    protected void loadValue(String sValue) {
        try {
            value = Integer.parseInt(sValue.trim());
        } catch (NumberFormatException nfe) {
            revertToDefault();
        }
    }

    protected Comparable<Integer> convertToComparable(String value) {
        return Integer.valueOf(value);
    }

    @Override
    public Integer getDefaultValue() {
        Object valueObj = super.getDefaultValue();
        try {
            return Integer.parseInt(valueObj.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }
}
