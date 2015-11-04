/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.settings;

import org.limewire.setting.BooleanSetting;
import org.limewire.setting.IntSetting;
import org.limewire.setting.StringSetting;

/**
 * Settings for connections.
 */
public final class ConnectionSettings extends LimeProps {
    
    private ConnectionSettings() {}
        
	/**
     * Constants for proxy settings
     */
    public static final int C_NO_PROXY = 0;
    public static final int C_SOCKS4_PROXY = 4;
    public static final int C_SOCKS5_PROXY = 5;
    public static final int C_HTTP_PROXY = 1;

    /**
	 * Sets whether or not the users ip address should be forced to
	 * the value they have entered.
	 */
    public static final BooleanSetting FORCE_IP_ADDRESS =
        FACTORY.createBooleanSetting("FORCE_IP_ADDRESS", false);
    
    public static final IntSetting PORT_RANGE_0 =
        FACTORY.createIntSetting("PORT_RANGE_0", 40256);
    
    public static final IntSetting PORT_RANGE_1 =
        FACTORY.createIntSetting("PORT_RANGE_1", 50256);

    public static final BooleanSetting MANUAL_PORT_RANGE =
            FACTORY.createBooleanSetting("MANUAL_PORT_RANGE", false);
        
    /** Whether or not to bind to a specific address for outgoing connections. */
    public static final BooleanSetting CUSTOM_NETWORK_INTERFACE =
        FACTORY.createBooleanSetting("CUSTOM_NETWORK_INTERFACE", false);
    
    /** The inetaddress to use if we're using a custom interface for binding. */
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
     * setting for locale preferencing
     */
    public static final BooleanSetting USE_LOCALE_PREF =
        FACTORY.createBooleanSetting("USE_LOCALE_PREF", true);
}

