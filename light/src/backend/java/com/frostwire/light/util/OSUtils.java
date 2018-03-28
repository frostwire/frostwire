/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.light.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

public class OSUtils {

    static {
        setOperatingSystems();
    }

    private static boolean _isWindows;
    private static boolean _isWindowsNT;
    private static boolean _isWindowsXP;
    private static boolean _isWindows95;
    private static boolean _isWindows98;
    private static boolean _isWindowsMe;
    private static boolean _isWindowsVista;
    private static boolean _isWindows7;
    private static boolean _isWindows8;
    private static boolean _isWindows10;

    /**
     * Variable for whether or not the operating system allows the
     * application to be reduced to the system tray.
     */
    private static boolean _supportsTray;

    /**
     * Variable for whether or not we're on Mac OS X.
     */
    private static boolean _isMacOSX;

    /**
     * OSX versions
     * 10.5 - Leopard
     * 10.6 - Snow Leopard
     * 10.7 - Lion
     * 10.8 - Mountain Lion
     * --- no more cats, now it's mountains
     * 10.9 - Mavericks
     * 10.10 - Yosemite
     * 10.11 - El Capitan
     * -- No longer called OSX, now called macOS
     * 10.12 - Sierra
     */
    private static boolean _isMacOSX105;
    private static boolean _isMacOSX106;
    private static boolean _isMacOSX107;

    /**
     * Variable for whether or not we're on Linux.
     */
    private static boolean _isLinux;

    /**
     * Variable for whether or not we're on Ubuntu
     */
    private static boolean _isUbuntu;

    /**
     * Variable for whether or not we're on Fedora
     */
    private static boolean _isFedora;

    /**
     * Variable for whether or not we're on Solaris.
     */
    private static boolean _isSolaris;

    /**
     * Variable for whether or not we're on OS/2.
     */
    private static boolean _isOS2;


