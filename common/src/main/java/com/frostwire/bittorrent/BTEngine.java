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

import com.frostwire.bittorrent.jlibtorrent.SessionManager;
import com.frostwire.bittorrent.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.*;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.util.Logger;
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
public final class BTEngine extends SessionManager {

    private static final Logger LOGGER = Logger.getLogger(BTEngine.class);

    private static final int[] INNER_LISTENER_TYPES = new int[]{TORRENT_ADDED.swig(),
            PIECE_FINISHED.swig(),
            PORTMAP.swig(),
            PORTMAP_ERROR.swig(),
            STORAGE_MOVED.swig(),
            LISTEN_SUCCEEDED.swig(),
            LISTEN_FAILED.swig(),
            EXTERNAL_IP.swig(),
            METADATA_RECEIVED.swig()
    };

    private static final String TORRENT_ORIG_PATH_KEY = "torrent_orig_path";
    public static BTContext ctx;

    private final ReentrantLock sync;
    private final InnerListener innerListener;
    private final Queue<RestoreDownloadTask> restoreDownloadsQueue;

    private Session session;
    private BTEngineListener listener;

    private static final LruCache<String, byte[]> MAGNET_CACHE = new LruCache<String, byte[]>(50);
    private static final Object MAGNET_LOCK = new Object();

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

    public Session session() {
        return session;
    }

    public BTEngineListener getListener() {
        return listener;
    }

    public void setListener(BTEngineListener listener) {
        this.listener = listener;
    }

    public long downloadRate() {
        if (session == null) {
            return 0;
        }
        return session.getStats().downloadRate();
    }

    public long uploadRate() {
        if (session == null) {
            return 0;
        }
        return session.getStats().uploadRate();
    }

    public long totalDownload() {
        if (session == null) {
            return 0;
        }
        return session.getStats().download();
    }

    public long totalUpload() {
        if (session == null) {
            return 0;
        }
        return session.getStats().upload();
    }

