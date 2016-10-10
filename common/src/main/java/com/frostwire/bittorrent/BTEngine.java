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
import com.frostwire.jlibtorrent.swig.entry;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.frostwire.jlibtorrent.swig.string_int_pair;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.util.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;

import static com.frostwire.jlibtorrent.alerts.AlertType.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class BTEngine extends SessionManager {

    private static final Logger LOG = Logger.getLogger(BTEngine.class);

    private static final int[] INNER_LISTENER_TYPES = new int[]{
            TORRENT_ADDED.swig(),
            LISTEN_SUCCEEDED.swig(),
            LISTEN_FAILED.swig(),
            EXTERNAL_IP.swig(),
            FASTRESUME_REJECTED.swig(),
            TORRENT_LOG.swig(),
            AlertType.LOG.swig()
    };

    private static final String TORRENT_ORIG_PATH_KEY = "torrent_orig_path";
    public static BTContext ctx;

    private final InnerListener innerListener;
    private final Queue<RestoreDownloadTask> restoreDownloadsQueue;

    private BTEngineListener listener;

    private BTEngine() {
        super(false);
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

    public BTEngineListener getListener() {
        return listener;
    }

    public void setListener(BTEngineListener listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        settings_pack sp = new settings_pack();

        sp.set_str(settings_pack.string_types.listen_interfaces.swigValue(), ctx.interfaces);
        sp.set_int(settings_pack.int_types.max_retry_port_bind.swigValue(), ctx.retries);

        //super.start(new SettingsPack(sp));
        SessionParams params = new SessionParams(new SettingsPack(sp));
        super.start(params);
    }

    @Override
    protected void onBeforeStart() {
        addListener(innerListener);
    }

    @Override
    protected void onAfterStart() {
        for (Pair<String, Integer> r : defaultRouters()) {
            string_int_pair p = new string_int_pair(r.first, r.second);
            swig().add_dht_router(p);
        }

        loadSettings();
        fireStarted();
    }

    @Override
    protected void onBeforeStop() {
        removeListener(innerListener);
        saveSettings();
    }

    @Override
    protected void onAfterStop() {
        fireStopped();
    }

    @Override
    public void moveStorage(File dataDir) {
        if (swig() == null) {
            return;
        }

        ctx.dataDir = dataDir; // this will be removed when we start using platform

        super.moveStorage(dataDir);
    }

    private void loadSettings() {
        if (swig() == null) {
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
            LOG.error("Error loading session state", e);
        }
    }

    @Override
    protected void onApplySettings(SettingsPack sp) {
        saveSettings();
    }

    private void saveSettings() {
        if (swig() == null) {
            return;
        }

        try {
            byte[] data = saveState();
            FileUtils.writeByteArrayToFile(settingsFile(), data);
        } catch (Throwable e) {
            LOG.error("Error saving session state", e);
        }
    }

    public void revertToDefaultConfiguration() {
        if (swig() == null) {
            return;
        }

        SettingsPack sp = settings();

        sp.broadcastLSD(true);

        if (ctx.optimizeMemory) {
            int maxQueuedDiskBytes = sp.maxQueuedDiskBytes();
            sp.maxQueuedDiskBytes(maxQueuedDiskBytes / 2);
            int sendBufferWatermark = sp.sendBufferWatermark();
            sp.sendBufferWatermark(sendBufferWatermark / 2);
            sp.cacheSize(256);
            sp.activeDownloads(4);
            sp.activeSeeds(4);
            sp.maxPeerlistSize(200);
            //sp.setGuidedReadCache(true);
            sp.tickInterval(1000);
            sp.inactivityTimeout(60);
            sp.seedingOutgoingConnections(false);
            sp.connectionsLimit(200);
        } else {
            sp.activeDownloads(10);
            sp.activeSeeds(10);
        }

        applySettings(sp);
    }

    public void download(File torrent, File saveDir, boolean[] selection) {
        if (swig() == null) {
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
        if (swig() == null) {
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
        if (swig() == null) {
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

    public void restoreDownloads() {
        if (swig() == null) {
            return;
        }

        if (ctx.homeDir == null || !ctx.homeDir.exists()) {
            LOG.warn("Wrong setup with BTEngine home dir");
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
            LOG.warn("Error saving torrent info to file", e);
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
            LOG.warn("Error saving resume torrent", e);
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
            TorrentHandle th = find(alert.handle().infoHash());
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

    private void onListenSucceeded(ListenSucceededAlert alert) {
        try {
            String endp = alert.address() + ":" + alert.port();
            String s = "endpoint: " + endp + " type:" + alert.socketType();
            LOG.info("Listen succeeded on " + s);
        } catch (Throwable e) {
            LOG.error("Error adding listen endpoint to internal list", e);
        }
    }

    private void onListenFailed(ListenFailedAlert alert) {
        String endp = alert.address() + ":" + alert.port();
        String s = "endpoint: " + endp + " type:" + alert.socketType();
        String message = alert.error().message();
        LOG.info("Listen failed on " + s + " (error: " + message + ")");
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
                        List<Entry> filePriorities = map.get("file_priorities").list();

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
        RestoreDownloadTask task = null;
        try {
            if (!restoreDownloadsQueue.isEmpty()) {
                task = restoreDownloadsQueue.poll();
            }
        } catch (Throwable t) {
            // on Android, LinkedList's .poll() implementation throws a NoSuchElementException
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
            // TODO: restore the last parameter
            download(ti, saveDir, resumeFile, priorities, null);
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
                case LISTEN_SUCCEEDED:
                    onListenSucceeded((ListenSucceededAlert) alert);
                    break;
                case LISTEN_FAILED:
                    onListenFailed((ListenFailedAlert) alert);
                    break;
                case EXTERNAL_IP:
                    onExternalIpAlert((ExternalIpAlert) alert);
                    break;
                case FASTRESUME_REJECTED:
                    onFastresumeRejected((FastresumeRejectedAlert) alert);
                    break;
                case TORRENT_LOG:
                case LOG:
                    printAlert(alert);
                    break;
            }
        }
    }

    private void onExternalIpAlert(ExternalIpAlert alert) {
        try {
            // libtorrent perform all kind of tests
            // to avoid non usable addresses
            String address = alert.externalAddress().toString();
            LOG.info("External IP: " + address);
        } catch (Throwable e) {
            LOG.error("Error saving reported external ip", e);
        }
    }

    private void onFastresumeRejected(FastresumeRejectedAlert alert) {
        try {
            LOG.warn("Failed to load fastresume data, path: " + alert.filePath() +
                    ", operation: " + alert.operation() + ", error: " + alert.error().message());
        } catch (Throwable e) {
            LOG.error("Error logging fastresume rejected alert", e);
        }
    }

    private void printAlert(Alert alert) {
        System.out.println("Log: " + alert);
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
                download(new TorrentInfo(torrent), saveDir, resume, priorities, null);
            } catch (Throwable e) {
                LOG.error("Unable to restore download from previous session. (" + torrent.getAbsolutePath() + ")", e);
            }
        }
    }

    private static List<Pair<String, Integer>> defaultRouters() {
        List<Pair<String, Integer>> list = new LinkedList<Pair<String, Integer>>();

        list.add(new Pair<>("router.bittorrent.com", 6881));
        list.add(new Pair<>("dht.transmissionbt.com", 6881));

        return list;
    }
}
