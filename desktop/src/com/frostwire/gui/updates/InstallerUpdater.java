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

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.BlockFinishedAlert;
import com.frostwire.jlibtorrent.alerts.FileErrorAlert;
import com.frostwire.jlibtorrent.alerts.TorrentFinishedAlert;
import com.frostwire.logging.Logger;
import com.frostwire.gui.DigestUtils;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.HttpClient.HttpRangeException;
import com.frostwire.util.HttpClientFactory;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UpdateSettings;
import org.limewire.util.CommonUtils;

import javax.swing.*;
import java.io.*;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author gubatron
 * @author aldenml
 */
public class InstallerUpdater implements Runnable {

    private static final Logger LOG = Logger.getLogger(InstallerUpdater.class);

    private TorrentHandle _manager = null;
    private UpdateMessage _updateMessage;
    private File _executableFile;

    private static String lastMD5;
    private static boolean isDownloadingUpdate = false;

    private final boolean forceUpdate;

    public InstallerUpdater(UpdateMessage updateMessage, boolean force) {
        _updateMessage = updateMessage;
        forceUpdate = force;
        isDownloadingUpdate = false;
    }

    public void start() {
        new Thread(this, "InstallerUpdater").start();
    }

    public static boolean isDownloadingUpdate() {
        return isDownloadingUpdate;
    }

    public void run() {
        if (!forceUpdate && !UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.getValue()) {
            return;
        }

        if (checkIfDownloaded()) {
            showUpdateMessage();
        } else {
            isDownloadingUpdate = true;

            if (_updateMessage.getTorrent() != null) {
                handleTorrentDownload();
            } else if (_updateMessage.getInstallerUrl() != null) {
                handleHttpDownload();
            } else {
                isDownloadingUpdate = false;
            }
        }
    }

    private void handleTorrentDownload() {
        File torrentFileLocation = downloadDotTorrent();

        try {

            // workaround to java issue
            // http://bugs.sun.com/bugdatabase/view_bug.do;:YfiG?bug_id=4483097
            boolean exists = torrentFileLocation.exists() || torrentFileLocation.getAbsoluteFile().exists();
            if (torrentFileLocation != null && exists) {
                _manager = startTorrentDownload(torrentFileLocation.getAbsolutePath(), UpdateSettings.UPDATES_DIR.getAbsolutePath());
            } else {
                isDownloadingUpdate = false;
            }

        } catch (Throwable e) {
            isDownloadingUpdate = false;
            LOG.error("Error starting update torrent download", e);
        }
    }

    private void handleHttpDownload() {
        File updateFolder = UpdateSettings.UPDATES_DIR;

        int index = _updateMessage.getInstallerUrl().lastIndexOf('/');
        File installerFileLocation = new File(updateFolder, _updateMessage.getInstallerUrl().substring(index + 1));

        if (!updateFolder.exists()) {
            updateFolder.mkdir();
            updateFolder.setWritable(true);
        }
        try {
            HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
            try {
                httpClient.save(_updateMessage.getInstallerUrl(), installerFileLocation, true);
            } catch (HttpRangeException e) {
                // recovery in case the server does not support resume
                httpClient.save(_updateMessage.getInstallerUrl(), installerFileLocation, false);
            } catch (IOException e2) {
                if (e2.getMessage().contains("416")) {
                    // HTTP Request Range error came through IOException.
                    httpClient.save(_updateMessage.getInstallerUrl(), installerFileLocation, false);
                }
            }
            isDownloadingUpdate = false;
            saveMetaData();
            cleanupOldUpdates();

            if (checkIfDownloaded()) {
                showUpdateMessage();
            }
        } catch (Throwable e) {
            isDownloadingUpdate = false;
            LOG.error("Failed to download installer: " + _updateMessage.getInstallerUrl(), e);
        }
    }

    private final TorrentHandle startTorrentDownload(String torrentFile, String saveDataPath) throws Exception {
        final Session session = BTEngine.getInstance().getSession();

        TorrentHandle th = session.addTorrent(new File(torrentFile), new File(saveDataPath));

        session.addListener(new TorrentAlertAdapter(th) {

            @Override
            public void fileError(FileErrorAlert alert) {
                onStateChanged(th, th.getStatus().getState());
            }

            @Override
            public void blockFinished(BlockFinishedAlert alert) {
                onStateChanged(th, th.getStatus().getState());
            }

            @Override
            public void torrentFinished(TorrentFinishedAlert alert) {
                session.removeListener(this);
                onStateChanged(th, th.getStatus().getState());
                downloadComplete(th);
            }
        });

        th.resume();

        return th;
    }