    public void start() {
        sync.lock();

        try {
            if (session != null) {
                return;
            }

            firewalled = true;
            listenEndpoints.clear();
            externalAddress = null;

            session = new Session(ctx.interfaces, ctx.retries, false, innerListener);
            super.session = (session) this.session.swig();
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

            super.session = null;
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

    @Override
    public void moveStorage(File dataDir) {
        if (session == null) {
            return;
        }

        ctx.dataDir = dataDir; // this will be removed when we start using platform

        super.moveStorage(dataDir);
    }

    private void loadSettings() {
        if (session == null) {
            return;
        }

        try {
            File f = settingsFile();
            if (f.exists()) {
                byte[] data = FileUtils.readFileToByteArray(f);
                loadState(data);
            } else {
                revertToDefaultConfiguration();
            }
        } catch (Throwable e) {
            LOGGER.error("Error loading session state", e);
        }
    }

    @Override
    protected void onApplySettings(SettingsPack sp) {
        saveSettings();
    }

    private void saveSettings() {
        if (session == null) {
            return;
        }

        try {
            byte[] data = saveState();
            FileUtils.writeByteArrayToFile(settingsFile(), data);
        } catch (Throwable e) {
            LOGGER.error("Error saving session state", e);
        }
    }

    public void revertToDefaultConfiguration() {
        if (session == null) {
            return;
        }

        SettingsPack sp = settings();

        sp.broadcastLSD(true);

        if (ctx.optimizeMemory) {
            int maxQueuedDiskBytes = sp.maxQueuedDiskBytes();
            sp.setMaxQueuedDiskBytes(maxQueuedDiskBytes / 2);
            int sendBufferWatermark = sp.sendBufferWatermark();
            sp.setSendBufferWatermark(sendBufferWatermark / 2);
            sp.setCacheSize(256);
            sp.activeDownloads(4);
            sp.activeSeeds(4);
            sp.setMaxPeerlistSize(200);
            sp.setGuidedReadCache(true);
            sp.setTickInterval(1000);
            sp.setInactivityTimeout(60);
            sp.setSeedingOutgoingConnections(false);
            sp.setConnectionsLimit(200);
        } else {
            sp.activeDownloads(10);
            sp.activeSeeds(10);
        }

        applySettings(sp);
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

        TorrentHandle th = find(ti.infoHash());
        boolean exists = th != null;

        if (selection != null) {
            if (th != null) {
                priorities = th.filePriorities();
            } else {
                priorities = Priority.array(Priority.IGNORE, ti.numFiles());
            }

            for (int i = 0; i < selection.length; i++) {
                if (selection[i]) {
                    priorities[i] = Priority.NORMAL;
                }
            }
        }

        download(ti, saveDir, priorities, null, null);

        if (!exists) {
            saveResumeTorrent(torrent);
        }
    }

    public void download(TorrentInfo ti, File saveDir, boolean[] selection, String magnetUrlParams) {
        if (session == null) {
            return;
        }

        saveDir = setupSaveDir(saveDir);
        if (saveDir == null) {
            return;
        }

        Priority[] priorities = null;

        TorrentHandle th = find(ti.infoHash());
        boolean torrentHandleExists = th != null;

        if (selection != null) {
            if (torrentHandleExists) {
                priorities = th.filePriorities();
            } else {
                priorities = Priority.array(Priority.IGNORE, ti.numFiles());
            }

            if (priorities != null) {
                for (int i = 0; i < selection.length; i++) {
                    if (selection[i] && i < priorities.length) {
                        priorities[i] = Priority.NORMAL;
                    }
                }
            }
        }

        download(ti, saveDir, priorities, null, magnetUrlParams);

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

        TorrentHandle th = find(ti.infoHash());
        boolean exists = th != null;

        if (th != null) {
            Priority[] priorities = th.filePriorities();
            if (priorities[fileIndex] == Priority.IGNORE) {
                priorities[fileIndex] = Priority.NORMAL;
                download(ti, saveDir, priorities, null, null);
            }
        } else {
            Priority[] priorities = Priority.array(Priority.IGNORE, ti.numFiles());
            priorities[fileIndex] = Priority.NORMAL;
            download(ti, saveDir, priorities, null, null);
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

        add_torrent_params p = add_torrent_params.create_instance_disabled_storage();
        error_code ec = new error_code();
        libtorrent.parse_magnet_uri(uri, p, ec);
        p.setUrl(uri);

        if (ec.value() != 0) {
            throw new IllegalArgumentException(ec.message());
        }

        final sha1_hash info_hash = p.getInfo_hash();
        String sha1 = info_hash.to_hex();

        byte[] data = MAGNET_CACHE.get(sha1);
        if (data != null) {
            return data;
        }

        boolean add;
        torrent_handle th;

        synchronized (MAGNET_LOCK) {
            th = session.swig().find_torrent(info_hash);
            if (th != null && th.is_valid()) {
                // we have a download with the same info-hash, let's wait
                add = false;
            } else {
                add = true;
            }

            if (add) {
                p.setName("fetch_magnet:" + uri);
                p.setSave_path("fetch_magnet/" + uri);

                long flags = p.get_flags();
                flags &= ~add_torrent_params.flags_t.flag_auto_managed.swigValue();
                p.set_flags(flags);

                ec.clear();
                th = session.swig().add_torrent(p, ec);
                th.resume();
            }
        }

        int n = 0;
        do {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // ignore
            }

            data = MAGNET_CACHE.get(sha1);

            n++;
        } while (n < timeout && data == null);

        synchronized (MAGNET_LOCK) {
            if (add && th != null && th.is_valid()) {
                session.swig().remove_torrent(th);
            }
        }

        return data;
    }

    public void restoreDownloads() {
        if (session == null) {
            return;
        }

        if (ctx.homeDir == null || !ctx.homeDir.exists()) {
            LOGGER.warn("Wrong setup with BTEngine home dir");
            return;
        }

        File[] torrents = ctx.homeDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name != null && FilenameUtils.getExtension(name).toLowerCase().equals("torrent");
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
                            LOGGER.warn("Can't create data dir or mount point is not accessible");
                            return;
                        }

                        restoreDownloadsQueue.add(new RestoreDownloadTask(t, null, null, resumeFile));
                    }
                } catch (Throwable e) {
                    LOGGER.error("Error restoring torrent download: " + t, e);
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
            String name = ti.name();
            if (name == null || name.length() == 0) {
                name = ti.infoHash().toString();
            }
            name = escapeFilename(name);

            torrentFile = new File(ctx.torrentsDir, name + ".torrent");
            byte[] arr = ti.toEntry().bencode();

            FileSystem fs = Platforms.get().fileSystem();
            fs.write(torrentFile, arr);
            fs.scan(torrentFile);
        } catch (Throwable e) {
            torrentFile = null;
            LOGGER.warn("Error saving torrent info to file", e);
        }

