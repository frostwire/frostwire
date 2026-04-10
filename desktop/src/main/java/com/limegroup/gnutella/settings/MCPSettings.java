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

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.IntSetting;
import org.limewire.setting.StringSetting;

public final class MCPSettings extends LimeProps {
    private MCPSettings() {}

    public static final BooleanSetting MCP_SERVER_ENABLED =
        FACTORY.createBooleanSetting("MCP_SERVER_ENABLED", false);

    public static final StringSetting MCP_SERVER_HOST =
        FACTORY.createStringSetting("MCP_SERVER_HOST", "127.0.0.1");

    public static final IntSetting MCP_SERVER_PORT =
        FACTORY.createIntSetting("MCP_SERVER_PORT", 8796);

    public static final BooleanSetting MCP_SERVER_TLS_ENABLED =
        FACTORY.createBooleanSetting("MCP_SERVER_TLS_ENABLED", false);

    public static final BooleanSetting MCP_SERVER_AUTO_START =
        FACTORY.createBooleanSetting("MCP_SERVER_AUTO_START", false);
}
