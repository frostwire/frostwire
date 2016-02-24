/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.*;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.logging.Logger;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.frostwire.jlibtorrent.alerts.AlertType.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class BTEngine {

    private static final Logger LOG = Logger.getLogger(BTEngine.class);

    private static final int[] INNER_LISTENER_TYPES = new int[]{TORRENT_ADDED.getSwig(),
            PIECE_FINISHED.getSwig(),
            PORTMAP.getSwig(),
            PORTMAP_ERROR.getSwig(),
            DHT_STATS.getSwig(),
            STORAGE_MOVED.getSwig(),
            LISTEN_SUCCEEDED.getSwig(),
            LISTEN_FAILED.getSwig()
    };

    private static final String TORRENT_ORIG_PATH_KEY = "torrent_orig_path";

    public static BTContext ctx;

    private final ReentrantLock sync;
    private final InnerListener innerListener;

    private final Queue<RestoreDownloadTask> restoreDownloadsQueue;

    private Session session;
    private Downloader downloader;


    private boolean firewalled;
    private BTEngineListener listener;
    private int totalDHTNodes;

    private BTEngine() {
        this.sync = new ReentrantLock();
        this.innerListener = new InnerListener();
        this.restoreDownloadsQueue = new LinkedList<>();
    }

    private static class Loader {
        static final BTEngine INSTANCE = new BTEngine();
    }

    public static BTEngine getInstance() {
        if (ctx == null) {
            throw new IllegalStateException("Context can't be null");
        }
        return Loader.INSTANCE;
    }

    public Session getSession() {
        return session;
    }

    public BTEngineListener getListener() {
        return listener;
    }

    public void setListener(BTEngineListener listener) {
        this.listener = listener;
    }

    public boolean isFirewalled() {
        return firewalled;
    }

    public long getDownloadRate() {
        if (session == null) {
            return 0;
        }
        return session.getStats().downloadRate();
    }

    public long getUploadRate() {
        if (session == null) {
            return 0;
        }
        return session.getStats().uploadRate();
    }

    public long getTotalDownload() {
        if (session == null) {
            return 0;
        }
        return session.getStats().download();
    }

    public long getTotalUpload() {
        if (session == null) {
            return 0;
        }
        return session.getStats().upload();
    }

    public int getDownloadRateLimit() {
        if (session == null) {
            return 0;
        }
        return session.getSettingsPack().downloadRateLimit();
    }

    public int getUploadRateLimit() {
        if (session == null) {
            return 0;
        }
        return session.getSettingsPack().uploadRateLimit();
    }

    public boolean isStarted() {
        return session != null;
    }

    public boolean isPaused() {
        return session != null && session.isPaused();
    }

    public void start() {
        sync.lock();

        try {
            if (session != null) {
                return;
            }

            session = new Session(ctx.interfaces, ctx.retries, false, innerListener);
            downloader = new Downloader(session);

            loadSettings();
            fireStarted();

        } finally {
            sync.unlock();
        }
    }

    /**
     * Abort and destroy the internal libtorrent session.
     */
    public void stop() {
        sync.lock();

        try {
            if (session == null) {
                return;
            }

            session.removeListener(innerListener);
            saveSettings();

            downloader = null;

            session.abort();
            session = null;

            fireStopped();

        } finally {
            sync.unlock();
        }
    }

    public void restart() {
        sync.lock();

        try {

            stop();
            Thread.sleep(1000); // allow some time to release native resources
            start();

        } catch (InterruptedException e) {
            // ignore
        } finally {
            sync.unlock();
        }
    }

    public void pause() {
        if (session != null && !session.isPaused()) {
            session.pause();
        }
    }

    public void resume() {
        if (session != null) {
            session.resume();
        }
    }

    public void updateSavePath(File dataDir) {
        if (session == null) {
            return;
        }

        ctx.dataDir = dataDir; // this will be removed when we start using platform

        try {
            torrent_handle_vector v = session.getSwig().get_torrents();
            long size = v.size();

            String path = dataDir.getAbsolutePath();
            for (int i = 0; i < size; i++) {
                torrent_handle th = v.get(i);
                torrent_status ts = th.status();
                boolean incomplete = !ts.getIs_seeding() && !ts.getIs_finished();
                if (th.is_valid() && incomplete) {
                    th.move_storage(path);
                }
            }
        } catch (Throwable e) {
            LOG.error("Error changing save path for session", e);
        }
    }

    public void loadSettings() {
        if (session == null) {
            return;
        }

        try {
            File f = settingsFile();
            if (f.exists()) {
                byte[] data = FileUtils.readFileToByteArray(f);
                session.loadState(data);
            } else {
                revertToDefaultConfiguration();
            }
        } catch (Throwable e) {
            LOG.error("Error loading session state", e);
        }
    }

    public void saveSettings() {
        if (session == null) {
            return;
        }

        try {
            byte[] data = session.saveState();
            FileUtils.writeByteArrayToFile(settingsFile(), data);
        } catch (Throwable e) {
            LOG.error("Error saving session state", e);
        }
    }

    private void saveSettings(SettingsPack sp) {
        if (session == null) {
            return;
        }
        session.applySettings(sp);
        saveSettings();
    }

    public void revertToDefaultConfiguration() {
        if (session == null) {
            return;
        }

        SettingsPack sp = session.getSettingsPack();

        sp.broadcastLSD(true);

        if (ctx.optimizeMemory) {
            int maxQueuedDiskBytes = sp.maxQueuedDiskBytes();
            sp.setMaxQueuedDiskBytes(maxQueuedDiskBytes / 2);
            int sendBufferWatermark = sp.sendBufferWatermark();
            sp.setSendBufferWatermark(sendBufferWatermark / 2);
            sp.setCacheSize(256);
            sp.setActiveDownloads(4);
            sp.setActiveSeeds(4);
            sp.setMaxPeerlistSize(200);
            sp.setGuidedReadCache(true);
            sp.setTickInterval(1000);
            sp.setInactivityTimeout(60);
            sp.setSeedingOutgoingConnections(false);
            sp.setConnectionsLimit(200);
        } else {
            sp.setActiveDownloads(10);
            sp.setActiveSeeds(10);
        }

        session.applySettings(sp);
        saveSettings();
    }

    public void download(File torrent, File saveDir) {
        download(torrent, saveDir, null);
    }

    public void download(File torrent, File saveDir, boolean[] selection) {
        if (session == null) {
            return;
        }

        saveDir = setupSaveDir(saveDir);
        if (saveDir == null) {
            return;
        }

        TorrentInfo ti = new TorrentInfo(torrent);

        Priority[] priorities = null;

        TorrentHandle th = downloader.find(ti.getInfoHash());
        boolean exists = th != null;

        if (selection != null) {
            if (th != null) {
                priorities = th.getFilePriorities();
            } else {
                priorities = Priority.array(Priority.IGNORE, ti.getNumFiles());
            }

            for (int i = 0; i < selection.length; i++) {
                if (selection[i]) {
                    priorities[i] = Priority.NORMAL;
                }
            }
        }

        download(ti, saveDir, priorities, null);

        if (!exists) {
            saveResumeTorrent(torrent);
        }
    }

    public void download(TorrentInfo ti, File saveDir, boolean[] selection) {
        if (session == null) {
            return;
        }

        saveDir = setupSaveDir(saveDir);
        if (saveDir == null) {
            return;
        }

        Priority[] priorities = null;

        TorrentHandle th = downloader.find(ti.getInfoHash());
        boolean torrentHandleExists = th != null;

        if (selection != null) {
            if (torrentHandleExists) {
                priorities = th.getFilePriorities();
            } else {
                priorities = Priority.array(Priority.IGNORE, ti.getNumFiles());
            }

            for (int i = 0; i < selection.length; i++) {
                if (selection[i]) {
                    priorities[i] = Priority.NORMAL;
                }
            }
        }

        download(ti, saveDir, priorities, null);

        if (!torrentHandleExists) {
            File torrent = saveTorrent(ti);
            saveResumeTorrent(torrent);
        }
    }

    public void download(TorrentCrawledSearchResult sr, File saveDir) {
        if (session == null) {
            return;
        }

        saveDir = setupSaveDir(saveDir);
        if (saveDir == null) {
            return;
        }

        TorrentInfo ti = sr.getTorrentInfo();
        int fileIndex = sr.getFileIndex();

        TorrentHandle th = downloader.find(ti.getInfoHash());
        boolean exists = th != null;

        if (th != null) {
            Priority[] priorities = th.getFilePriorities();
            if (priorities[fileIndex] == Priority.IGNORE) {
                priorities[fileIndex] = Priority.NORMAL;
                download(ti, saveDir, priorities, null);
            }
        } else {
            Priority[] priorities = Priority.array(Priority.IGNORE, ti.getNumFiles());
            priorities[fileIndex] = Priority.NORMAL;
            download(ti, saveDir, priorities, null);
        }

        if (!exists) {
            File torrent = saveTorrent(ti);
            saveResumeTorrent(torrent);
        }
    }

    /**
     * @param uri
     * @param timeout in seconds
     * @return
     */
    public byte[] fetchMagnet(String uri, int timeout) {
        if (session == null) {
            return null;
        }

        return downloader.fetchMagnet(uri, timeout);
    }

    public void restoreDownloads() {
        if (session == null) {
            return;
        }

        if (ctx.homeDir == null || !ctx.homeDir.exists()) {
            LOG.warn("Wrong setup with BTEngine home dir");
            return;
        }

        File[] torrents = ctx.homeDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name != null && FilenameUtils.getExtension(name).equals("torrent");
            }
        });

        if (torrents != null) {
            for (File t : torrents) {
                try {
                    String infoHash = FilenameUtils.getBaseName(t.getName());
                    if (infoHash != null) {
                        File resumeFile = resumeDataFile(infoHash);

                        File savePath = readSavePath(infoHash);
                        if (setupSaveDir(savePath) == null) {
                            LOG.warn("Can't create data dir or mount point is not accessible");
                            return;
                        }

                        restoreDownloadsQueue.add(new RestoreDownloadTask(t, null, null, resumeFile));
                    }
                } catch (Throwable e) {
                    LOG.error("Error restoring torrent download: " + t, e);
                }
            }
        }

        migrateVuzeDownloads();

        runNextRestoreDownloadTask();
    }

    File settingsFile() {
        return new File(ctx.homeDir, "settings.dat");
    }

    File resumeTorrentFile(String infoHash) {
        return new File(ctx.homeDir, infoHash + ".torrent");
    }

    File resumeDataFile(String infoHash) {
        return new File(ctx.homeDir, infoHash + ".resume");
    }

    File readTorrentPath(String infoHash) {
        File torrent = null;

        try {
            byte[] arr = FileUtils.readFileToByteArray(resumeTorrentFile(infoHash));
            entry e = entry.bdecode(Vectors.bytes2byte_vector(arr));
            torrent = new File(e.dict().get(TORRENT_ORIG_PATH_KEY).string());
        } catch (Throwable e) {
            // can't recover original torrent path
        }

        return torrent;
    }

    File readSavePath(String infoHash) {
        File savePath = null;

        try {
            byte[] arr = FileUtils.readFileToByteArray(resumeDataFile(infoHash));
            entry e = entry.bdecode(Vectors.bytes2byte_vector(arr));
            savePath = new File(e.dict().get("save_path").string());
        } catch (Throwable e) {
            // can't recover original torrent path
        }

        return savePath;
    }

    private File saveTorrent(TorrentInfo ti) {
        File torrentFile;

        try {
            String name = ti.getName();
            if (name == null || name.length() == 0) {
                name = ti.getInfoHash().toString();
            }
            name = escapeFilename(name);

            torrentFile = new File(ctx.torrentsDir, name + ".torrent");
            byte[] arr = ti.toEntry().bencode();

            FileSystem fs = Platforms.get().fileSystem();
            fs.write(torrentFile, arr);
            fs.scan(torrentFile);
        } catch (Throwable e) {
            torrentFile = null;
            LOG.warn("Error saving torrent info to file", e);
        }

        return torrentFile;
    }

    private void saveResumeTorrent(File torrent) {
        try {
            TorrentInfo ti = new TorrentInfo(torrent);
            entry e = ti.toEntry().getSwig();
            e.dict().set(TORRENT_ORIG_PATH_KEY, new entry(torrent.getAbsolutePath()));
            byte[] arr = Vectors.byte_vector2bytes(e.bencode());
            FileUtils.writeByteArrayToFile(resumeTorrentFile(ti.getInfoHash().toString()), arr);
        } catch (Throwable e) {
            LOG.warn("Error saving resume torrent", e);
        }
    }

    private void doResumeData(TorrentAlert<?> alert, boolean force) {
        try {
            if (!force) {
                // TODO: I need to restore this later
                if (ctx.optimizeMemory) {
                    return;
                }
            }
            TorrentHandle th = session.findTorrent(alert.getHandle().getInfoHash());
            if (th != null && th.isValid()) {
                th.saveResumeData();
            }
        } catch (Throwable e) {
            LOG.warn("Error triggering resume data", e);
        }
    }

    private void fireStarted() {
        if (listener != null) {
            listener.started(this);
        }
    }

    private void fireStopped() {
        if (listener != null) {
            listener.stopped(this);
        }
    }

    private void fireDownloadAdded(TorrentAlert<?> alert) {
        try {
            TorrentHandle th = session.findTorrent(alert.getHandle().getInfoHash());
            BTDownload dl = new BTDownload(this, th);
            if (listener != null) {
                listener.downloadAdded(this, dl);
            }
        } catch (Throwable e) {
            LOG.error("Unable to create and/or notify the new download", e);
        }
    }

    private void fireDownloadUpdate(TorrentHandle th) {
        try {
            BTDownload dl = new BTDownload(this, th);
            if (listener != null) {
                listener.downloadUpdate(this, dl);
            }
        } catch (Throwable e) {
            LOG.error("Unable to notify update the a download", e);
        }
    }

    private void logListenSucceeded(ListenSucceededAlert alert) {
        TcpEndpoint endp = alert.getEndpoint();
        String addr = endp.address().swig().to_string();
        String s = "endpoint: " + addr + ":" + endp.port() + " type:" + alert.getSwig().getSock_type();
        LOG.info("Listen succeeded on " + s);
    }

    private void logListenFailed(ListenFailedAlert alert) {
        String s = "endpoint: " + alert.listenInterface() + " type:" + alert.getSwig().getSock_type();
        LOG.info("Listen failed on " + s);
    }

    private void migrateVuzeDownloads() {
        try {
            File dir = new File(ctx.homeDir.getParent(), "azureus");
            File file = new File(dir, "downloads.config");

            if (file.exists()) {
                Entry configEntry = Entry.bdecode(file);
                List<Entry> downloads = configEntry.dictionary().get("downloads").list();

                for (Entry d : downloads) {
                    try {
                        Map<String, Entry> map = d.dictionary();
                        File saveDir = new File(map.get("save_dir").string());
                        File torrent = new File(map.get("torrent").string());
                        ArrayList<Entry> filePriorities = map.get("file_priorities").list();

                        Priority[] priorities = Priority.array(Priority.IGNORE, filePriorities.size());
                        for (int i = 0; i < filePriorities.size(); i++) {
                            long p = filePriorities.get(i).integer();
                            if (p != 0) {
                                priorities[i] = Priority.NORMAL;
                            }
                        }

                        if (torrent.exists() && saveDir.exists()) {
                            LOG.info("Restored old vuze download: " + torrent);
                            restoreDownloadsQueue.add(new RestoreDownloadTask(torrent, saveDir, priorities, null));
                            saveResumeTorrent(torrent);
                        }
                    } catch (Throwable e) {
                        LOG.error("Error restoring vuze torrent download", e);
                    }
                }

                file.delete();
            }
        } catch (Throwable e) {
            LOG.error("Error migrating old vuze downloads", e);
        }
    }

    private File setupSaveDir(File saveDir) {
        File result = null;

        if (saveDir == null) {
            if (ctx.dataDir != null) {
                result = ctx.dataDir;
            } else {
                LOG.warn("Unable to setup save dir path, review your logic, both saveDir and ctx.dataDir are null.");
            }
        } else {
            result = saveDir;
        }

        FileSystem fs = Platforms.get().fileSystem();

        if (result != null && !fs.isDirectory(result) && !fs.mkdirs(result)) {
            result = null;
            LOG.warn("Failed to create save dir to download");
        }

        if (result != null && !fs.canWrite(result)) {
            result = null;
            LOG.warn("Failed to setup save dir with write access");
        }

        return result;
    }

    private void runNextRestoreDownloadTask() {
        final RestoreDownloadTask task;
        try {
            task = restoreDownloadsQueue.poll();
        } catch (Throwable t) {
            // on Android, LinkedList's .poll() implementation throws a NoSuchElementException
            return;
        }
        if (task != null) {
            task.run();
        }
    }

    public void download(TorrentInfo ti, File saveDir, Priority[] priorities, File resumeFile) {

        TorrentHandle th = session.findTorrent(ti.getInfoHash());

        if (th != null) {
            // found a download with the same hash, just adjust the priorities if needed
            if (priorities != null) {
                if (ti.getNumFiles() != priorities.length) {
                    throw new IllegalArgumentException("The priorities length should be equals to the number of files");
                }

                th.prioritizeFiles(priorities);
                fireDownloadUpdate(th);
                th.resume();
            } else {
                // did they just add the entire torrent (therefore not selecting any priorities)
                final Priority[] wholeTorrentPriorities = Priority.array(Priority.NORMAL, ti.getNumFiles());
                th.prioritizeFiles(wholeTorrentPriorities);
                fireDownloadUpdate(th);
                th.resume();
            }
        } else { // new download
            session.asyncAddTorrent(ti, saveDir, priorities, resumeFile);
        }
    }

    // this is here until we have a properly done OS utils.
    private static String escapeFilename(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\[\\]]+", "_");
    }

    // NOTE: don't delete, new API
    /*
    private static SettingsPack settingsToPack(SessionSettings s) {
        string_entry_map map = new string_entry_map();
        libtorrent.save_settings_to_dict(s.getSwig(), map);
        entry e = new entry(map);
        bdecode_node le = new bdecode_node();
        error_code ec = new error_code();
        bdecode_node.bdecode(e.bencode(), le, ec);
        if (ec.value() != 0) {
            throw new IllegalStateException("Can't create settings pack");
        }

        return new SettingsPack(libtorrent.load_pack_from_dict(le));
    }*/

    /*
    private void updateAverageSpeeds() {
        long now = System.currentTimeMillis();

        bytesRecv = session.getStats().getPayloadDownload();
        bytesSent = session.getStats().getPayloadUpload();

        if (now - speedMarkTimestamp > SPEED_AVERAGE_CALCULATION_INTERVAL_MILLISECONDS) {
            averageRecvSpeed = ((bytesRecv - totalRecvSinceLastSpeedStamp) * 1000) / (now - speedMarkTimestamp);
            averageSentSpeed = ((bytesSent - totalSentSinceLastSpeedStamp) * 1000) / (now - speedMarkTimestamp);
            speedMarkTimestamp = now;
            totalRecvSinceLastSpeedStamp = bytesRecv;
            totalSentSinceLastSpeedStamp = bytesSent;
        }
    }*/

    private final class InnerListener implements AlertListener {
        @Override
        public int[] types() {
            return INNER_LISTENER_TYPES;
        }

        @Override
        public void alert(Alert<?> alert) {

            AlertType type = alert.getType();

            switch (type) {
                case TORRENT_ADDED:
                    TorrentAlert<?> torrentAlert = (TorrentAlert<?>) alert;
                    fireDownloadAdded(torrentAlert);
                    doResumeData(torrentAlert, false);
                    runNextRestoreDownloadTask();
                    break;
                case PIECE_FINISHED:
                    doResumeData((TorrentAlert<?>) alert, false);
                    break;
                case PORTMAP:
                    firewalled = false;
                    break;
                case PORTMAP_ERROR:
                    firewalled = true;
                    break;
                case DHT_STATS:
                    totalDHTNodes = ((DhtStatsAlert) alert).totalNodes();
                    break;
                case STORAGE_MOVED:
                    doResumeData((TorrentAlert<?>) alert, true);
                    break;
                case LISTEN_SUCCEEDED:
                    //logListenSucceeded((ListenSucceededAlert) alert);
                    break;
                case LISTEN_FAILED:
                    //logListenFailed((ListenFailedAlert) alert);
                    break;
            }
        }
    }

    private final class RestoreDownloadTask implements Runnable {

        private final File torrent;
        private final File saveDir;
        private final Priority[] priorities;
        private final File resume;

        public RestoreDownloadTask(File torrent, File saveDir, Priority[] priorities, File resume) {
            this.torrent = torrent;
            this.saveDir = saveDir;
            this.priorities = priorities;
            this.resume = resume;
        }

        @Override
        public void run() {
            try {
                session.asyncAddTorrent(new TorrentInfo(torrent), saveDir, priorities, resume);
            } catch (Throwable e) {
                LOG.error("Unable to restore download from previous session", e);
            }
        }
    }

    //--------------------------------------------------
    // Settings methods
    //--------------------------------------------------

    public int getDownloadSpeedLimit() {
        if (session == null) {
            return 0;
        }
        return session.getSettingsPack().downloadRateLimit();
    }

    public void setDownloadSpeedLimit(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack settingsPack = session.getSettingsPack();
        settingsPack.setDownloadRateLimit(limit);
        saveSettings(settingsPack);
    }

    public int getUploadSpeedLimit() {
        if (session == null) {
            return 0;
        }
        return session.getSettingsPack().uploadRateLimit();
    }

    public void setUploadSpeedLimit(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack settingsPack = session.getSettingsPack();
        settingsPack.setUploadRateLimit(limit);
        session.applySettings(settingsPack);
        saveSettings(settingsPack);
    }

    public int getMaxActiveDownloads() {
        if (session == null) {
            return 0;
        }
        return session.getSettingsPack().activeDownloads();
    }

    public void setMaxActiveDownloads(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack settingsPack = session.getSettingsPack();
        settingsPack.setActiveDownloads(limit);
        session.applySettings(settingsPack);
        saveSettings(settingsPack);
    }

    public int getMaxActiveSeeds() {
        if (session == null) {
            return 0;
        }
        return session.getSettingsPack().activeSeeds();
    }

    public void setMaxActiveSeeds(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack settingsPack = session.getSettingsPack();
        settingsPack.setActiveSeeds(limit);
        session.applySettings(settingsPack);
        saveSettings(settingsPack);
    }

    public int getMaxConnections() {
        if (session == null) {
            return 0;
        }
        return session.getSettingsPack().connectionsLimit();
    }

    public void setMaxConnections(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack settingsPack = session.getSettingsPack();
        settingsPack.setConnectionsLimit(limit);
        session.applySettings(settingsPack);
        saveSettings(settingsPack);
    }

    public int getMaxPeers() {
        if (session == null) {
            return 0;
        }
        return session.getSettingsPack().maxPeerlistSize();
    }

    public void setMaxPeers(int limit) {
        if (session == null) {
            return;
        }
        SettingsPack settingsPack = session.getSettingsPack();
        settingsPack.setMaxPeerlistSize(limit);
        session.applySettings(settingsPack);
        saveSettings(settingsPack);
    }

    public String getListenInterfaces() {
        if (session == null) {
            return null;
        }
        return session.getSettingsPack().getString(settings_pack.string_types.listen_interfaces.swigValue());
    }

    public void setListenInterfaces(String value) {
        if (session == null) {
            return;
        }
        SettingsPack sp = new SettingsPack();
        sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), value);
        saveSettings(sp);
    }

    public int getTotalDHTNodes() {
        return totalDHTNodes;
    }
}
