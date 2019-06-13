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
 * Provides a font name setting value. As a subclass of
 * <code>Setting</code>, the setting has a key.
 * <p>
 * Create a <code>FontNameSetting</code> object with a
 * {@link SettingsFactory#createFontNameSetting(String, String)}.
 */
final class FontNameSetting extends AbstractSetting {
    FontNameSetting(Properties defaultProps, Properties props, String key,
                    String defaultStr) {
        super(defaultProps, props, key, defaultStr);
    }

    @Override
    protected void loadValue(String sValue) {
    }
}
