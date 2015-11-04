/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

import java.io.File;
import java.io.IOException;

import javax.swing.JOptionPane;

import org.apache.commons.io.FilenameUtils;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;

import com.frostwire.logging.Logger;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UpdateSettings;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public final class UpdateMediator {

    private static final Logger LOG = Logger.getLogger(UpdateMediator.class);

    private UpdateMessage latestMsg;

    private static UpdateMediator instance;

    public static UpdateMediator instance() {
        if (instance == null) {
            instance = new UpdateMediator();
        }
        return instance;
    }

    private UpdateMediator() {
    }

    public boolean isUpdated() {
        return latestMsg != null && latestMsg.getVersion() != null && !latestMsg.getVersion().trim().equals("") && !UpdateManager.isFrostWireOld(latestMsg.getVersion());
    }

    public String getLatestVersion() {
        return latestMsg != null ? latestMsg.getVersion() : "";
    }

    public boolean isUpdateDownloading() {
        return InstallerUpdater.isDownloadingUpdate();
    }

    public boolean isUpdateDownloaded() {
        if (latestMsg == null) {
            return false;
        }

        String lastMD5 = InstallerUpdater.getLastMD5();

        if (lastMD5 == null) {
            return false;
        }

        return lastMD5.equalsIgnoreCase(latestMsg.getRemoteMD5().trim());
    }

    public File getUpdateBinaryFile() {
        try {
            if (latestMsg == null) {
                return null;
            }

            String installerFilename = null;

            if (latestMsg.getTorrent() != null) {
                int indx1 = latestMsg.getTorrent().lastIndexOf('/') + 1;
                int indx2 = latestMsg.getTorrent().lastIndexOf(".torrent");

                installerFilename = latestMsg.getTorrent().substring(indx1, indx2);
            } else if (latestMsg.getInstallerUrl() != null) {
                int indx1 = latestMsg.getInstallerUrl().lastIndexOf('/') + 1;

                installerFilename = latestMsg.getInstallerUrl().substring(indx1);
            }

            File f = new File(UpdateSettings.UPDATES_DIR, installerFilename);

            if (installerFilename == null || !f.exists()) {
                return null;
            }

            return f;
        } catch (Throwable e) {
            LOG.error("Error getting update binary path", e);
        }

        return null;
    }

    public void startUpdate() {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                File executableFile = getUpdateBinaryFile();
                if (executableFile == null || latestMsg == null) {
                    return;
                }
                openInstallerAndShutdown(executableFile);
            }
        });
    }

    public static void openInstallerAndShutdown(File executableFile) {
        try {
            if (CommonUtils.isPortable()) {
                //UpdateMediator.instance().installPortable(executableFile);
                return; // pending refactor
            }

            if (OSUtils.isWindows()) {
                String[] commands = new String[] { "CMD.EXE", "/C", executableFile.getAbsolutePath() };
                ProcessBuilder pbuilder = new ProcessBuilder(commands);
                pbuilder.start();
            } else if (OSUtils.isLinux() && OSUtils.isUbuntu()) {
                installUbuntu(executableFile);
            } else if (OSUtils.isMacOSX()) {
                final String[] mountCommand = new String[] { "hdiutil", "attach", executableFile.getAbsolutePath() };
                final String[] finderShowCommand = new String[] { "open", "/Volumes/" + FilenameUtils.getBaseName(executableFile.getName()) };
                final String[] finderShowCommandFallback = new String[]{"open", "file:///Volumes/Frostwire Installer"};

                ProcessBuilder pbuilder = new ProcessBuilder(mountCommand);
                Process mountingProcess = pbuilder.start();
                mountingProcess.waitFor();

                pbuilder = new ProcessBuilder(finderShowCommand);
                Process showProcess = pbuilder.start();
                showProcess.waitFor();

                pbuilder = new ProcessBuilder(finderShowCommandFallback);
                showProcess = pbuilder.start();
                showProcess.waitFor();

                Runtime.getRuntime().exec(finderShowCommandFallback);
            }

            GUIMediator.shutdown();
        } catch (Throwable e) {
            LOG.error("Unable to launch new installer", e);
        }
    }

    public void checkForUpdate() {
        latestMsg = null;
        UpdateManager.scheduleUpdateCheckTask(0,true);
    }

    public void setUpdateMessage(UpdateMessage msg) {
        this.latestMsg = msg;
    }

    public void showUpdateMessage() {
        if (latestMsg == null) {
            return;
        }

        DialogOption result = GUIMediator.showYesNoMessage(latestMsg.getMessageInstallerReady(), I18n.tr("Update"), JOptionPane.INFORMATION_MESSAGE);

        if (result == DialogOption.YES) {
            startUpdate();
        }
    }

    void installPortable(File executableFile) {
        PortableUpdater pu = new PortableUpdater(executableFile);
        pu.update();
    }

    static void installUbuntu(File executableFile) throws IOException {
        boolean success = trySoftwareCenter(executableFile) || tryGdebiGtk(executableFile);

        if (!success) {
            throw new IOException("Unable to install update");
        }
    }

    private static boolean trySoftwareCenter(File executableFile) {
        return tryUbuntuInstallCmd("/usr/bin/software-center", executableFile);
    }

    private static boolean tryGdebiGtk(File executableFile) {
        return tryUbuntuInstallCmd("gdebi-gtk", executableFile);
    }

    private static boolean tryUbuntuInstallCmd(String cmd, File executableFile) {
        try {
            String[] commands = new String[] { cmd, executableFile.getAbsolutePath() };

            ProcessBuilder pbuilder = new ProcessBuilder(commands);
            pbuilder.start();

            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}
