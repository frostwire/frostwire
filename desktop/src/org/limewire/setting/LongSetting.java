/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
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

package org.limewire.setting;

import java.util.Properties;

/**
 * Provides a long setting value. As a subclass of
 * <code>Setting</code>, the setting has a key.
 * <p>
 * Create a <code>LongSetting</code> object with a
 * {@link SettingsFactory#createLongSetting(String, long)}.
 */
public class LongSetting extends AbstractNumberSetting<Long> {
    private long value;

    /**
     * Creates a new <code>LongSetting</code> instance with the specified
     * key and default value.
     */
    LongSetting(Properties defaultProps, Properties props, String key,
                long defaultLong) {
        super(defaultProps, props, key, String.valueOf(defaultLong),
                false, null, null);
    }

    /**
     * Returns the value of this setting.
     *
     * @return the value of this setting
     */
    public long getValue() {
        return value;
    }

    /**
     * Mutator for this setting.
     *
     * @param value the value to store
     */
    public void setValue(long value) {
        setValueInternal(String.valueOf(value));
    }

    /**
     * Load value from property string value
     *
     * @param sValue property string value
     */
    protected void loadValue(String sValue) {
        try {
            value = Long.parseLong(sValue.trim());
        } catch (NumberFormatException nfe) {
            revertToDefault();
        }
    }

    protected Comparable<Long> convertToComparable(String value) {
        return Long.valueOf(value);
    }
}
