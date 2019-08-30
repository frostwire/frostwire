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

package com.limegroup.gnutella.util;

import org.apache.commons.io.IOUtils;
import org.limewire.setting.SettingsFactory;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;
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
    private static final String FROSTWIRE_VERSION = "6.8.2";
    /**
     * Build number for the current version, gets reset to 1 on every version bump
     */
    private static final int BUILD_NUMBER = 288;
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
        if (OSUtils.isWindowsVista() || OSUtils.isWindows7()) {
            root = SystemUtils.getSpecialPath(SpecialLocations.DOWNLOADS);
        } else if (OSUtils.isWindows()) {
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
        File musicFile;
        if (OSUtils.isMacOSX()) {
            musicFile = new File(CommonUtils.getUserHomeDir(), "Music");
        } else if (OSUtils.isWindowsXP()) {
            musicFile = new File(CommonUtils.getUserHomeDir(), "My Documents" + File.separator + "My Music");
        } else if (OSUtils.isWindowsVista() || OSUtils.isWindows7()) {
            musicFile = new File(CommonUtils.getUserHomeDir(), "Music");
        } else if (OSUtils.isUbuntu()) {
            musicFile = new File(CommonUtils.getUserHomeDir(), "Music");
        } else {
            musicFile = new File(CommonUtils.getUserHomeDir(), "Music");
        }
        return musicFile;
    }

    public static File getUserVideoFolder() {
        File videoFile;
        if (OSUtils.isMacOSX()) {
            videoFile = new File(CommonUtils.getUserHomeDir(), "Movies");
        } else if (OSUtils.isWindowsXP()) {
            videoFile = new File(CommonUtils.getUserHomeDir(), "My Documents" + File.separator + "My Videos");
        } else if (OSUtils.isWindowsVista() || OSUtils.isWindows7()) {
            videoFile = new File(CommonUtils.getUserHomeDir(), "Videos");
        } else if (OSUtils.isUbuntu()) {
            videoFile = new File(CommonUtils.getUserHomeDir(), "Videos");
        } else {
            videoFile = new File(CommonUtils.getUserHomeDir(), "Videos");
        }
        return videoFile;
    }

    /**
     * @return 4 int array with { MAJOR.MINOR.REVISION.BUILD
     */
    public static void getFrostWireVersionBuild(final int[] result) {
        String[] vStrArray = getFrostWireVersion().split("\\.");
        result[0] = Integer.parseInt(vStrArray[0]);
        result[1] = Integer.parseInt(vStrArray[1]);
        result[2] = Integer.parseInt(vStrArray[2]);
        result[3] = getBuildNumber();
    }
}
