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

package com.frostwire.gui.updates;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.gui.DigestUtils;
import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.HttpClient.HttpRangeException;
import com.limegroup.gnutella.gui.DialogOption;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.settings.UpdateSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

import javax.swing.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.frostwire.jlibtorrent.alerts.AlertType.*;

/**
 * @author gubatron
 * @author aldenml
 */
class InstallerUpdater implements Runnable {
    private static final Logger LOG = Logger.getLogger(InstallerUpdater.class);
    private static String lastMD5;
    private static boolean isDownloadingUpdate = false;
    private static int downloadProgress = 0;
    private final UpdateMessage updateMessage;
    private final boolean forceUpdate;
    private TorrentHandle torrentHandle = null;
    private File executableFile;

    InstallerUpdater(UpdateMessage um, boolean force) {
        updateMessage = um;
        forceUpdate = force;
        isDownloadingUpdate = false;
    }

    static int getUpdateDownloadProgress() {
        return downloadProgress;
    }

    static boolean isDownloadingUpdate() {
        return isDownloadingUpdate;
    }

    private static void printTorrentHandleStatus(TorrentHandle th) {
        if (th == null) {
            return;
        }
        StringBuilder buf = new StringBuilder();
        buf.append("Infohash: ");
        buf.append(th.infoHash().toHex());
        buf.append(" Completed:");
        TorrentStatus stats = th.status();
        buf.append(stats.progress());
        buf.append('%');
        buf.append(" Seeds:");
        buf.append(stats.numSeeds());
        buf.append(" Peers:");
        buf.append(stats.numPeers());
        buf.append(" Downloaded:");
        buf.append(stats.totalDone());
        buf.append(" Uploaded:");
        buf.append(stats.totalUpload());
        buf.append(" DSpeed:");
        buf.append(stats.downloadRate());
        buf.append(" USpeed:");
        buf.append(stats.uploadRate());
        while (buf.length() < 80) {
            buf.append(' ');
        }
        buf.append(" TO:");
        buf.append(th.savePath());
        System.out.println(buf.toString());
    }

    static String getLastMD5() {
        return lastMD5;
    }

