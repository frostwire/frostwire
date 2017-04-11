/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.platform;

/**
 * @author gubatron
 * @author aldenml
 */
public interface Platform {

    FileSystem fileSystem();

    SystemPaths systemPaths();

    AppSettings appSettings();

    /**
     * Returns true if we are under a platform that
     * it is(or can mimic) and Android OS.
     *
     * @return
     */
    boolean android();

    /**
     * Returns the number of the SDK version if the
     * platform is android, -1 otherwise.
     *
     * @return
     */
    int androidVersion();

    /**
     * Returns the current network type.
     * <p/>
     * This is the type of the network that the packages will
     * be routed through by default.
     *
     * @return
     */
    NetworkType networkType();

    VPNMonitor vpn();

    enum NetworkType {

        /**
         * The absence of APN.
         */
        NONE,

        /**
         * The Default Mobile data connection.  When active, all data traffic
         * will use this connection by default.
         */
        MOBILE,

        /**
         * The Default WIFI data connection.  When active, all data traffic
         * will use this connection by default.
         */
        WIFI,

        /**
         * The Default WiMAX data connection.  When active, all data traffic
         * will use this connection by default.
         */
        WIMAX,

        /**
         * The Default Bluetooth data connection. When active, all data traffic
         * will use this connection by default.
         */
        BLUETOOTH,

        /**
         * The Default Ethernet data connection.  When active, all data traffic
         * will use this connection by default.
         */
        ETHERNET,

        /**
         *
         */
        UNKNOWN
    }
}
