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

package com.limegroup.gnutella;

import com.limegroup.gnutella.settings.LibrarySettings;
import org.limewire.util.CommonUtils;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is the glue that holds LimeWire together.
 * All various components are wired together here.
 */
public class LimeCoreGlue {
    private static final AtomicBoolean preinstalled = new AtomicBoolean(false);
    private static LimeCoreGlue INSTANCE;
    private final AtomicBoolean installed = new AtomicBoolean(false);

    private LimeCoreGlue() {
    }

    public static LimeCoreGlue instance() {
        if (INSTANCE == null) {
            INSTANCE = new LimeCoreGlue();
        }
        return INSTANCE;
    }

    /**
     * Wires initial pieces together that are required for nearly everything.
     */
    public static void preinstall() throws InstallFailedException {
        Properties metaConfiguration = CommonUtils.loadMetaConfiguration();
        File portableSettingsDir = null;
        if (!metaConfiguration.isEmpty()) {
            portableSettingsDir = CommonUtils.getPortableSettingsDir(metaConfiguration);
        }
        File userSettingsDir = (portableSettingsDir == null) ? CommonUtils.getUserSettingsDir() : portableSettingsDir;
        preinstall(userSettingsDir);
    }

    /**
     * Wires initial pieces together that are required for nearly everything.
     *
     * @param userSettingsDir the preferred directory for user settings
     */
    private static void preinstall(File userSettingsDir) throws InstallFailedException {
        // Only preinstall once
        if (!preinstalled.compareAndSet(false, true))
            return;
        // This looks a lot more complicated than it really is.
        // The excess try/catch blocks are just to make debugging easier,
        // to keep track of what messages each successive IOException is.
        // The flow is basically:
        //  - Try to set the settings dir to the requested location.
        //  - If that doesn't work, try getting a temporary directory to use.
        //  - If we can't find a temporary directory, deleting old stale ones & try again.
        //  - If it still doesn't work, bail.
        //  - If it did work, mark it for deletion & set it as the settings directory.
        //  - If it can't be set, bail.
        //  - Otherwise, success.
        try {
            CommonUtils.setUserSettingsDir(userSettingsDir);
            LibrarySettings.resetLibraryFoldersIfPortable();
        } catch (Exception e) {
            throw new InstallFailedException("Settings Directory Failure", e);
        }
    }

    /**
     * Wires all various components together.
     */
    public void install() {
        // Only install once.
        if (!installed.compareAndSet(false, true))
            return;
        preinstall(); // Ensure we're preinstalled.
    }

    /**
     * Simple exception for failure to install.
     */
    public static class InstallFailedException extends RuntimeException {
        InstallFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
