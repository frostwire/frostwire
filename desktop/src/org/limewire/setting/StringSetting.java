/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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
 * Provides a <code>String</code> setting value. As a subclass of
 * <code>Setting</code>, the setting has a key.
 * <p>
 * Create a <code>StringSetting</code> object with a
 * {@link SettingsFactory#createStringSetting(String, String)}.
 */
public final class StringSetting extends AbstractSetting {
    private String value;

    /**
     * Creates a new <tt>SettingBool</tt> instance with the specified
     * key and default value.
     *
     * @param key        the constant key to use for the setting
     * @param defaultStr the default value to use for the setting
     */
    StringSetting(Properties defaultProps, Properties props, String key,
                  String defaultStr) {
        super(defaultProps, props, key, defaultStr);
    }

    /**
     * Returns the value of this setting.
     *
     * @return the value of this setting
     */
    public String getValue() {
        return value;
    }

    /**
     * Mutator for this setting.
     *
     * @param str the <tt>String</tt> to store
     */
    public void setValue(String str) {
        setValueInternal(str);
    }

    /**
     * Load value from property string value
     *
     * @param sValue property string value
     */
    protected void loadValue(String sValue) {
        value = sValue;
    }
}
