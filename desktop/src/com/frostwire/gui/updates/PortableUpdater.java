/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014,, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.updates;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;

import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;

import com.frostwire.logging.Logger;
import com.frostwire.util.ZipUtils;
import com.frostwire.util.ZipUtils.ZipListener;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.ResourceManager;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class PortableUpdater {

    private static final Logger LOG = Logger.getLogger(PortableUpdater.class);

    private static final String PORTABLE_UPDATER_SCRIPT_WINDOWS = "portable_updater.js";
    private static final String PORTABLE_UPDATER_SCRIPT_MACOSX = "portable_updater.sh";
    private static final String TEMP_DIR = "FrostWire_temp";

    private final File zipFile;
    private final File tempDir;
    private final File destDir;

    public PortableUpdater(File zipFile) {
        if (OSUtils.isWindows()) {
            createScript(PORTABLE_UPDATER_SCRIPT_WINDOWS);
        } else if (OSUtils.isMacOSX()) {
            createScript(PORTABLE_UPDATER_SCRIPT_MACOSX);
        }

        File rootFolder = CommonUtils.getPortableRootFolder();

        this.zipFile = zipFile;
        this.tempDir = new File(FilenameUtils.normalize(new File(rootFolder, TEMP_DIR).getAbsolutePath()));
        this.destDir = new File(FilenameUtils.normalize(new File(rootFolder, getDestDirName()).getAbsolutePath()));
    }

    public void update() {
        ProgressMonitor progressMonitor = new ProgressMonitor(GUIMediator.getAppFrame(), I18n.tr("Uncompressing files"), "", 0, 100);
        progressMonitor.setMillisToDecideToPopup(0);
        progressMonitor.setMillisToPopup(0);
        progressMonitor.setProgress(0);
        UncompressTask task = new UncompressTask(progressMonitor);
        task.execute();
    }

    private String getDestDirName() {
        String name = "FrostWire"; // default name?

        if (OSUtils.isWindows()) {
            name = "FrostWire";
        } else if (OSUtils.isMacOSX()) {
            name = "FrostWire.app";
        }

        return name;
    }

    private void fixExecutablePermissions(File file, String[] exePaths) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                fixExecutablePermissions(child, exePaths);
            }
        } else {
            for (String path : exePaths) {
                if (file.getPath().contains(path)) {
                    file.setExecutable(true);
                }
            }
        }
    }

    private static void createScript(String scriptName) {
        File fileJS = new File(CommonUtils.getUserSettingsDir(), scriptName);
        //        if (fileJS.exists()) {
        //            return;
        //        }

        URL url = ResourceManager.getURLResource(scriptName);

        InputStream is = null;
        OutputStream out = null;

        try {
            if (url != null) {
                is = new BufferedInputStream(url.openStream());
                out = new FileOutputStream(fileJS);
                IOUtils.copy(is, out);

                fileJS.setExecutable(true);
            }
        } catch (IOException e) {
            LOG.error("Error creating script", e);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(out);
        }
    }

    private static String[] createWSHScriptCommand(File source, File dest) {
        ArrayList<String> command = new ArrayList<String>();
        command.add("wscript");
        command.add("//B");
        command.add("//NoLogo");
        command.add(new File(CommonUtils.getUserSettingsDir(), PORTABLE_UPDATER_SCRIPT_WINDOWS).getAbsolutePath());
        command.add(source.getAbsolutePath());
        command.add(dest.getAbsolutePath());

        return command.toArray(new String[0]);
    }

    private static String[] createMacOSXScriptCommand(File source, File dest) {
        ArrayList<String> command = new ArrayList<String>();
        command.add(new File(CommonUtils.getUserSettingsDir(), PORTABLE_UPDATER_SCRIPT_MACOSX).getAbsolutePath());
        command.add(source.getAbsolutePath());
        command.add(dest.getAbsolutePath());

        return command.toArray(new String[0]);
    }

    private class UncompressTask extends SwingWorker<Void, Void> {

        private final ProgressMonitor progressMonitor;

        public UncompressTask(ProgressMonitor progressMonitor) {
            this.progressMonitor = progressMonitor;
        }

        @Override
        public Void doInBackground() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    progressMonitor.setProgress(0);
                }
            });

            ZipUtils.unzip(zipFile, tempDir, new ZipListener() {

                @Override
                public void onUnzipping(final String fileName, final int progress) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            progressMonitor.setNote(fileName);
                            progressMonitor.setProgress(progress);
                        }
                    });
                }

                @Override
                public boolean isCanceled() {
                    return progressMonitor.isCanceled();
                }
            });

            return null;
        }

        @Override
        public void done() {
            progressMonitor.close();
            try {

                if (OSUtils.isWindows()) {
                    Runtime.getRuntime().exec(createWSHScriptCommand(tempDir, destDir));
                } else if (OSUtils.isMacOSX()) {
                    fixExecutablePermissions(tempDir, new String[] { "MacOS", "Home/bin" });
                    Runtime.getRuntime().exec(createMacOSXScriptCommand(tempDir, destDir));
                }
            } catch (IOException e) {
                LOG.error("Failed to execute update script", e);
            }

            GUIMediator.shutdown();
        }
    }
}
