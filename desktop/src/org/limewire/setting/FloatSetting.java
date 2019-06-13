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