    private static void downloadTorrentFile(String torrentURL, File saveLocation) throws IOException {
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

    public void start() {
        new Thread(this, "InstallerUpdater").start();
    }

    public void run() {
        if (!forceUpdate && !UpdateSettings.AUTOMATIC_INSTALLER_DOWNLOAD.getValue()) {
            return;
        }
        cleanupInvalidUpdates();
        if (checkIfDownloaded()) {
            showUpdateMessage();
        } else {
            isDownloadingUpdate = true;
            if (updateMessage.getTorrent() != null) {
                handleTorrentDownload();
            } else if (updateMessage.getInstallerUrl() != null) {
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
            if (exists) {
                startTorrentDownload(torrentFileLocation.getAbsolutePath(), UpdateSettings.UPDATES_DIR.getAbsolutePath());
            } else {
                isDownloadingUpdate = false;
            }
        } catch (Throwable e) {
            isDownloadingUpdate = false;
            LOG.error("Error starting update torrent download", e);
        }
    }

    private String getFileNameFromHttpUrl() {
        int index = updateMessage.getInstallerUrl().lastIndexOf('/');
        return updateMessage.getInstallerUrl().substring(index + 1);
    }

    private void handleHttpDownload() {
        File updateFolder = UpdateSettings.UPDATES_DIR;
        String fileName = updateMessage.getSaveAs() != null ?
                updateMessage.getSaveAs() : getFileNameFromHttpUrl();
        File installerFileLocation = new File(updateFolder, fileName);
        if (!updateFolder.exists()) {
            updateFolder.mkdir();
            updateFolder.setWritable(true);
        }
        try {
            HttpClient httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC);
            httpClient.setListener(new HttpClient.HttpClientListener() {
                long contentLength;
                long downloaded = 0;

                @Override
                public void onError(HttpClient client, Throwable e) {
                    e.printStackTrace();
                }

                @Override
                public void onData(HttpClient client, byte[] buffer, int offset, int length) {
                    downloaded += length;
                    downloadProgress = (int) ((float) downloaded / contentLength * 100.0);
                }

                @Override
                public void onComplete(HttpClient client) {
                }

                @Override
                public void onCancel(HttpClient client) {
                }

                @Override
                public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {
                    if (headerFields.containsKey("Content-Length")) {
                        contentLength = Long.parseLong(headerFields.get("Content-Length").get(0));
                    }
                }
            });
            try {
                httpClient.save(updateMessage.getInstallerUrl(), installerFileLocation, true);
            } catch (HttpRangeException e) {
                // recovery in case the server does not support resume
                httpClient.save(updateMessage.getInstallerUrl(), installerFileLocation, false);
            } catch (IOException e2) {
                e2.printStackTrace();
                if (e2.getMessage().contains("416")) {
                    // HTTP Request Range error came through IOException.
                    httpClient.save(updateMessage.getInstallerUrl(), installerFileLocation, false);
                }
            }
            isDownloadingUpdate = false;
            downloadComplete();
        } catch (Throwable e) {
            isDownloadingUpdate = false;
            LOG.error("Failed to download installer: " + updateMessage.getInstallerUrl(), e);
        }
    }

    private void startTorrentDownload(String torrentFile, String saveDataPath) {
        TorrentInfo tinfo = new TorrentInfo(new File(torrentFile));
        final Sha1Hash updateInfoHash = tinfo.infoHash();
        AlertListener updateTorrentListener = new AlertListener() {
            TorrentHandle th = null;

            @Override
            public int[] types() {
                return new int[]{
                        TORRENT_RESUMED.swig(),
                        ADD_TORRENT.swig(),
                        PIECE_FINISHED.swig(),
                        TORRENT_FINISHED.swig(),
                };
            }

            @Override
            public void alert(Alert<?> alert) {
                try {
                    if (checkIfDownloaded()) {
                        BTEngine.getInstance().removeListener(this);
                        showUpdateMessage();
                    }
                    if (!(alert instanceof TorrentAlert<?>)) {
                        //check manually for the md5 every time as jlibtorrent is failing to report the finished download.
                        return;
                    }
                    Sha1Hash alertSha1Hash = ((TorrentAlert) alert).handle().infoHash();
                    if (!alertSha1Hash.toHex().equals(updateInfoHash.toHex())) {
                        return;
                    }
                    if (alert.type().equals(ADD_TORRENT) && alertSha1Hash.toHex().equals(updateInfoHash.toHex())) {
                        Sha1Hash sha1 = ((TorrentAlert<?>) alert).handle().infoHash();
                        th = BTEngine.getInstance().find(sha1);
                        torrentHandle = th;
                        th.resume();
                        // Sleep for a bit, sometimes the added torrent is from the last session and it is finished but this is so
                        // early that we only see progress of 100% when we debug and step thru.
                        Thread.sleep(2000);
                        // it can happen that the file is finished at this moment and no update message has been shown before.
                        int progress = (int) (torrentHandle.status().progress() * 100);
                        if (progress == 100) {
                            isDownloadingUpdate = false;
                            BTEngine.getInstance().removeListener(this);
                            downloadComplete();
                            return;
                        }
                    }
                    if (th == null) {
                        return;
                    }
                    AlertType type = alert.type();
                    System.out.println("InstallerUpdater.AlertListener: " + type);
                    printTorrentHandleStatus(th);
                    switch (type) {
                        case TORRENT_RESUMED:
                            printTorrentHandleStatus(th);
                        case FILE_ERROR:
                        case PIECE_FINISHED:
                            printTorrentHandleStatus(th);
                            onStateChanged(th, th.status().state());
                            int progress = (int) (torrentHandle.status().progress() * 100);
                            if (progress == 100) {
                                isDownloadingUpdate = false;
                                BTEngine.getInstance().removeListener(this);
                                downloadComplete();
                                return;
                            }
                            break;
                        case TORRENT_FINISHED:
                            BTEngine.getInstance().removeListener(this);
                            onStateChanged(th, th.status().state());
                            downloadComplete();
                            break;
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        };
        try {
            BTEngine.getInstance().addListener(updateTorrentListener);
            BTEngine.getInstance().download(tinfo, new File(saveDataPath), null, null, null);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void showUpdateMessage() {
        GUIMediator.safeInvokeLater(() -> {
            if (executableFile == null) {
                return;
            }
            String buildsMissedMessage = getBuildsMissedMessage();
            DialogOption result = GUIMediator.showYesNoMessage(buildsMissedMessage + updateMessage.getMessageInstallerReady(), I18n.tr("Update"), JOptionPane.INFORMATION_MESSAGE);
            if (result == DialogOption.YES) {
                UpdateMediator.openInstallerAndShutdown(executableFile);
            }
        });
    }

    private String getBuildsMissedMessage() {
        if (updateMessage.getBuild() != null) {
            try {
                int newBuildNumber = Integer.parseInt(updateMessage.getBuild());
                int buildDelta = newBuildNumber - FrostWireUtils.getBuildNumber();
                if (buildDelta > 1) {
                    return "<html>" + I18n.tr("Time flies! You have missed the last {0} updates.", buildDelta) + "<br><br>&nbsp;";
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
        return "";
    }

    private File downloadDotTorrent() {
        File appSpecialShareFolder = UpdateSettings.UPDATES_DIR;
        int index = updateMessage.getTorrent().lastIndexOf('/');
        File torrentFileLocation = new File(appSpecialShareFolder, updateMessage.getTorrent().substring(index + 1));
        if (!appSpecialShareFolder.exists() && appSpecialShareFolder.mkdir()) {
            appSpecialShareFolder.setWritable(true);
        }
        //We always re-download the torrent just in case.
        try {
            downloadTorrentFile(updateMessage.getTorrent(), torrentFileLocation);
        } catch (Throwable e) {
            LOG.error("Error downloading update torrent file", e);
        }
        return torrentFileLocation;
    }

    private void onStateChanged(TorrentHandle th, TorrentStatus.State state) {
        if (th == null) {
            return;
        }
        torrentHandle = th;
        printTorrentHandleStatus(th);
        if (torrentDataDownloadedToDisk()) {
            isDownloadingUpdate = false;
            return;
        }
        System.out.println("InstallerUpdater.stateChanged() - " + state + " completed: " + torrentHandle.status().isFinished());
        if (state == TorrentStatus.State.SEEDING) {
            isDownloadingUpdate = false;
            System.out.println("InstallerUpdater.stateChanged() - SEEDING!");
            return;
        }
        ErrorCode error = null;
        TorrentStatus status = th.status();
        if (status != null) {
            error = status.errorCode();
        }
        if (error != null && error.value() != 0) {
            isDownloadingUpdate = false;
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
            System.out.println(error);
            System.out.println("InstallerUpdater: ERROR - stopIt, startDownload!");
            System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
            //try to restart the download. delete torrent and data
            //manager.stopIt(DownloadManager.STATE_READY, false, true);
            try {
                BTEngine.getInstance().remove(th, SessionHandle.DELETE_FILES);
                //processMessage(updateMessage);
            } catch (Throwable e) {
                LOG.error("Error removing download manager on error", e);
            }
        } else if (state == TorrentStatus.State.DOWNLOADING) {
            System.out.println("stateChanged(STATE_DOWNLOADING)");
            downloadProgress = (int) (torrentHandle.status().progress() * 100);
        }
    }

    private void downloadComplete() {
        System.out.println("InstallerUpdater.downloadComplete()!!!!");
        printTorrentHandleStatus(torrentHandle);
        cleanupInvalidUpdates();
        if (checkIfDownloaded()) {
            showUpdateMessage();
        }
    }

    private void cleanupInvalidUpdates() {
        if (!UpdateSettings.UPDATES_DIR.exists() ||
                !UpdateSettings.UPDATES_DIR.isDirectory()) {
            return;
        }
        File[] files = UpdateSettings.UPDATES_DIR.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        String currentMD5 = updateMessage.getRemoteMD5();
        for (File file : files) {
            try {
                String fileMD5 = DigestUtils.getMD5(file);
                if (!DigestUtils.compareMD5(currentMD5, fileMD5)) {
                    System.out.println("InstallerUpdater.cleanupInvalidUpdates() -> removed " + file.getName() + " (file size: " + file.length() + " bytes)");
                    if (updateMessage.getInstallerUrl() != null && updateMessage.getInstallerUrl() != "") {
                        System.out.println("InstallerUpdater.cleanupInvalidUpdates() -> downloaded from " + updateMessage.getInstallerUrl());
                    }
                    System.out.println("InstallerUpdater.cleanupInvalidUpdates() -> expected MD5=" + currentMD5.toLowerCase() + " vs " + file.getName() + " MD5=" + fileMD5 + "\n");
                    file.delete();
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private boolean checkIfDownloaded() {
        String installerFilename = UpdateMediator.getInstallerFilename(updateMessage);
        if (installerFilename == null) {
            return false;
        }
        File f = new File(UpdateSettings.UPDATES_DIR, installerFilename);
        if (!f.exists()) {
            System.out.println("InstallerUpdater.checkIfDownloaded() - File <" + installerFilename + "> does not exist");
            return false;
        }
        executableFile = f;
        try {
            lastMD5 = DigestUtils.getMD5(f);
            boolean result = DigestUtils.compareMD5(lastMD5, updateMessage.getRemoteMD5());
            if (!result) {
                System.out.println("InstallerUpdater.checkIfDownloaded() - MD5 check failed. expected MD5=" + updateMessage.getRemoteMD5().toLowerCase() + " vs " + f.getName() + " MD5=" + lastMD5.toLowerCase() + " (file size: " + f.length() + " bytes)");
            }
            return result;
        } catch (Throwable e) {
            LOG.error("Error checking update MD5", e);
            return false;
        }
    }

    private boolean torrentDataDownloadedToDisk() {
        return torrentHandle != null && torrentHandle.isValid() && (torrentHandle.status().isFinished() || torrentHandle.status().isSeeding());
    }
}
