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

package org.limewire.util;

import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;

import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Returns system information, where supported, for Windows. Most methods in
 * <code>SystemUtils</code> rely on native code and fail gracefully if the
 * native code library isn't found. <code>SystemUtils</code> uses
 * SystemUtilities.dll.
 */
public class SystemUtils {
    private static final Logger LOG = Logger.getLogger(SystemUtils.class);
    /**
     * Whether or not the native libraries could be loaded.
     */
    private static final boolean isLoaded;

    static {
        boolean canLoad = false;
        try {
            if ((OSUtils.isWindows() && OSUtils.isGoodWindows())) {
                System.loadLibrary("SystemUtilities");
                canLoad = true;
            }
        } catch (Throwable noGo) {
            System.out.println("ERROR: " + noGo.getMessage());
            canLoad = false;
        }
        isLoaded = canLoad;
        LOG.info("SystemUtilities dynamic library loaded? " + isLoaded);
    }

    private SystemUtils() {
    }

    /**
     * Gets the path to the Windows launcher .exe file that is us running right now.
     *
     * @return A String like "c:\Program Files\LimeWire\LimeWire.exe".
     * null on error.
     */
    public static String getRunningPath() {
        try {
            if (OSUtils.isWindows() && isLoaded) {
                String path = getRunningPathNative();
                return (path.equals("")) ? null : path;
            }
            return null;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Gets the complete path to a special folder in the platform operating system shell.
     * <p>
     * The returned path is specific to the current user, and current to how the user has customized it.
     * Here are the given special folder names and example return paths this method currently supports on Windows:
     *
     * <pre>
     * Documents         C:\Documents and Settings\UserName\My Documents
     * ApplicationData   C:\Documents and Settings\UserName\Application Data
     * Desktop           C:\Documents and Settings\UserName\Desktop
     * StartMenu         C:\Documents and Settings\UserName\Start Menu
     * StartMenuPrograms C:\Documents and Settings\UserName\Start Menu\Programs
     * StartMenuStartup  C:\Documents and Settings\UserName\Start Menu\Programs\Startup
     * </pre>
     *
     * @return The path to that folder, or null on error
     */
    public static String getSpecialPath(SpecialLocations location) {
        if (OSUtils.isWindows() && isLoaded) {
            try {
                String path = getSpecialPathNative(location.getName());
                if (!path.equals("")) {
                    return path;
                }
            } catch (UnsatisfiedLinkError error) {
                // Must catch the error because earlier versions of the dll didn't
                // include this method, and installs that happen to have not
                // updated this dll for whatever reason will receive the error
                // otherwise.
                LOG.error("Unable to use getSpecialPath!", error);
            }
        }
        return null;
    }

    /**
     * Changes the icon of a window.
     * Puts the given icon in the title bar, task bar, and Alt+Tab box.
     * Replaces the Swing icon with a real Windows .ico icon that supports multiple sizes, full color, and partially transparent pixels.
     *
     * @param frame The AWT Component, like a JFrame, that is backed by a native window
     * @param icon  The path to a .exe or .ico file on the disk
     */
    public static void setWindowIcon(Component frame, File icon) {
        if (OSUtils.isWindows() && isLoaded) {
            setWindowIconNative(frame, System.getProperty("sun.boot.library.path"), icon.getPath());
        }
    }

    /**
     * Flushes the icon cache on the OS, forcing any icons to be redrawn
     * with the current-most icon.
     */
    public static void flushIconCache() {
        if ((isLoaded && OSUtils.isWindows())) {
            flushIconCacheNative();
        }
    }

    /**
     * Reads a text value stored in the Windows Registry.
     *
     * @param root The name of the root registry key, like "HKEY_LOCAL_MACHINE"
     * @param path The path to the registry key with backslashes as separators, like "Software\\Microsoft\\Windows"
     * @param name The name of the variable within that key, or blank to access the key's default value
     * @return The text value stored there or blank on error
     */
    public static String registryReadText(String root, String path, String name) throws IOException {
        if (OSUtils.isWindows() && isLoaded) {
            return registryReadTextNative(root, path, name);
        }
        throw new IOException(" not supported ");
    }

    /**
     * Sets a numerical value in the Windows Registry.
     *
     * @param root  The name of the root registry key, like "HKEY_LOCAL_MACHINE"
     * @param path  The path to the registry key with backslashes as separators, like "Software\\Microsoft\\Windows"
     * @param name  The name of the variable within that key, or blank to access the key's default value
     * @param value The number value to set there
     */
    public static void registryWriteNumber(String root, String path, String name, int value) {
        if ((OSUtils.isWindows() && isLoaded)) {
            registryWriteNumberNative(root, path, name, value);
        }
    }

    /**
     * Sets a text value in the Windows Registry.
     *
     * @param root  The name of the root registry key, like "HKEY_LOCAL_MACHINE"
     * @param path  The path to the registry key with backslashes as separators, like "Software\\Microsoft\\Windows"
     * @param name  The name of the variable within that key, or blank to access the key's default value
     * @param value The text value to set there
     */
    public static void registryWriteText(String root, String path, String name, String value) {
        if ((OSUtils.isWindows() && isLoaded)) {
            registryWriteTextNative(root, path, name, value);
        }
    }

    /**
     * Deletes a key in the Windows Registry.
     *
     * @param root The name of the root registry key, like "HKEY_LOCAL_MACHINE"
     * @param path The path to the registry key with backslashes as separators, like "Software\\Microsoft\\Windows"
     */
    public static void registryDelete(String root, String path) {
        if ((OSUtils.isWindows() && isLoaded)) {
            registryDeleteNative(root, path);
        }
    }

    /**
     * Determine if a program is listed on the Windows Firewall exceptions list.
     *
     * @param path The path to the program, like "C:\Program Files\LimeWire\LimeWire.exe"
     * @return True if it has a listing on the Exceptions list, false if not or on error
     */
    public static boolean isProgramListedOnFirewall(String path) {
        return (OSUtils.isWindows() && isLoaded) && firewallIsProgramListedNative(path);
    }

    /**
     * Add a program to the Windows Firewall exceptions list.
     *
     * @param path The path to the program, like "C:\Program Files\LimeWire\LimeWire.exe"
     * @param name The name of the program, like "LimeWire", this is the text that will identify the item on the list
     * @return False if error
     */
    public static boolean addProgramToFirewall(String path, String name) {
        return (OSUtils.isWindows() && isLoaded) && firewallAddNative(path, name);
    }

    /**
     * Remove a program from the Windows Firewall exceptions list.
     *
     * @param path The path to the program, like "C:\Program Files\LimeWire\LimeWire.exe"
     */
    public static void removeProgramFromFirewall(String path) {
        if ((OSUtils.isWindows() && isLoaded)) {
            firewallRemoveNative(path);
        }
    }

    public static String getShortFileName(String fileName) {
        return (OSUtils.isWindows() && isLoaded) ? getShortFileNameNative(fileName) : fileName;
    }

    /**
     * @return the default String that the shell will execute to open
     * a file with the provided extension.
     * Only supported on windows.
     */
    public static String getDefaultExtensionHandler(String extension) {
        if (!OSUtils.isWindows() || !isLoaded) {
            return null;
        }
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }
        try {
            String progId = registryReadText("HKEY_CLASSES_ROOT", extension, "");
            return ("".equals(progId)) ? "" : registryReadText("HKEY_CLASSES_ROOT", progId + "\\shell\\open\\command", "");
        } catch (IOException iox) {
            return null;
        }
    }

    /**
     * @return the default String that the shell will execute to open
     * content with the provided mime type.
     * Only supported on windows.
     */
    public static String getDefaultMimeHandler(String mimeType) {
        if (!OSUtils.isWindows() || !isLoaded) {
            return null;
        }
        String extension;
        try {
            extension = registryReadText("HKEY_CLASSES_ROOT", "MIME\\Database\\Content Type\\" + mimeType, "Extension");
        } catch (IOException iox) {
            return null;
        }
        return ("".equals(extension)) ? "" : getDefaultExtensionHandler(extension);
    }

    private static native String getRunningPathNative();

    /*
     * The following methods are implemented in C++ code in SystemUtilities.dll.
     * The idea is that the Windows, Mac, and Linux libraries have methods with the same names.
     * Call a method, and it will run platform-specific code to complete the task in the appropriate platform-specific way.
     */

    private static native String getSpecialPathNative(String name);

    private static native String getShortFileNameNative(String fileName);

    private static native String setWindowIconNative(Component frame, String bin, String icon);

    private static native boolean flushIconCacheNative();

    private static native String registryReadTextNative(String root, String path, String name) throws IOException;

    private static native boolean registryWriteNumberNative(String root, String path, String name, int value);

    private static native boolean registryWriteTextNative(String root, String path, String name, String value);

    private static native boolean registryDeleteNative(String root, String path);

    private static native boolean firewallIsProgramListedNative(String path);

    private static native boolean firewallAddNative(String path, String name);

    private static native boolean firewallRemoveNative(String path);

    /**
     * A list of places that getSpecialPath uses.
     */
    public enum SpecialLocations {
        DOCUMENTS("Documents"),
        DOWNLOADS("Downloads");
        private final String name;

        SpecialLocations(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }
    }
}
