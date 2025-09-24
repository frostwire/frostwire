/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.util;

import java.io.File;
import java.util.Locale;

/**
 * Provides methods to get operating system properties, resources and versions,
 * and determine operating system criteria.
 */
public class OSUtils {
    private static boolean _isWindows;
    /**
     * Variable for whether the operating system allows the
     * application to be reduced to the system tray.
     */
    private static boolean _supportsTray;
    /**
     * Variable for whether we're on Mac OS X.
     */
    private static boolean _isMacOSX;
    /**
     * Variable for whether we're on Linux.
     */
    private static boolean _isLinux;

    /*
      OSX versions
      10.9 - Mavericks
      10.10 - Yosemite
      10.11 - El Capitan
      -- No longer called OSX, now called macOS
      10.12 - Sierra
     */
    /**
     * Variable for whether we're on Ubuntu
     */
    private static boolean _isUbuntu;
    /**
     * Variable for whether we're on Fedora
     */
    private static boolean _isFedora;
    /**
     * Variable for whether we're on Solaris.
     */
    private static boolean _isSolaris;
    /**
     * Variable for whether we're on OS/2.
     */
    private static boolean _isOS2;

    static {
        setOperatingSystems();
    }

    /**
     * Sets the operating system variables.
     */
    private static void setOperatingSystems() {
        _isWindows = false;
        _isLinux = false;
        _isUbuntu = false;
        _isFedora = false;
        _isMacOSX = false;
        _isSolaris = false;
        _isOS2 = false;
        String os = System.getProperty("os.name");
        System.out.println("os.name=\"" + os + "\"");
        os = os.toLowerCase(Locale.US);
        // set the operating system variables
        _isWindows = os.contains("windows");
        _isSolaris = os.contains("solaris");
        _isLinux = os.contains("linux");
        _isOS2 = os.contains("os/2");
        if (_isLinux) {
            String unameStr = UnameReader.read();
            _isUbuntu = unameStr.contains("buntu") || unameStr.contains("ebian");
            _isFedora = unameStr.contains("edora") || unameStr.contains("ed Hat");
        }
        if (_isWindows || _isLinux)
            _supportsTray = true;
        if (os.startsWith("mac os")) {
            if (os.endsWith("x")) {
                _isMacOSX = true;
            }
        }
    }

    /**
     * Returns the operating system.
     */
    public static String getOS() {
        return System.getProperty("os.name");
    }

    /**
     * Returns the operating system version.
     */
    private static String getOSVersion() {
        return System.getProperty("os.version");
    }

    public static String getFullOS() {
        return getOS() + "-" + getOSVersion() + "-" + getArchitecture();
    }

    /**
     * Returns true if this is Windows NT or Windows 2000 and
     * hence can support a system tray feature.
     */
    public static boolean supportsTray() {
        return _supportsTray;
    }

    /**
     * Returns whether the OS is some version of Windows.
     *
     * @return `true` if the application is running on some Windows
     * version, `false` otherwise
     */
    public static boolean isWindows() {
        return _isWindows;
    }

    /**
     * Returns whether the OS is OS/2.
     *
     * @return `true` if the application is running on OS/2,
     * `false` otherwise
     */
    public static boolean isOS2() {
        return _isOS2;
    }

    /**
     * Returns whether the OS is Mac OS X.
     *
     * @return `true` if the application is running on Mac OS X,
     * `false` otherwise
     */
    public static boolean isMacOSX() {
        return _isMacOSX;
    }

    /**
     * Returns whether the OS is any macOS.
     *
     * @return `true` if the application is running on Mac OSX
     * or any previous mac version, `false` otherwise
     */
    public static boolean isAnyMac() {
        return _isMacOSX;
    }

    /**
     * Returns whether the OS is Solaris.
     *
     * @return `true` if the application is running on Solaris,
     * `false` otherwise
     */
    public static boolean isSolaris() {
        return _isSolaris;
    }

    /**
     * Returns whether the OS is Linux.
     *
     * @return `true` if the application is running on Linux,
     * `false` otherwise
     */
    public static boolean isLinux() {
        return _isLinux;
    }

    /**
     * Returns whether the Linux distribution is Ubuntu or Debian.
     *
     * @return `true` if the application is running on Ubuntu or Debian distributions,
     * `false` otherwise
     */
    public static boolean isUbuntu() {
        return _isUbuntu;
    }

    /**
     * Returns whether the Linux distribution is Fedora or Red Hat
     *
     * @return `true` if the application is running on Fedora or Red Hat distributions,
     * `false` otherwise
     */
    public static boolean isFedora() {
        return _isFedora;
    }

    /**
     * Returns whether the OS is some version of
     * Unix, defined here as only Solaris or Linux.
     */
    public static boolean isUnix() {
        return _isLinux || _isSolaris;
    }

    /**
     * Return whether the current operating system supports moving files
     * to the trash.
     */
    public static boolean supportsTrash() {
        return isWindows() || isMacOSX();
    }

    /** In the case for arm64, Java returns instead os.arch=aarch64, see getArchitectureInPOSIX() */
    public static String getArchitecture() {
        return System.getProperty("os.arch");
    }

    /**
     * arm64, x86_86
     */
    public static String getMacOSArchitecture() {
        String os_arch = System.getProperty("os.arch");
        if ("aarch64".equals(os_arch)) {
            return "arm64";
        }
        if ("i386".equals(os_arch) || "x86".equals(os_arch)) {
            return "x86_64";
        }
        return os_arch;
    }

    public static boolean isMachineX64() {
        String value = System.getProperty("sun.arch.data.model");
        return value != null && value.equals("64");
    }

    public static boolean isMacOSCatalina105OrNewer() {
        // iTunes died with Catalina, now it's called "Apple Music", technically "Music.app"
        String osVersion = OSUtils.getOSVersion();
        String[] os_parts = osVersion.split("\\.");
        int major = Integer.parseInt(os_parts[0]);
        int minor = Integer.parseInt(os_parts[1]);
        return OSUtils.isAnyMac() && major >= 10 && minor >= 15;
    }

    private static File APP_X_MANIFEST_XML = null;

    public static boolean isWindowsAppStoreInstall() {
        if (!isWindows()) {
            return false;
        }
        if (APP_X_MANIFEST_XML == null) {
            APP_X_MANIFEST_XML = new File("AppxManifest.xml");
        }
        return APP_X_MANIFEST_XML.exists() && APP_X_MANIFEST_XML.isFile();
    }

}
