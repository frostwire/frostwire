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
 * Provides a byte setting value. As a subclass of
 * <code>Setting</code>, the setting has a key.
 * <p>
 * Create a <code>ByteSetting</code> object with a
 * {@link SettingsFactory#createByteSetting(String, byte)}.
 */
public final class ByteSetting extends AbstractNumberSetting<Byte> {
    private byte value;

    /**
     * Creates a new <tt>SettingBool</tt> instance with the specified
     * key and default value.
     *
     * @param key         the constant key to use for the setting
     * @param defaultByte the default value to use for the setting
     */
    ByteSetting(Properties defaultProps, Properties props, String key,
                byte defaultByte) {
        super(defaultProps, props, key, String.valueOf(defaultByte),
                false, null, null);
    }

    /**
     * Returns the value of this setting.
     *
     * @return the value of this setting
     */
    @SuppressWarnings("unused")
    public byte getValue() {
        return value;
    }

    /**
     * Mutator for this setting.
     *
     * @param value the value to store
     */
    @SuppressWarnings("unused")
    public void setValue(byte value) {
        setValueInternal(String.valueOf(value));
    }

    /**
     * Load value from property string value
     *
     * @param sValue property string value
     */
    @SuppressWarnings("unused")
    protected void loadValue(String sValue) {
        try {
            value = Byte.parseByte(sValue.trim());
        } catch (NumberFormatException nfe) {
            revertToDefault();
        }
    }

    @SuppressWarnings("unused")
    protected Comparable<Byte> convertToComparable(String value) {
        return Byte.valueOf(value);
    }
}
