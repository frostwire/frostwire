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

import com.frostwire.util.Logger;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public class SystemUtils {

    private static final Logger LOG = Logger.getLogger(SystemUtils.class);

    /**
     * Whether or not the native libraries could be loaded.
     */
    private static boolean isLoaded;

    static {
        boolean canLoad = false;
        try {
            if ((OSUtils.isWindows() && OSUtils.isGoodWindows()) || OSUtils.isMacOSX()) {
                System.loadLibrary("SystemUtilities");
                canLoad = true;
            }
        } catch (Throwable noGo) {
            System.out.println("ERROR: " + noGo.getMessage());
            canLoad = false;
        }
        isLoaded = canLoad;
    }

    private SystemUtils() {}

    /**
     * Sets a file to be writeable.  Package-access so FileUtils can delegate
     * the filename given should ideally be a canonicalized filename.
     */
    static void setWriteable(String fileName) {
        if (isLoaded && (OSUtils.isWindows() || OSUtils.isMacOSX())) {
            setFileWriteable(fileName);
        }
    }

    /**
     * Gets the path to the Windows launcher .exe file that is us running right now.
     *
     * @return A String like "c:\Program Files\LimeWire\LimeWire.exe".
     *         null on error.
     */
    public static final String getRunningPath() {
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

    /** A list of places that getSpecialPath uses. */
    public enum SpecialLocations {
        DOCUMENTS("Documents"),
        DOWNLOADS("Downloads"),
        APPLICATION_DATA("ApplicationData"),
        DESKTOP("Desktop"),
        START_MENU("StartMenu"),
        START_MENU_PROGRAMS("StartMenuPrograms"),
        START_MENU_STARTUP("StartMenuStartup");

        private final String name;
        SpecialLocations(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Gets the complete path to a special folder in the platform operating system shell.
     *
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
     * @return     The path to that folder, or null on error
     */
    public static final String getSpecialPath(SpecialLocations location) {
        if (OSUtils.isWindows() && isLoaded) {
            try {
                String path = getSpecialPathNative(location.getName());
                if (!path.equals("")) {
                    return path;
                }

            } catch(UnsatisfiedLinkError error) {
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
     * @return      False on error
     */
    public static final boolean setWindowIcon(Component frame, File icon) {
        if (OSUtils.isWindows() && isLoaded) {
            String result = setWindowIconNative(frame, System.getProperty("sun.boot.library.path"), icon.getPath());
            return result.equals(""); // Returns blank on success, or information about an error
        }

        return false;
    }

    /**
     * Sets a Component to be topmost.
     */
    public static final boolean setWindowTopMost(Component frame) {
        if (isLoaded && OSUtils.isWindows()) {
            String result = setWindowTopMostNative(frame, System.getProperty("sun.boot.library.path"));
            return result.equals("");
        }

        return false;
    }

    public static final boolean toggleFullScreen(long hwnd) {
        return (isLoaded && (OSUtils.isWindows() || OSUtils.isLinux())) ? toggleFullScreenNative(hwnd) : false;
    }

    /**
     * Flushes the icon cache on the OS, forcing any icons to be redrawn
     * with the current-most icon.
     */
    public static final boolean flushIconCache() {
        return (isLoaded && OSUtils.isWindows()) ? flushIconCacheNative() : false;
    }

    /**
     * Reads a text value stored in the Windows Registry.
     *
     * @param root The name of the root registry key, like "HKEY_LOCAL_MACHINE"
     * @param path The path to the registry key with backslashes as separators, like "Software\\Microsoft\\Windows"
     * @param name The name of the variable within that key, or blank to access the key's default value
     * @return     The text value stored there or blank on error
     */
    public static final String registryReadText(String root, String path, String name) throws IOException {
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
     * @return      False on error
     */
    public static final boolean registryWriteNumber(String root, String path, String name, int value) {
        return (OSUtils.isWindows() && isLoaded) ? registryWriteNumberNative(root, path, name, value) : false;
    }

    /**
     * Sets a text value in the Windows Registry.
     *
     * @param root  The name of the root registry key, like "HKEY_LOCAL_MACHINE"
     * @param path  The path to the registry key with backslashes as separators, like "Software\\Microsoft\\Windows"
     * @param name  The name of the variable within that key, or blank to access the key's default value
     * @param value The text value to set there
     * @return      False on error
     */
    public static final boolean registryWriteText(String root, String path, String name, String value) {
        return (OSUtils.isWindows() && isLoaded) ? registryWriteTextNative(root, path, name, value) : false;
    }

    /**
     * Deletes a key in the Windows Registry.
     *
     * @param root The name of the root registry key, like "HKEY_LOCAL_MACHINE"
     * @param path The path to the registry key with backslashes as separators, like "Software\\Microsoft\\Windows"
     * @return     False on error
     */
    public static final boolean registryDelete(String root, String path) {
        return (OSUtils.isWindows() && isLoaded) ? registryDeleteNative(root, path) : false;
    }

    /**
     * Determine if this Windows computer has Windows Firewall on it.
     *
     * @return True if it does, false if it does not or there was an error
     */
    public static final boolean isFirewallPresent() {
        return (OSUtils.isWindows() && isLoaded) ? firewallPresentNative() : false;
    }

    /**
     * Determine if the Windows Firewall is enabled.
     *
     * @return True if the setting on the "General" tab is "On (recommended)".
     *         False if the setting on the "General" tab is "Off (not recommended)".
     *         False on error.
     */
    public static final boolean isFirewallEnabled() {
        return (OSUtils.isWindows() && isLoaded) ? firewallEnabledNative() : false;
    }

    /**
     * Determine if a program is listed on the Windows Firewall exceptions list.
     *
     * @param path The path to the program, like "C:\Program Files\LimeWire\LimeWire.exe"
     * @return     True if it has a listing on the Exceptions list, false if not or on error
     */
    public static final boolean isProgramListedOnFirewall(String path) {
        return (OSUtils.isWindows() && isLoaded) ? firewallIsProgramListedNative(path) : false;
    }

    /**
     * Add a program to the Windows Firewall exceptions list.
     *
     * @param path The path to the program, like "C:\Program Files\LimeWire\LimeWire.exe"
     * @param name The name of the program, like "LimeWire", this is the text that will identify the item on the list
     * @return     False if error
     */
    public static final boolean addProgramToFirewall(String path, String name) {
        return (OSUtils.isWindows() && isLoaded) ? firewallAddNative(path, name) : false;
    }

    /**
     * Remove a program from the Windows Firewall exceptions list.
     *
     * @param path The path to the program, like "C:\Program Files\LimeWire\LimeWire.exe"
     * @return     False if error.
     */
    public static final boolean removeProgramFromFirewall(String path) {
        return (OSUtils.isWindows() && isLoaded) ? firewallRemoveNative(path) : false;
    }

    /**
     * Opens a Web address using the default browser on the native platform.
     *
     * This method returns immediately, not later after the browser exits.
     * On Windows, this method does the same thing as Start, Run.
     *
     * @param url The Web address to open, like "http://www.frostwire.com/"
     * @return    0, in place of the process exit code
     */
    public static int openURL(String url) throws IOException {
        if (OSUtils.isWindows() && isLoaded) {
            openURLNative(url);
            return 0; // program's still running, no way of getting an exit code.
        }

        throw new IOException("native code not linked");
    }

    /**
     * Runs a path using the default program on the native platform.
     *
     * Given a path to a program, runs that program.
     * Given a path to a document, opens it in the default program for that kind of document.
     * Given a path to a folder, opens it in the shell.
     *
     * This method returns immediately, not later after the program exits.
     * On Windows, this method does the same thing as Start, Run.
     *
     * @param path The complete path to run, like "C:\folder\file.ext"
     * @return     0, in place of the process exit code
     */
    public static int openFile(String path) throws IOException {
        if (OSUtils.isWindows() && isLoaded) {
            openFileNative(path);
            return 0; // program's running, no way to get exit code.
        }

        throw new IOException("native code not linked");
    }

    /**
     * Runs a path using the default program on the native platform.
     *
     * Given a path to a program, runs that program.
     * Given a path to a document, opens it in the default program for that kind of document.
     * Given a path to a folder, opens it in the shell.
     *
     * Note: this method accepts a parameter list thus should
     *        be generally used with executable files
     *
     * This method returns immediately, not later after the program exits.
     * On Windows, this method does the same thing as Start, Run.
     *
     * @param path The complete path to run, like "C:\folder\file.ext"
     * @param path The list of parameters to pass to the file
     * @return     0, in place of the process exit code
     */
    public static int openFile(String path, String params) throws IOException {
        if (OSUtils.isWindows() && isLoaded) {
            openFileParamsNative(path, params);
            return 0; // program's running, no way to get exit code.
        }

        throw new IOException("native code not linked");
    }

    public static String getShortFileName(String fileName) {
        return (OSUtils.isWindows() && isLoaded) ? getShortFileNameNative(fileName) : fileName;
    }

    /**
     * Moves a file to the platform-specific trash can or recycle bin.
     *
     * @param file The file to trash
     * @return     True on success
     */
    public static boolean recycle(File file) {
        if (OSUtils.isWindows() && isLoaded) {
            // Get the path to the file
            String path = null;
            try {
                path = file.getCanonicalPath();
            } catch (IOException err) {
                LOG.error("IOException", err);
                path = file.getAbsolutePath();
            }

            // Use native code to move the file at that path to the recycle bin
            return recycleNative(path);

        } else {
            return false;
        }
    }

    /**
     * @return the default String that the shell will execute to open
     * a file with the provided extension.
     * Only supported on windows.
     */
    public static String getDefaultExtentionHandler(String extention) {
        if (!OSUtils.isWindows() || !isLoaded) {
            return null;
        }

        if (!extention.startsWith(".")) {
            extention = "."+extention;
        }

        try {
            String progId = registryReadText("HKEY_CLASSES_ROOT", extention,"");
            return ("".equals(progId)) ? "" : registryReadText("HKEY_CLASSES_ROOT",progId+"\\shell\\open\\command","");
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

        String extention = "";
        try {
            extention = registryReadText("HKEY_CLASSES_ROOT","MIME\\Database\\Content Type\\" + mimeType,"Extension");
        } catch (IOException iox) {
            return null;
        }

        return ("".equals(extention)) ? "" : getDefaultExtentionHandler(extention);
    }

    /*
     * The following methods are implemented in C++ code in SystemUtilities.dll.
     * In addition, setFileWritable(String) may be implemented in FrostWire's native library for another platform, like Mac or Linux.
     * The idea is that the Windows, Mac, and Linux libraries have methods with the same names.
     * Call a method, and it will run platform-specific code to complete the task in the appropriate platform-specific way.
     */

    private static final native String getRunningPathNative();
    private static final native String getSpecialPathNative(String name);
    private static final native String getShortFileNameNative(String fileName);
    private static final native void openURLNative(String url);
    private static final native void openFileNative(String path);
    private static final native void openFileParamsNative(String path, String params);
    private static final native boolean recycleNative(String path);
    private static final native int setFileWriteable(String path);
    private static final native String setWindowIconNative(Component frame, String bin, String icon);
    private static final native String setWindowTopMostNative(Component frame, String bin);
    private static final native boolean flushIconCacheNative();
    private static final native boolean toggleFullScreenNative(long hwnd);

    private static final native int registryReadNumberNative(String root, String path, String name) throws IOException ;
    private static final native String registryReadTextNative(String root, String path, String name) throws IOException;
    private static final native boolean registryWriteNumberNative(String root, String path, String name, int value);
    private static final native boolean registryWriteTextNative(String root, String path, String name, String value);
    private static final native boolean registryDeleteNative(String root, String path);

    private static final native boolean firewallPresentNative();
    private static final native boolean firewallEnabledNative();
    private static final native boolean firewallExceptionsNotAllowedNative();
    private static final native boolean firewallIsProgramListedNative(String path);
    private static final native boolean firewallIsProgramEnabledNative(String path);
    private static final native boolean firewallAddNative(String path, String name);
    private static final native boolean firewallRemoveNative(String path);
}
