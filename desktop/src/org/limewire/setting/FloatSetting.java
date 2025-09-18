/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
 * Provides a float setting value. As a subclass of
 * <code>Setting</code>, the setting has a key.
 * <p>
 * Create a <code>FloatSetting</code> object with a
 * {@link SettingsFactory#createFloatSetting(String, float)}.
 */
public class FloatSetting extends AbstractNumberSetting<Float> {
    private float value;

    /**
     * Creates a new <tt>FloatSetting</tt> instance with the specified
     * key and default value.
     *
     * @param defaultProps Default properties
     * @param key          the constant key to use for the setting
     * @param defaultFloat the default value to use for the setting
     */
    FloatSetting(Properties defaultProps, Properties props, String key,
                 float defaultFloat) {
        super(defaultProps, props, key, String.valueOf(defaultFloat),
                false, null, null);
    }

    /**
     * Returns the value of this setting.
     *
     * @return the value of this setting
     */
    public float getValue() {
        return value;
    }

    /**
     * Mutator for this setting.
     *
     * @param value the value to store
     */
    public void setValue(float value) {
        setValueInternal(String.valueOf(value));
    }

    /**
     * Load value from property string value
     *
     * @param sValue property string value
     */
    protected void loadValue(String sValue) {
        try {
            value = Float.parseFloat(sValue.trim());
        } catch (NumberFormatException nfe) {
            revertToDefault();
        }
    }

    protected Comparable<Float> convertToComparable(String value) {
        return Float.valueOf(value);
    }
}
