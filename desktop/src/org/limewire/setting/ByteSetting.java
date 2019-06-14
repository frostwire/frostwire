/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
