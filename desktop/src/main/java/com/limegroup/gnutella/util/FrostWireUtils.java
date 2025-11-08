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

package com.limegroup.gnutella.util;

import com.frostwire.util.OSUtils;
import org.apache.commons.io.IOUtils;
import org.limewire.setting.SettingsFactory;
import org.limewire.util.CommonUtils;
import org.limewire.util.SystemUtils;
import org.limewire.util.SystemUtils.SpecialLocations;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * This class handles common utility functions that many classes
 * may want to access.
 */
public final class FrostWireUtils {
    /**
     * Constant for the current version of FrostWire.
     */
    private static final String FROSTWIRE_VERSION = "7.0.0";
    /**
     * Build number for the current version, gets reset to 1 on every version bump
     */
    private static final int BUILD_NUMBER = 327;
    private static final boolean IS_RUNNING_FROM_SOURCE = new File("README.md").exists();

    /**
     * Make sure the constructor can never be called.
     */
    private FrostWireUtils() {
    }

    /**
     * Returns the current version number of FrostWire as
     * a string, e.g., "5.2.9".
     */
    public static String getFrostWireVersion() {
        return FROSTWIRE_VERSION;
    }

    public static int getBuildNumber() {
        return BUILD_NUMBER;
    }

    public static boolean isIsRunningFromSource() {
        return IS_RUNNING_FROM_SOURCE;
    }

    /**
     * Returns whether or not failures were encountered in load/save settings on startup.
     */
    public static boolean hasSettingsLoadSaveFailures() {
        return SettingsFactory.hasLoadSaveFailure();
    }

    public static void resetSettingsLoadSaveFailures() {
        SettingsFactory.resetLoadSaveFailure();
    }

    /**
     * Returns the path of the FrostWire.jar executable.
     * For a windows binary distribution this is the same path as FrostWire.exe since those files live together.
     */
    public static String getFrostWireJarPath() {
        return new File(FrostWireUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
    }

    /**
     * Returns the root folder from which all Saved/Shared/etc..
     * folders should be placed.
     */
    public static File getFrostWireRootFolder() {
        String root = null;
        if (OSUtils.isWindows()) {
            root = SystemUtils.getSpecialPath(SpecialLocations.DOCUMENTS);
        }
        if (root == null || "".equals(root))
            root = CommonUtils.getUserHomeDir().getPath();
        return new File(root, "FrostWire");
    }

    public static Set<File> getFrostWire4SaveDirectories() {
        Set<File> result = new HashSet<>();
        try {
            File settingFile = new File(CommonUtils.getFrostWire4UserSettingsDir(), "frostwire.props");
            Properties props = new Properties();
            FileInputStream fis = new FileInputStream(settingFile);
            props.load(fis);
            IOUtils.closeQuietly(fis);
            if (props.containsKey("DIRECTORY_FOR_SAVING_FILES")) {
                result.add(new File(props.getProperty("DIRECTORY_FOR_SAVING_FILES")));
            }
            String[] types = new String[]{"document", "application", "audio", "video", "image"};
            for (String type : types) {
                String key = "DIRECTORY_FOR_SAVING_" + type + "_FILES";
                if (props.containsKey(key)) {
                    result.add(new File(props.getProperty(key)));
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    public static File getUserMusicFolder() {
        return new File(CommonUtils.getUserHomeDir(), "Music");
    }

    public static File getUserVideoFolder() {
        return (OSUtils.isMacOSX()) ?
                new File(CommonUtils.getUserHomeDir(), "Movies") : // Videos dir for macOS
                new File(CommonUtils.getUserHomeDir(), "Videos");  // Videos dir for all other platforms
    }

    /**
     * update the 4 int result array with { MAJOR, MINOR, REVISION, BUILD }
     */
    public static void getFrostWireVersionBuild(final int[] result) {
        String[] vStrArray = getFrostWireVersion().split("\\.");
        result[0] = Integer.parseInt(vStrArray[0]);
        result[1] = Integer.parseInt(vStrArray[1]);
        result[2] = Integer.parseInt(vStrArray[2]);
        result[3] = getBuildNumber();
    }

    public static String getDevelopmentFrostWireDesktopFolderPath() {
        File fwJarFolder = new File(FrostWireUtils.getFrostWireJarPath()).getParentFile();
        String pathPrefix;
        // From Command line:
        // fwJarFolder=.../frostwire/desktop/build
        // .../frostwire/desktop/lib/native/fwplayer_macos.<arch>
        if (fwJarFolder.getAbsolutePath().endsWith("build")) {
            pathPrefix = fwJarFolder.getParentFile().getAbsolutePath();
        } else {
            // From IntelliJ:
            // fwJarFolder=.../frostwire/desktop/build/classes
            // .../frostwire/desktop/build/lib/native/fwplayer_macos.<arch>
            pathPrefix = fwJarFolder.getParentFile().getParentFile().getAbsolutePath();
        }
        return pathPrefix;
    }

    /**
     * Determines the path of the telluride launcher relative to FrostWire and returns the corresponding File object.
     * It should determine it regardless of running from source or from a binary distribution.
     */
    public static File getTellurideLauncherFile() {
        boolean isRelease = !FrostWireUtils.getFrostWireJarPath().contains("frostwire" + File.separatorChar + "desktop");
        if (isRelease) {
            if (OSUtils.isWindows()) {
                // Should be on the same folder as frostwire.exe
                return new File("telluride.exe");
            } else if (OSUtils.isAnyMac()) {
                String javaHome = System.getProperty("java.home");

                //System.out.println("FrostWireUtils.getTellurideLauncherFile(): java.home -> " + javaHome);
                // FrostWireUtils.getTellurideLauncherFile(): java.home -> /Path/To/FrostWire.app/Contents/PlugIns/jre/Contents/Home
                File f = new File(javaHome).getAbsoluteFile();
                f = f.getParentFile(); // Contents
                f = f.getParentFile(); // jre
                f = f.getParentFile(); // PlugIns
                f = f.getParentFile(); // Contents
                return new File(f, "MacOS" + File.separator + "telluride_macos." + OSUtils.getMacOSArchitecture());
            } else if (OSUtils.isLinux()) {
		String executable_name = "telluride_linux." + OSUtils.getMacOSArchitecture();
                File candidate1 = new File("/usr/lib/frostwire", executable_name);
                if (candidate1.exists()) {
                    return candidate1;
                }
                // maybe running from extracted .tar.gz installer
                File candidate2 = new File(executable_name);
                if (candidate2.exists()) {
                    return candidate2;
                }
            }
        } else {
	    // running from gradlew
            String pathPrefix = getDevelopmentFrostWireDesktopFolderPath() + File.separatorChar + ".." + File.separatorChar + "telluride";
            if (OSUtils.isWindows()) {
                return new File(pathPrefix, "telluride.exe");
            } else if (OSUtils.isAnyMac()) {
                return new File(pathPrefix, "telluride_macos." + OSUtils.getMacOSArchitecture());
            } else if (OSUtils.isLinux()) {
                return new File(pathPrefix, "telluride_linux." + OSUtils.getMacOSArchitecture());
            }
        }
        return null;
    }
}