        return torrentFile;
    }

    private void saveResumeTorrent(File torrent) {
        try {
            TorrentInfo ti = new TorrentInfo(torrent);
            entry e = ti.toEntry().swig();
            e.dict().set(TORRENT_ORIG_PATH_KEY, new entry(torrent.getAbsolutePath()));
            byte[] arr = Vectors.byte_vector2bytes(e.bencode());
            FileUtils.writeByteArrayToFile(resumeTorrentFile(ti.infoHash().toString()), arr);
        } catch (Throwable e) {
            LOGGER.warn("Error saving resume torrent", e);
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
            TorrentHandle th = find(alert.handle().getInfoHash());
            if (th != null && th.isValid()) {
                th.saveResumeData();
            }
        } catch (Throwable e) {
            LOGGER.warn("Error triggering resume data", e);
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
            TorrentHandle th = find(alert.handle().getInfoHash());
            BTDownload dl = new BTDownload(this, th);
            if (listener != null) {
                listener.downloadAdded(this, dl);
            }
        } catch (Throwable e) {
            LOGGER.error("Unable to create and/or notify the new download", e);
        }
    }

    private void fireDownloadUpdate(TorrentHandle th) {
        try {
            BTDownload dl = new BTDownload(this, th);
            if (listener != null) {
                listener.downloadUpdate(this, dl);
            }
        } catch (Throwable e) {
            LOGGER.error("Unable to notify update the a download", e);
        }
    }

    private void onListenSucceeded(ListenSucceededAlert alert) {
        try {
            TcpEndpoint endp = alert.getEndpoint();
            if (alert.getSocketType() == ListenSucceededAlert.SocketType.TCP) {
                String address = endp.address().toString();
                int port = endp.port();
                listenEndpoints.add(new TcpEndpoint(address, port));
            }

            String s = "endpoint: " + endp + " type:" + alert.getSocketType();
            LOGGER.info("Listen succeeded on " + s);
        } catch (Throwable e) {
            LOGGER.error("Error adding listen endpoint to internal list", e);
        }
    }

    private void onListenFailed(ListenFailedAlert alert) {
        TcpEndpoint endp = alert.endpoint();
        String s = "endpoint: " + endp + " type:" + alert.getSocketType();
        String message = alert.getError().message();
        LOGGER.info("Listen failed on " + s + " (error: " + message + ")");
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
                            LOGGER.info("Restored old vuze download: " + torrent);
                            restoreDownloadsQueue.add(new RestoreDownloadTask(torrent, saveDir, priorities, null));
                            saveResumeTorrent(torrent);
                        }
                    } catch (Throwable e) {
                        LOGGER.error("Error restoring vuze torrent download", e);
                    }
                }

                file.delete();
            }
        } catch (Throwable e) {
            LOGGER.error("Error migrating old vuze downloads", e);
        }
    }

    private File setupSaveDir(File saveDir) {
        File result = null;

        if (saveDir == null) {
            if (ctx.dataDir != null) {
                result = ctx.dataDir;
            } else {
                LOGGER.warn("Unable to setup save dir path, review your logic, both saveDir and ctx.dataDir are null.");
            }
        } else {
            result = saveDir;
        }

        FileSystem fs = Platforms.get().fileSystem();

        if (result != null && !fs.isDirectory(result) && !fs.mkdirs(result)) {
            result = null;
            LOGGER.warn("Failed to create save dir to download");
        }

        if (result != null && !fs.canWrite(result)) {
            result = null;
            LOGGER.warn("Failed to setup save dir with write access");
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

    private void download(TorrentInfo ti, File saveDir, Priority[] priorities, File resumeFile, String magnetUrlParams) {

        TorrentHandle th = find(ti.infoHash());

        if (th != null) {
            // found a download with the same hash, just adjust the priorities if needed
            if (priorities != null) {
                if (ti.numFiles() != priorities.length) {
                    throw new IllegalArgumentException("The priorities length should be equals to the number of files");
                }

                th.prioritizeFiles(priorities);
                fireDownloadUpdate(th);
                th.resume();
            } else {
                // did they just add the entire torrent (therefore not selecting any priorities)
                final Priority[] wholeTorrentPriorities = Priority.array(Priority.NORMAL, ti.numFiles());
                th.prioritizeFiles(wholeTorrentPriorities);
                fireDownloadUpdate(th);
                th.resume();
            }
        } else { // new download
            download(ti, saveDir, resumeFile, priorities, magnetUrlParams);
            //session.asyncAddTorrent(ti, saveDir, priorities, resumeFile);
        }
    }

    // this is here until we have a properly done OS utils.
    private static String escapeFilename(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\[\\]]+", "_");
    }

    private final class InnerListener implements AlertListener {
        @Override
        public int[] types() {
            return INNER_LISTENER_TYPES;
        }

        @Override
        public void alert(Alert<?> alert) {

            AlertType type = alert.type();

            switch (type) {
                case TORRENT_ADDED:
                    TorrentAlert<?> torrentAlert = (TorrentAlert<?>) alert;
                    fireDownloadAdded(torrentAlert);
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
                case STORAGE_MOVED:
                    doResumeData((TorrentAlert<?>) alert, true);
                    break;
                case LISTEN_SUCCEEDED:
                    onListenSucceeded((ListenSucceededAlert) alert);
                    break;
                case LISTEN_FAILED:
                    onListenFailed((ListenFailedAlert) alert);
                    break;
                case EXTERNAL_IP:
                    onExternalIpAlert((ExternalIpAlert) alert);
                    break;
                case METADATA_RECEIVED:
                    saveMagnetData((MetadataReceivedAlert) alert);
                    break;
            }
        }
    }

    private void onExternalIpAlert(ExternalIpAlert alert) {
        try {
            // libtorrent perform all kind of tests
            // to avoid non usable addresses
            String address = alert.getExternalAddress().toString();
            externalAddress = new Address(address);
            LOGGER.info("External IP: " + externalAddress);
        } catch (Throwable e) {
            LOGGER.error("Error saving reported external ip", e);
        }
    }

    private void saveMagnetData(MetadataReceivedAlert alert) {
        try {
            torrent_handle th = alert.handle().swig();
            TorrentInfo ti = new TorrentInfo(th.get_torrent_copy());
            String sha1 = ti.infoHash().toHex();
            byte[] data = ti.bencode();

            MAGNET_CACHE.put(sha1, data);
        } catch (Throwable e) {
            LOGGER.error("Error in saving magnet in internal cache", e);
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
                download(new TorrentInfo(torrent), saveDir, resume, priorities, (String) null);
            } catch (Throwable e) {
                LOGGER.error("Unable to restore download from previous session. (" + torrent.getAbsolutePath() + ")", e);
            }
        }
    }

    public long dhtNodes() {
        return session != null ? session.getStats().dhtNodes() : 0;
    }

    private static final class LruCache<K, V> extends LinkedHashMap<K, V> {

        private final int maxSize;

        public LruCache(int maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maxSize;
        }
    }
}
