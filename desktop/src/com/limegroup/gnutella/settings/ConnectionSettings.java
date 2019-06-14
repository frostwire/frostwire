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

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.IntSetting;
import org.limewire.setting.StringSetting;

/**
 * Settings for connections.
 */
public final class ConnectionSettings extends LimeProps {
    /**
     * Constants for proxy settings
     */
    public static final int C_NO_PROXY = 0;
    public static final int C_SOCKS4_PROXY = 4;
    public static final int C_SOCKS5_PROXY = 5;
    public static final int C_HTTP_PROXY = 1;
    public static final IntSetting PORT_RANGE_0 =
            FACTORY.createIntSetting("PORT_RANGE_0", 40256);
    public static final IntSetting PORT_RANGE_1 =
            FACTORY.createIntSetting("PORT_RANGE_1", 50256);
    public static final BooleanSetting MANUAL_PORT_RANGE =
            FACTORY.createBooleanSetting("MANUAL_PORT_RANGE", false);
    /**
     * Whether or not to bind to a specific address for outgoing connections.
     */
    public static final BooleanSetting CUSTOM_NETWORK_INTERFACE =
            FACTORY.createBooleanSetting("CUSTOM_NETWORK_INTERFACE", false);
    /**
     * The inetaddress to use if we're using a custom interface for binding.
     */
    public static final StringSetting CUSTOM_INETADRESS =
            FACTORY.createStringSetting("CUSTOM_INETADRESS_TO_BIND", "0.0.0.0");
    /**
     * Setting for the address of the proxy
     */
    public static final StringSetting PROXY_HOST =
            FACTORY.createStringSetting("PROXY_HOST", "");
    /**
     * Setting for the port of the proxy
     */
    public static final IntSetting PROXY_PORT =
            FACTORY.createIntSetting("PROXY_PORT", 0);
    /**
     * Setting for which proxy type to use or if any at all
     */
    public static final IntSetting CONNECTION_METHOD =
            FACTORY.createIntSetting("CONNECTION_TYPE", C_NO_PROXY);
    /**
     * Setting for whether or not to authenticate at the remote proxy
     */
    public static final BooleanSetting PROXY_AUTHENTICATE =
            FACTORY.createBooleanSetting("PROXY_AUTHENTICATE", false);
    /**
     * Setting for the username to use for the proxy
     */
    public static final StringSetting PROXY_USERNAME =
            FACTORY.createStringSetting("PROXY_USERNAME", "");
    /**
     * Setting for the password to use for the proxy
     */
    public static final StringSetting PROXY_PASS =
            FACTORY.createStringSetting("PROXY_PASS", "");
    /**
     * setting that governs if BitTorrent engine can start if there is no VPN (if true it can only start with VPN active)
     */
    public static final BooleanSetting VPN_DROP_PROTECTION = FACTORY.createBooleanSetting("VPN_DROP_PROTECTION", false);

    private ConnectionSettings() {
    }
}