    private void showUpdateMessage() {
        GUIMediator.safeInvokeLater(new Runnable() {
            public void run() {
                if (_executableFile == null) {
                    return;
                }

                DialogOption result = GUIMediator.showYesNoMessage(_updateMessage.getMessageInstallerReady(), I18n.tr("Update"), JOptionPane.INFORMATION_MESSAGE);

                if (result == DialogOption.YES) {
                    UpdateMediator.openInstallerAndShutdown(_executableFile);
                }
            }
        });
    }

    private File downloadDotTorrent() {

        File appSpecialShareFolder = UpdateSettings.UPDATES_DIR;

        int index = _updateMessage.getTorrent().lastIndexOf('/');
        File torrentFileLocation = new File(appSpecialShareFolder, _updateMessage.getTorrent().substring(index + 1));

        if (!appSpecialShareFolder.exists()) {
            appSpecialShareFolder.mkdir();
            appSpecialShareFolder.setWritable(true);
        }

        //We always re-download the torrent just in case.
        try {
            downloadTorrentFile(_updateMessage.getTorrent(), torrentFileLocation);
        } catch (Throwable e) {
            LOG.error("Error downloading update torrent file", e);
        }

        return torrentFileLocation;
    }

    private final InstallerMetaData getLastInstallerMetaData() {
        InstallerMetaData result = null;
        try {
            File installerDatFile = new File(getInstallerDatPath());

            if (!installerDatFile.exists()) {
                return null;
            }

            FileInputStream fis = new FileInputStream(installerDatFile);
            ObjectInputStream ois = new ObjectInputStream(fis);

            try {
                result = (InstallerMetaData) ois.readObject();

                if (result == null) {
                    return null;
                }
            } finally {
                fis.close();
                ois.close();
            }

            return result;

        } catch (Throwable e) {
            // processMessage will deal with us returning null
            LOG.info("Can't read installer meta data");
            return null;
        }
    }

    private boolean checkIfDownloaded() {

        InstallerMetaData md = getLastInstallerMetaData();

        if (md == null) {
            return false;
        }

        if (!md.frostwireVersion.equals(_updateMessage.getVersion())) {
            return false;
        }

        String installerFilename = null;

        if (_updateMessage.getTorrent() != null) {
            int indx1 = _updateMessage.getTorrent().lastIndexOf('/') + 1;
            int indx2 = _updateMessage.getTorrent().lastIndexOf(".torrent");

            installerFilename = _updateMessage.getTorrent().substring(indx1, indx2);
        } else if (_updateMessage.getInstallerUrl() != null) {
            int indx1 = _updateMessage.getInstallerUrl().lastIndexOf('/') + 1;

            installerFilename = _updateMessage.getInstallerUrl().substring(indx1);
        }

        File f = new File(UpdateSettings.UPDATES_DIR, installerFilename);

        if (installerFilename == null || !f.exists()) {
            return false;
        }

        _executableFile = f;

        try {
            lastMD5 = DigestUtils.getMD5(f);
            return DigestUtils.compareMD5(lastMD5, _updateMessage.getRemoteMD5());
        } catch (Throwable e) {
            LOG.error("Error checking update MD5", e);
            return false;
        }
    }

    private void onStateChanged(TorrentHandle manager, TorrentStatus.State state) {
        if (_manager == null && manager != null) {
            _manager = manager;
        }

        //printDiskManagerPieces(manager.getDiskManager());
        printDownloadManagerStatus(manager);

        if (torrentDataDownloadedToDisk()) {
            isDownloadingUpdate = false;
            return;
        }

        System.out.println("InstallerUpdater.stateChanged() - " + state + " completed: " + manager.getStatus().isFinished());
        if (state == TorrentStatus.State.SEEDING) {
            isDownloadingUpdate = false;
            System.out.println("InstallerUpdater.stateChanged() - SEEDING!");
            return;
        }

        //if (state == DownloadManager.STATE_ERROR) {
        ErrorCode error = manager.getStatus().errorCode();
        if (error != null && error.value() != 0) {
            isDownloadingUpdate = false;
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
            System.out.println(error);
            System.out.println("InstallerUpdater: ERROR - stopIt, startDownload!");
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");

            //try to restart the download. delete torrent and data
            //manager.stopIt(DownloadManager.STATE_READY, false, true);
            try {
                BTEngine.getInstance().getSession().removeTorrent(manager, Session.Options.DELETE_FILES);
                //processMessage(_updateMessage);
            } catch (Throwable e) {
                LOG.error("Error removing download manager on error", e);
            }

        } else if (state == TorrentStatus.State.DOWNLOADING) {
            System.out.println("stateChanged(STATE_DOWNLOADING)");
        } /*else if (state == DownloadManager.STATE_READY || state == TorrentStatus.State.STATE_QUEUED) {
            System.out.println("stateChanged(STATE_READY)");
            manager.startDownload();
        }*/
    }

