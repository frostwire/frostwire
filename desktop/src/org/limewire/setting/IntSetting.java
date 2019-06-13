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
 * Provides an int setting value. As a subclass of <code>Setting</code>,
 * the setting has a key.
 * <p>
 * Create a <code>IntSetting</code> object with a
 * {@link SettingsFactory#createIntSetting(String, int)}.
 */
public final class IntSetting extends AbstractNumberSetting<Integer> {
    private int value;

    /**
     * Creates a new <tt>IntSetting</tt> instance with the specified
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
