/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.updates;

import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UpdateSettings;
import java.io.File;
import java.io.IOException;
import javax.swing.*;
import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public final class UpdateMediator {
  private static final Logger LOG = Logger.getLogger(UpdateMediator.class);
  private static UpdateMediator instance;
  private UpdateMessage latestMsg;

  private UpdateMediator() {}

  public static UpdateMediator instance() {
    if (instance == null) {
      instance = new UpdateMediator();
    }
    return instance;
  }

  /**
   * The file name the installer is saved as: the announced saveAs name, else the name of the
   * torrent (without ".torrent") or the last segment of the installer URL; null when the message
   * names none of them.
   */
  static String getInstallerFilename(UpdateMessage message) {
    String saveAs = message.getSaveAs();
    if (saveAs != null && !saveAs.isEmpty()) {
      return saveAs;
    }
    String torrentName = lastPathSegment(message.getTorrent());
    if (torrentName != null) {
      if (torrentName.endsWith(".torrent") && torrentName.length() > ".torrent".length()) {
        torrentName = torrentName.substring(0, torrentName.length() - ".torrent".length());
      }
      return torrentName;
    }
    return lastPathSegment(message.getInstallerUrl());
  }

  /** The last segment of the path of the URL, without query or fragment; null if there is none. */
  private static String lastPathSegment(String url) {
    if (url == null) {
      return null;
    }
    int end = url.length();
    int query = url.indexOf('?');
    if (query >= 0) {
      end = query;
    }
    int fragment = url.indexOf('#');
    if (fragment >= 0 && fragment < end) {
      end = fragment;
    }
    String segment = url.substring(url.lastIndexOf('/', end - 1) + 1, end);
    return segment.isEmpty() ? null : segment;
  }

  static void openInstallerAndShutdown(File executableFile) {
    try {
      //            if (CommonUtils.isPortable()) {
      //                //UpdateMediator.instance().installPortable(executableFile);
      //                return; // pending refactor
      //            }
      if (OSUtils.isWindows()) {
        String[] commands = new String[] {"CMD.EXE", "/C", executableFile.getAbsolutePath()};
        ProcessBuilder pbuilder = new ProcessBuilder(commands);
        pbuilder.start();
      } else if (OSUtils.isLinux() && OSUtils.isUbuntu()) {
        installUbuntu(executableFile);
      } else if (OSUtils.isMacOSX()) {
        final String[] mountCommand =
            new String[] {"hdiutil", "attach", executableFile.getAbsolutePath()};
        final String[] finderShowCommand =
            new String[] {
              "open", "/Volumes/" + FilenameUtils.getBaseName(executableFile.getName())
            };
        final String[] finderShowCommandFallback =
            new String[] {"open", "file:///Volumes/Frostwire"};
        final String[] finderShowCommandFallback2 =
            new String[] {"open", "file:///Volumes/Frostwire Installer"};
        ProcessBuilder pbuilder = new ProcessBuilder(mountCommand);
        Process mountingProcess = pbuilder.start();
        mountingProcess.waitFor();
        pbuilder = new ProcessBuilder(finderShowCommand);
        Process showProcess = pbuilder.start();
        showProcess.waitFor();
        pbuilder = new ProcessBuilder(finderShowCommandFallback);
        showProcess = pbuilder.start();
        showProcess.waitFor();
        pbuilder = new ProcessBuilder(finderShowCommandFallback2);
        showProcess = pbuilder.start();
        showProcess.waitFor();
        Runtime.getRuntime().exec(finderShowCommandFallback);
      }
      GUIMediator.shutdown();
    } catch (Throwable e) {
      LOG.error("Unable to launch new installer", e);
    }
  }

  private static void installUbuntu(File executableFile) throws IOException {
    boolean success =
        tryGnomeSoftware(executableFile)
            || tryGdebiGtk(executableFile)
            || trySoftwareCenter(executableFile);
    if (!success) {
      throw new IOException("Unable to install update");
    }
  }

  private static boolean tryGnomeSoftware(File executableFile) {
    return tryUbuntuInstallCmd(
        "/usr/bin/gnome-software",
        "--local-filename=" + executableFile.getAbsolutePath(),
        "--verbose");
  }

  private static boolean tryGdebiGtk(File executableFile) {
    return tryUbuntuInstallCmd("gdebi-gtk", executableFile);
  }

  private static boolean trySoftwareCenter(File executableFile) {
    return tryUbuntuInstallCmd("/usr/bin/software-center", executableFile);
  }

  private static boolean tryUbuntuInstallCmd(String cmd, File executableFile) {
    return tryUbuntuInstallCmd(cmd, executableFile.getAbsolutePath());
  }

  private static boolean tryUbuntuInstallCmd(String cmd, String... options) {
    try {
      int options_length = (options == null || options.length == 0) ? 0 : options.length;
      String[] commands = new String[1 + options_length];
      commands[0] = cmd;
      if (options != null) {
        System.arraycopy(options, 0, commands, 1, options_length);
      }
      System.out.print("UpdateMediator.tryUbuntuInstallCmd: ");
      for (String c : commands) {
        System.out.print(c + " ");
      }
      System.out.println();
      ProcessBuilder pbuilder = new ProcessBuilder(commands);
      pbuilder.start();
      return true;
    } catch (Throwable e) {
      e.printStackTrace();
      return false;
    }
  }

  public boolean isUpdated() {
    return latestMsg != null
        && latestMsg.getVersion() != null
        && !latestMsg.getVersion().trim().equals("")
        && !UpdateManager.isFrostWireOld(latestMsg);
  }

  public String getLatestVersion() {
    return latestMsg != null ? latestMsg.getVersion() : "";
  }

  public boolean isUpdateDownloading() {
    return InstallerUpdater.isDownloadingUpdate();
  }

  //    void installPortable(File executableFile) {
  //        PortableUpdater pu = new PortableUpdater(executableFile);
  //        pu.update();
  //    }

  public boolean isUpdateDownloaded() {
    if (latestMsg == null) {
      return false;
    }
    String lastMD5 = InstallerUpdater.getLastMD5();
    return lastMD5 != null && lastMD5.equalsIgnoreCase(latestMsg.getRemoteMD5().trim());
  }

  private File getUpdateBinaryFile() {
    try {
      if (latestMsg == null) {
        return null;
      }
      String installerFilename = getInstallerFilename(latestMsg);
      if (installerFilename == null) {
        return null;
      }
      File f = new File(UpdateSettings.UPDATES_DIR, installerFilename);
      if (!f.exists()) {
        return null;
      }
      return f;
    } catch (Throwable e) {
      LOG.error("Error getting update binary path", e);
    }
    return null;
  }

  public void startUpdate() {
    GUIMediator.safeInvokeLater(
        () -> {
          File executableFile = getUpdateBinaryFile();
          if (executableFile == null || latestMsg == null) {
            return;
          }
          openInstallerAndShutdown(executableFile);
        });
  }

  public void checkForUpdate() {
    latestMsg = null;
    UpdateManager.scheduleUpdateCheckTask(0, true);
  }

  void setUpdateMessage(UpdateMessage msg) {
    this.latestMsg = msg;
  }

  public void showUpdateMessage() {
    if (latestMsg == null) {
      return;
    }
    DialogOption result =
        GUIMediator.showYesNoMessage(
            latestMsg.getMessageInstallerReady(),
            I18n.tr("Update"),
            JOptionPane.INFORMATION_MESSAGE);
    if (result == DialogOption.YES) {
      startUpdate();
    }
  }

  public int getUpdateDownloadProgress() {
    return InstallerUpdater.getUpdateDownloadProgress();
  }
}
