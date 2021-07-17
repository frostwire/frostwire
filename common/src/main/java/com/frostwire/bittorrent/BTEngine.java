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

package com.frostwire.bittorrent;

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
import java.io.IOException;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static com.frostwire.jlibtorrent.alerts.AlertType.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class BTEngine extends SessionManager {
    private static final Logger LOG = Logger.getLogger(BTEngine.class);
    private static final int[] INNER_LISTENER_TYPES = new int[]{
            ADD_TORRENT.swig(),
            LISTEN_SUCCEEDED.swig(),
            LISTEN_FAILED.swig(),
            EXTERNAL_IP.swig(),
            FASTRESUME_REJECTED.swig(),
            DHT_BOOTSTRAP.swig(),
            TORRENT_LOG.swig(),
            PEER_LOG.swig(),
            AlertType.LOG.swig()
    };
    private static final String TORRENT_ORIG_PATH_KEY = "torrent_orig_path";
    private static final String STATE_VERSION_KEY = "state_version";
    // this constant only changes when the libtorrent settings_pack ABI is
    // incompatible with the previous version, it should only happen from
    // time to time, not in every version
    private static final String STATE_VERSION_VALUE = "1.2.0.6";
    private final static CountDownLatch ctxSetupLatch = new CountDownLatch(1);
    public static BTContext ctx;
    private final InnerListener innerListener;
    private final Queue<RestoreDownloadTask> restoreDownloadsQueue;
    private BTEngineListener listener;

    private BTEngine() {
        super(false);
        this.innerListener = new InnerListener();
        this.restoreDownloadsQueue = new LinkedList<>();
    }

    public static BTEngine getInstance() {
        if (ctx == null) {
            try {
                ctxSetupLatch.await();
            } catch (InterruptedException e) {
                LOG.error(e.getMessage(), e);
            }
            if (ctx == null && Loader.INSTANCE.isRunning()) {
                throw new IllegalStateException("BTContext can't be null");
            }
        }
        return Loader.INSTANCE;
    }

    public static void onCtxSetupComplete() {
        ctxSetupLatch.countDown();
    }

    // this is here until we have a properly done OS utils.
    private static String escapeFilename(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|\\[\\]]+", "_");
    }

    private static String dhtBootstrapNodes() {
        StringBuilder sb = new StringBuilder();
        sb.append("dht.libtorrent.org:25401").append(",");
        sb.append("router.bittorrent.com:6881").append(",");
        sb.append("dht.transmissionbt.com:6881").append(",");
        // for DHT IPv6
        sb.append("router.silotis.us:6881");
        return sb.toString();
    }

    private static SettingsPack defaultSettings() {
        SettingsPack sp = new SettingsPack();
        //sp.broadcastLSD(true); //setting was deprecated/removed on libtorrent 1.2.4 (Feb 10th 2020)
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
        return sp;
    }

    public BTEngineListener getListener() {
        return listener;
    }

    public void setListener(BTEngineListener listener) {
        this.listener = listener;
    }

    /**
     * @see com.frostwire.android.gui.MainApplication.start() for ctx.interfaces and the rest of the context
     */
    @Override
    public void start() {
        SessionParams params = loadSettings();
        settings_pack sp = params.settings().swig();
        sp.set_str(settings_pack.string_types.listen_interfaces.swigValue(), ctx.interfaces);
        sp.set_int(settings_pack.int_types.max_retry_port_bind.swigValue(), ctx.retries);
        sp.set_str(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), dhtBootstrapNodes());
        sp.set_int(settings_pack.int_types.active_limit.swigValue(), 2000);
        sp.set_int(settings_pack.int_types.stop_tracker_timeout.swigValue(), 0);
        sp.set_int(settings_pack.int_types.alert_queue_size.swigValue(), 5000);
        sp.set_bool(settings_pack.bool_types.enable_dht.swigValue(), ctx.enableDht);
        //sp.set_bool(settings_pack.bool_types.upnp_ignore_nonrouters.swigValue(), true); // will be dropped in libtorrent 1.2.4.0 (Feb 10th 2020)
        if (ctx.optimizeMemory) {
            sp.set_bool(settings_pack.bool_types.enable_ip_notifier.swigValue(), false);
        }
        // NOTE: generate_fingerprint needs a tag number between 0 and 19, otherwise it returns an
        // invalid character that makes the app crash on android
        String fwFingerPrint = libtorrent.generate_fingerprint("FW", ctx.version[0], ctx.version[1], ctx.version[2], ctx.version[3] % 10);
        sp.set_str(settings_pack.string_types.peer_fingerprint.swigValue(), fwFingerPrint);
        String userAgent = String.format(Locale.ENGLISH,
                "FrostWire/%d.%d.%d libtorrent/%s",
                ctx.version[0],
                ctx.version[1],
                ctx.version[2],
                libtorrent.version());
        sp.set_str(settings_pack.string_types.user_agent.swigValue(), userAgent);
        LOG.info("Peer Fingerprint: " + sp.get_str(settings_pack.string_types.peer_fingerprint.swigValue()));
        LOG.info("User Agent: " + sp.get_str(settings_pack.string_types.user_agent.swigValue()));
        super.start(params);
    }

    @Override
    public void stop() {
        super.stop();
        if (ctx == null) {
            onCtxSetupComplete();
        }
    }

    @Override
    protected void onBeforeStart() {
        addListener(innerListener);
    }

    @Override
    protected void onAfterStart() {
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

    private SessionParams loadSettings() {
        try {
            File f = settingsFile();
            if (f.exists()) {
                byte[] data = FileUtils.readFileToByteArray(f);
                byte_vector buffer = Vectors.bytes2byte_vector(data);
                bdecode_node n = new bdecode_node();
                error_code ec = new error_code();
                int ret = bdecode_node.bdecode(buffer, n, ec);
                if (ret == 0) {
                    String stateVersion = n.dict_find_string_value_s(STATE_VERSION_KEY);
                    if (!STATE_VERSION_VALUE.equals(stateVersion)) {
                        return defaultParams();
                    }
                    session_params params = libtorrent.read_session_params(n);
                    buffer.clear(); // prevents GC
                    return new SessionParams(params);
                } else {
                    LOG.error("Can't decode session state data: " + ec.message());
                    return defaultParams();
                }
            } else {
                return defaultParams();
            }
        } catch (Throwable e) {
            LOG.error("Error loading session state", e);
            return defaultParams();
        }
    }

    private SessionParams defaultParams() {
        SettingsPack sp = defaultSettings();
        return new SessionParams(sp);
    }

    @Override
    protected void onApplySettings(SettingsPack sp) {
        saveSettings();
    }

    @Override
    public byte[] saveState() {
        if (swig() == null) {
            return null;
        }
        entry e = new entry();
        swig().save_state(e);
        e.set(STATE_VERSION_KEY, STATE_VERSION_VALUE);
        return Vectors.byte_vector2bytes(e.bencode());
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
        SettingsPack sp = defaultSettings();
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
        if (selection == null) {
            selection = new boolean[ti.numFiles()];
            Arrays.fill(selection, true);
        }
        Priority[] priorities = null;
        TorrentHandle th = find(ti.infoHash());
        boolean exists = th != null;
        if (selection != null) {
            if (th != null) {
                priorities = th.filePriorities();
            } else {
                priorities = Priority.array(Priority.IGNORE, ti.numFiles());
            }
            boolean changed = false;
            for (int i = 0; i < selection.length; i++) {
                if (selection[i] && priorities[i] == Priority.IGNORE) {
                    priorities[i] = Priority.NORMAL;
                    changed = true;
                }
            }
            if (!changed) { // nothing to do
                return;
            }
        }
        download(ti, saveDir, priorities, null, null);
        if (!exists) {
            saveResumeTorrent(ti);
        }
    }

    public void download(TorrentInfo ti, File saveDir, boolean[] selection, List<TcpEndpoint> peers) {
        download(ti, saveDir, selection, peers, false);
    }

    public void download(TorrentInfo ti, File saveDir, boolean[] selection, List<TcpEndpoint> peers, boolean dontSaveTorrentFile) {
        if (swig() == null) {
            return;
        }
        saveDir = setupSaveDir(saveDir);
        if (saveDir == null) {
            return;
        }
        if (selection == null) {
            selection = new boolean[ti.numFiles()];
            Arrays.fill(selection, true);
        }
        Priority[] priorities = null;
        TorrentHandle th = find(ti.infoHash());
        boolean torrentHandleExists = th != null;
        if (torrentHandleExists) {
            try {
                priorities = th.filePriorities();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            priorities = Priority.array(Priority.IGNORE, ti.numFiles());
        }
        if (priorities != null) {
            boolean changed = false;
            for (int i = 0; i < selection.length; i++) {
                if (selection[i] && i < priorities.length && priorities[i] == Priority.IGNORE) {
                    priorities[i] = Priority.NORMAL;
                    changed = true;
                }
            }
        }
        download(ti, saveDir, priorities, null, peers);

        saveResumeTorrent(ti);
        if (!dontSaveTorrentFile) {
            saveTorrent(ti);
        }
    }

    public void download(TorrentCrawledSearchResult sr, File saveDir) {
        download(sr, saveDir, false);
    }

    public void download(TorrentCrawledSearchResult sr, File saveDir, boolean dontSaveTorrentFile) {
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
            saveResumeTorrent(ti);
            if (!dontSaveTorrentFile) {
                saveTorrent(ti);
            }
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
        File[] torrents = ctx.homeDir.listFiles((dir, name) -> name != null && FilenameUtils.getExtension(name).toLowerCase().equals("torrent"));
        if (torrents != null) {
            for (File t : torrents) {
                try {
                    String infoHash = FilenameUtils.getBaseName(t.getName());
                    if (infoHash != null) {
                        File resumeFile = resumeDataFile(infoHash);
                        File savePath = readSavePath(infoHash);
                        if (setupSaveDir(savePath) == null) {
                            LOG.warn("Can't create data dir or mount point is not accessible");
                            continue;
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

    File torrentFile(String name) {
        return new File(ctx.torrentsDir, name + ".torrent");
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

    private void saveTorrent(TorrentInfo ti) {
        File torrentFile;
        try {
            String name = getEscapedFilename(ti);
            torrentFile = torrentFile(name);
            byte[] arr = ti.toEntry().bencode();
            FileSystem fs = Platforms.get().fileSystem();
            fs.write(torrentFile, arr);
            fs.scan(torrentFile);
        } catch (Throwable e) {
            LOG.warn("Error saving torrent info to file", e);
        }
    }

    private void saveResumeTorrent(TorrentInfo ti) {
        try {
            String name = getEscapedFilename(ti);
            entry e = ti.toEntry().swig();
            e.dict().set(TORRENT_ORIG_PATH_KEY, new entry(torrentFile(name).getAbsolutePath()));
            byte[] arr = Vectors.byte_vector2bytes(e.bencode());
            FileUtils.writeByteArrayToFile(resumeTorrentFile(ti.infoHash().toString()), arr);
        } catch (Throwable e) {
            LOG.warn("Error saving resume torrent", e);
        }
    }

    private String getEscapedFilename(TorrentInfo ti) {
        String name = ti.name();
        if (name == null || name.length() == 0) {
            name = ti.infoHash().toString();
        }
        return escapeFilename(name);
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
            if (th != null) {
                BTDownload dl = new BTDownload(this, th);
                if (listener != null) {
                    listener.downloadAdded(this, dl);
                }
            } else {
                LOG.info("torrent was not successfully added");
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
        ErrorCode error = alert.error();
        String message = error.message() + "/value=" + error.value();
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
                            saveResumeTorrent(new TorrentInfo(torrent));
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

    private void download(TorrentInfo ti, File saveDir, Priority[] priorities, File resumeFile, List<TcpEndpoint> peers) {
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
            download(ti, saveDir, resumeFile, priorities, peers);
            th = find(ti.infoHash());
            if (th != null) {
                fireDownloadUpdate(th);
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

    private void onDhtBootstrap() {
        //long nodes = stats().dhtNodes();
        //LOG.info("DHT bootstrap, total nodes=" + nodes);
    }

    private void printAlert(Alert alert) {
        System.out.println("Log: " + alert);
    }

    private static class Loader {
        static final BTEngine INSTANCE = new BTEngine();
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
                case ADD_TORRENT:
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
                case DHT_BOOTSTRAP:
                    onDhtBootstrap();
                    break;
                case TORRENT_LOG:
                case PEER_LOG:
                case LOG:
                    printAlert(alert);
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
                download(new TorrentInfo(torrent), saveDir, resume, priorities, null);
            } catch (Throwable e) {
                LOG.error("Unable to restore download from previous session. (" + torrent.getAbsolutePath() + ")", e);
            }
        }
    }
}