    /**
     * Sets the operating system variables.
     */
    public static void setOperatingSystems() {
        _isWindows = false;
        _isWindows8 = false;
        _isWindows7 = false;
        _isWindowsVista = false;
        _isWindowsXP = false;
        _isWindowsMe = false;
        _isWindowsNT = false;
        _isWindows98 = false;
        _isWindows95 = false;
        _isLinux = false;
        _isUbuntu = false;
        _isFedora = false;
        _isMacOSX = false;
        _isMacOSX105 = false;
        _isMacOSX106 = false;
        _isSolaris = false;
        _isOS2 = false;

        String os = System.getProperty("os.name");
        System.out.println("os.name=\"" + os + "\"");
        os = os.toLowerCase(Locale.US);

        // set the operating system variables
        _isWindows	= os.indexOf("windows") != -1;
        _isSolaris	= os.indexOf("solaris") != -1;
        _isLinux	= os.indexOf("linux") != -1;
        _isOS2		= os.indexOf("os/2") != -1;

        if(_isWindows){
            _isWindows10 = os.indexOf("windows 10") != -1;
            _isWindows8	= os.indexOf("windows 8") != -1;
            _isWindows7	= os.indexOf("windows 7") != -1;
            _isWindowsVista	= os.indexOf("windows vista") != -1;
            _isWindowsXP	= os.indexOf("windows xp") != -1;
            _isWindowsNT	= os.indexOf("windows nt") != -1;
            _isWindowsMe	= os.indexOf("windows me") != -1;
            _isWindows98	= os.indexOf("windows 98") != -1;
            _isWindows95	= os.indexOf("windows 95") != -1;
        }

        if (_isLinux) {
            String unameStr = unameRead();
            _isUbuntu = unameStr.contains("buntu") || unameStr.contains("ebian");
            _isFedora = unameStr.contains("edora") || unameStr.contains("ed Hat");
        }

        if(_isWindows || _isLinux)
            _supportsTray = true;

        if(os.startsWith("mac os")) {
            if(os.endsWith("x")) {
                _isMacOSX = true;
                _isMacOSX105 = System.getProperty("os.version").startsWith("10.5");
                _isMacOSX106 = System.getProperty("os.version").startsWith("10.6");
                _isMacOSX107 = System.getProperty("os.version").startsWith("10.7");
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
    public static String getOSVersion() {
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
     * Returns whether or not the OS is some version of Windows.
     *
     * @return <tt>true</tt> if the application is running on some Windows
     *         version, <tt>false</tt> otherwise
     */
    public static boolean isWindows() {
        return _isWindows;
    }

    /**
     * Returns whether or not the OS is WinXP.
     *
     * @return <tt>true</tt> if the application is running on WinXP,
     *  <tt>false</tt> otherwise
     */
    public static boolean isWindowsXP() {
        return _isWindowsXP;
    }

    /**
     * @return true if the application is running on Windows NT
     */
    public static boolean isWindowsNT() {
        return _isWindowsNT;
    }

    /**
     * @return true if the application is running on Windows 95
     */
    public static boolean isWindows95() {
        return _isWindows95;
    }

    /**
     * @return true if the application is running on Windows 98
     */
    public static boolean isWindows98() {
        return _isWindows98;
    }

    /**
     * @return true if the application is running on Windows ME
     */
    public static boolean isWindowsMe() {
        return _isWindowsMe;
    }

    /**
     * @return true if the application is running on Windows Vista
     */
    public static boolean isWindowsVista() {
        return _isWindowsVista;
    }

    /**
     * @return true if the application is running on Windows 7
     */
    public static boolean isWindows7() {
        return _isWindows7;
    }

    public static boolean isWindows8() {
        return _isWindows8;
    }

    public static boolean isWindows10() {
        return _isWindows10;
    }

    /**
     * @return true if the application is running on a windows
     * that supports native theme.
     */
    public static boolean isNativeThemeWindows() {
        return isWindowsVista() || isWindowsXP();
    }

    /**
     * Returns whether or not the OS is OS/2.
     *
     * @return <tt>true</tt> if the application is running on OS/2,
     *         <tt>false</tt> otherwise
     */
    public static boolean isOS2() {
        return _isOS2;
    }

    /**
     * Returns whether or not the OS is Mac OS X.
     *
     * @return <tt>true</tt> if the application is running on Mac OS X,
     *         <tt>false</tt> otherwise
     */
    public static boolean isMacOSX() {
        return _isMacOSX;
    }

    /**
     * Returns whether or not the OS version of Mac OS X is 10.5.x.
     *
     * @return <tt>true</tt> if the application is running on Mac OS X 10.5.x,
     *         <tt>false</tt> otherwise
     */
    public static boolean isMacOSX105() {
        return _isMacOSX105;
    }

    /**
     * Returns whether or not the OS version of Mac OS X is 10.6.x.
     *
     *  @return <tt>true</tt> if the application is running on Mac OS X 10.6.x,
     *         <tt>false</tt> otherwise
     */
    public static boolean isMacOSX106() {
        return _isMacOSX106;
    }

    /**
     * Returns whether or not the OS version of Mac OS X is 10.6.x.
     *
     *  @return <tt>true</tt> if the application is running on Mac OS X 10.6.x,
     *         <tt>false</tt> otherwise
     */
    public static boolean isMacOSX107() {
        return _isMacOSX107;
    }


    /**
     * Returns whether or not the OS is any Mac OS.
     *
     * @return <tt>true</tt> if the application is running on Mac OSX
     *  or any previous mac version, <tt>false</tt> otherwise
     */
    public static boolean isAnyMac() {
        return _isMacOSX;
    }

    /**
     * Returns whether or not the OS is Solaris.
     *
     * @return <tt>true</tt> if the application is running on Solaris,
     *         <tt>false</tt> otherwise
     */
    public static boolean isSolaris() {
        return _isSolaris;
    }

    /**
     * Returns whether or not the OS is Linux.
     *
     * @return <tt>true</tt> if the application is running on Linux,
     *         <tt>false</tt> otherwise
     */
    public static boolean isLinux() {
        return _isLinux;
    }

    /**
     * Returns whether or not the Linux distribution is Ubuntu or Debian.
     *
     * @return <tt>true</tt> if the application is running on Ubuntu or Debian distributions,
     *         <tt>false</tt> otherwise
     */
    public static boolean isUbuntu() {
        return _isUbuntu;
    }

    /**
     * Returns whether or not the Linux distribution is Fedora or Red Hat
     *
     * @return <tt>true</tt> if the application is running on Fedora or Red Hat distributions,
     *         <tt>false</tt> otherwise
     */
    public static boolean isFedora() {
        return _isFedora;
    }

    /**
     * Returns whether or not the OS is some version of
     * Unix, defined here as only Solaris or Linux.
     */
    public static boolean isUnix() {
        return _isLinux || _isSolaris;
    }

    /**
     * Returns whether or not this operating system is considered
     * capable of meeting the requirements of a high load server.
     *
     * @return <tt>true</tt> if this OS meets high load server requirements,
     *         <tt>false</tt> otherwise
     */
    public static boolean isHighLoadOS() {
        return !(_isWindows98 || _isWindows95 || _isWindowsMe || _isWindowsNT);
    }

    /**
     * @return true if this is a well-supported version of windows.
     * (not 95, 98, nt or me)
     */
    public static boolean isGoodWindows() {
        return isWindows() && isHighLoadOS();
    }

    public static boolean isModernWindows() {
        return isWindows()
                && !(_isWindows98 || _isWindows95 || _isWindowsMe || _isWindowsNT || _isWindowsXP);
    }

    /**
     * Return whether the current operating system supports moving files
     * to the trash.
     */
    public static boolean supportsTrash() {
        return isWindows() || isMacOSX();
    }

    /**
     * Returns the maximum path system of file system of the current OS
     * or a conservative approximation.
     */
    public static int getMaxPathLength() {
        if (isWindows()) {
            return Short.MAX_VALUE;
        }
        else if (isLinux()) {
            return 4096 - 1;
        }
        else {
            return 1024 - 1;
        }
    }

    public static String getArchitecture() {
        return System.getProperty("os.arch");
    }

    public static boolean isMachineX64() {
        String value = System.getProperty("sun.arch.data.model");
        return value != null && value.equals("64");
    }

    public static String unameRead()  {

        String output = "";

        try {

            ProcessBuilder pb = new ProcessBuilder("uname", "-a");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            InputStream is = proc.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            String line;
            int exit = -1;

            while ((line = br.readLine()) != null) {

                output = line;

                try {
//			        exit = proc.exitValue();
//			        if (exit == 0)  {
//			            // Process finished
//			        }
                } catch (IllegalThreadStateException t) {
                    proc.destroy();
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return output;
    }
}