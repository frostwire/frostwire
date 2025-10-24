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