    private void downloadComplete(TorrentHandle manager) {
        System.out.println("InstallerUpdater.downloadComplete()!!!!");
        printDownloadManagerStatus(_manager);

        saveMetaData();
        cleanupOldUpdates();

        if (checkIfDownloaded()) {
            showUpdateMessage();
        }
    }

    private void cleanupOldUpdates() {

        final Pattern p = Pattern.compile("^frostwire-([0-9]+[0-9]?\\.[0-9]+[0-9]?\\.[0-9]+[0-9]?)(.*?)(\\.torrent)?$");

        for (File f : UpdateSettings.UPDATES_DIR.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String name) {

                Matcher m = p.matcher(name);

                if (m.matches()) {
                    return !m.group(1).equals(_updateMessage.getVersion());
                }

                return false;
            }
        })) {

            f.delete();
        }
    }

    private final String getInstallerDatPath() {
        return CommonUtils.getUserSettingsDir().getAbsolutePath() + File.separator + "installer.dat";
    }

    private void saveMetaData() {
        try {
            String installerPath = getInstallerDatPath();

            InstallerMetaData md = new InstallerMetaData();
            md.frostwireVersion = _updateMessage.getVersion();

            File f = new File(installerPath);

            if (f.exists()) {
                f.delete();
            }

            f.createNewFile();

            FileOutputStream fos = new FileOutputStream(installerPath);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject((InstallerMetaData) md);

            fos.close();

        } catch (Throwable e) {
            LOG.info("Can't save update meta data");
        }
    }

    private boolean torrentDataDownloadedToDisk() {
        if (_manager == null) {
            return false;
        }

        if (!_manager.isValid()) {
            return false;
        }

        return _manager.getStatus().isFinished() || _manager.getStatus().isSeeding();

        /*
        String saveLocation = UpdateSettings.UPDATES_DIR.getAbsolutePath();
        File f = new File(saveLocation);

        DiskManager dm = _manager.getDiskManager();
        //boolean filesExist = dm.filesExist();		
        int percentDone = dm.getPercentDone();
        long totalLength = dm.getTotalLength();
        int rechecking = dm.getCompleteRecheckStatus();

        return f.exists() && f.length() == totalLength && percentDone == 1000 && rechecking == -1;
        */
    }

    /*
    public static void printDiskManagerPieces(DiskManager dm) {
        if (dm == null) {
            return;
        }
        DiskManagerPiece[] pieces = dm.getPieces();
        for (DiskManagerPiece piece : pieces) {
            System.out.print(piece.isDone() ? "1" : "0");
        }

        System.out.println();
    }*/

    public static void printDownloadManagerStatus(TorrentHandle manager) {
        if (manager == null) {
            return;
        }

        StringBuffer buf = new StringBuffer();
        buf.append(" Completed:");

        TorrentStatus stats = manager.getStatus();

        buf.append(stats.getProgress());
        buf.append('%');
        buf.append(" Seeds:");
        buf.append(stats.getNumSeeds());
        buf.append(" Peers:");
        buf.append(stats.getNumPeers());
        buf.append(" Downloaded:");
        buf.append(stats.getTotalDone());
        buf.append(" Uploaded:");
        buf.append(stats.getTotalUpload());
        buf.append(" DSpeed:");
        buf.append(stats.getDownloadRate());
        buf.append(" USpeed:");
        buf.append(stats.getUploadRate());
        //buf.append(" TrackerStatus:");
        //buf.append(manager.getTrackerStatus());
        while (buf.length() < 80) {
            buf.append(' ');
        }

        buf.append(" TO:");
        buf.append(manager.getSavePath());

        System.out.println(buf.toString());
    }

    public static final String getLastMD5() {
        return lastMD5;
    }

    public final static void downloadTorrentFile(String torrentURL, File saveLocation) throws IOException, URISyntaxException {
        byte[] contents = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).getBytes(torrentURL);

        // save the torrent locally if you have to
        if (saveLocation != null && contents != null && contents.length > 0) {

            if (saveLocation.exists()) {
                saveLocation.delete();
            }

            //Create all the route necessary to save the .torrent file if it does not exit.
            saveLocation.getParentFile().mkdirs();
            saveLocation.createNewFile();
            saveLocation.setWritable(true);

            FileOutputStream fos = new FileOutputStream(saveLocation, false);
            fos.write(contents);
            fos.flush();
            fos.close();
        }
    } //downloadTorrentFile

    public static class InstallerMetaData implements Serializable {

        private static final long serialVersionUID = -2309399378691373445L;

        /**
         * Version coming from the update message
         */
        public String frostwireVersion;
    }
}
