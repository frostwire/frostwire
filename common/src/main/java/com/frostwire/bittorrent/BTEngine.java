/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.*;
import com.frostwire.jlibtorrent.alerts.*;
import com.frostwire.jlibtorrent.swig.*;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
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
    
    // Cached paused state to avoid blocking EDT calls
    private volatile boolean cachedPausedState = false;

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
        return "dht.libtorrent.org:25401" + "," +
                "router.bittorrent.com:6881" + "," +
                "dht.transmissionbt.com:6881" + "," +
                // for DHT IPv6
                "router.silotis.us:6881";
    }

    private static SettingsPack defaultSettings() {
        SettingsPack sp = new SettingsPack();
        //sp.broadcastLSD(true); //setting was deprecated/removed on libtorrent 1.2.4 (Feb 10th 2020)
        sp.validateHttpsTrackers(false);
        if (ctx.optimizeMemory) {
            int maxQueuedDiskBytes = sp.maxQueuedDiskBytes();
            sp.maxQueuedDiskBytes(maxQueuedDiskBytes / 2);
            int sendBufferWatermark = sp.sendBufferWatermark();
            sp.sendBufferWatermark(sendBufferWatermark / 2);
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
     * @see com.frostwire.android.gui.MainApplication::onCreate() for ctx.interfaces and the rest of the context
     */
    @SuppressWarnings("JavadocReference")
    @Override
    public void start() {
        SessionParams params = loadSettings();
        settings_pack sp = params.swig().getSettings();
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

        try {
            super.start(params);
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
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
        // Initialize cached paused state on start in background thread to avoid EDT blocking
        new Thread(() -> {
            try {
                cachedPausedState = super.isPaused();
            } catch (Exception e) {
                // If there's an issue getting the initial state, assume not paused
                cachedPausedState = false;
            }
        }, "BTEngine-PausedStateInit").start();
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
                    session_params params = session_params.read_session_params(n);
                    buffer.clear(); // prevents GC
                    SessionParams result = new SessionParams(params);
                    if (OSUtils.isMacOSX()) {
                        result.setPosixDiskIO();
                    } else {
                        result.setDefaultDiskIO();
                    }
                    return result;
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
        SessionParams params = new SessionParams(sp);
        if (OSUtils.isMacOSX()) {
            params.setPosixDiskIO();
        } else {
            params.setDefaultDiskIO();
        }
        return params;
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

        com.frostwire.jlibtorrent.swig.session session = swig();
        session_params params = session.session_state();
        if (params == null) {
            LOG.warn("Unable to save session state, session_params is null");
            return null;
        }
        entry e = session_params.write_session_params(params, save_state_flags_t.all());
        //entry e = entry.from_string_bytes(session_params.write_session_params_buf(params, save_state_flags_t.all()));
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

    /**
     * Override pause method to maintain cached state
     */
    @Override
    public void pause() {
        super.pause();
        cachedPausedState = true;
    }

    /**
     * Override resume method to maintain cached state
     */
    @Override
    public void resume() {
        super.resume();
        cachedPausedState = false;
    }

    /**
     * Non-blocking method to check if engine is paused
     * Uses cached state to avoid EDT blocking calls
     */
    public boolean isPausedCached() {
        return cachedPausedState;
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
        TorrentHandle th = find(ti.infoHashV1());
        boolean exists = th != null;
        if (th != null) {
            if (th.isValid()) {
                priorities = th.filePriorities();
            }
        } else {
            priorities = Priority.array(Priority.IGNORE, ti.numFiles());
        }

        if (priorities != null) {
            for (int i = 0; i < selection.length; i++) {
                priorities[i] = selection[i] ? Priority.NORMAL : Priority.IGNORE;
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

    public void download(TorrentInfo ti, File saveDir, boolean[] selection, List<TcpEndpoint> peers, boolean saveTorrentFile) {
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
        TorrentHandle th = find(ti.infoHashV1());
        boolean torrentHandleExists = th != null;
        if (torrentHandleExists) {
            try {
                if (th.isValid()) {
                    priorities = th.filePriorities();
                }
            } catch (Throwable t) {
                LOG.error("Error loading session state", t);
            }
        }
        if (priorities == null) {
            priorities = Priority.array(Priority.IGNORE, ti.numFiles());
        }
        // Update priorities based on selection
        for (int i = 0; i < selection.length && i < priorities.length; i++) {
            priorities[i] = selection[i] ? Priority.NORMAL : Priority.IGNORE;
        }
        download(ti, saveDir, priorities, null, peers);

        saveResumeTorrent(ti);
        if (saveTorrentFile) {
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
        TorrentHandle th = find(ti.infoHashV1());
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
        File[] torrents = ctx.homeDir.listFiles((dir, name) -> name != null && FilenameUtils.getExtension(name).equalsIgnoreCase("torrent"));
        if (torrents != null) {
            for (File t : torrents) {
                try {
                    String infoHash = FilenameUtils.getBaseName(t.getName());
                    if (infoHash != null) {
                        File resumeFile = resumeDataFile(infoHash);
                        File savePath = readSavePath(infoHash);
                        File checked = setupSaveDir(savePath);
                        if (checked == null) {
                            // fallback to default data dir
                            checked = setupSaveDir(ctx.dataDir);
                        }
                        if (checked == null) {
                            LOG.warn("Can't create data dir or mount point is not accessible for infoHash=" + infoHash);
                            continue;
                        }
                        restoreDownloadsQueue.add(new RestoreDownloadTask(t, checked, null, resumeFile));
                    }
                } catch (Throwable e) {
                    LOG.error("Error restoring torrent download: " + t, e);
                }
                runNextRestoreDownloadTask();
            }
        }


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
        } catch (Throwable e) {
            LOG.warn("Error saving torrent info to file", e);
        }
    }

    private void saveResumeTorrent(TorrentInfo ti) {
        try {
            String name = getEscapedFilename(ti);
            entry e = ti.toEntry().swig();
            e.dict().put(TORRENT_ORIG_PATH_KEY, new entry(torrentFile(name).getAbsolutePath()));
            byte[] arr = Vectors.byte_vector2bytes(e.bencode());
            FileUtils.writeByteArrayToFile(resumeTorrentFile(ti.infoHashV1().toString()), arr);
        } catch (Throwable e) {
            LOG.warn("Error saving resume torrent", e);
        }
    }

    private String getEscapedFilename(TorrentInfo ti) {
        String name = ti.name();
        if (name == null || name.isEmpty()) {
            name = ti.infoHashV1().toString();
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

    private void download(TorrentInfo ti, File saveDir, Priority[] priorities, @SuppressWarnings("SameParameterValue") File resumeFile, List<TcpEndpoint> peers) {
        TorrentHandle th = find(ti.infoHashV1());
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
            // Add paused to avoid the race where default priorities are used
            download(ti, saveDir, resumeFile, priorities, peers, TorrentFlags.PAUSED);
            th = find(ti.infoHashV1());
            LOG.info("BTEngine.download() - new download - torrent_handle is in session? " + th.inSession());
            if (th != null) {
                // Re-apply priorities on the handle to be 100% sure they stick
                if (priorities != null && priorities.length == ti.numFiles()) {
                    th.prioritizeFiles(priorities);
                }
                try {
                    th.setFlags(TorrentFlags.NEED_SAVE_RESUME);
                    th.saveResumeData(TorrentHandle.SAVE_INFO_DICT);
                } catch (Throwable t) {
                    LOG.warn("BTEngine.download() - unable to trigger initial resume save", t);
                }
                fireDownloadUpdate(th);
                th.resume();
            } else {
                LOG.warn("BTEngine.download() - new download: torrent was not successfully added, torrent handle not found by infoHashV1");
            }
        }
    }

    private void onExternalIpAlert(ExternalIpAlert alert) {
        try {
            // libtorrent perform all kind of tests
            // to avoid non-usable addresses
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

    private void printAlert(@SuppressWarnings("rawtypes") Alert alert) {
        LOG.info("Log: " + alert);
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
                download(new TorrentInfo(torrent), saveDir, resume, priorities, null, TorrentFlags.PAUSED);
            } catch (Throwable e) {
                LOG.error("Unable to restore download from previous session. (" + torrent.getAbsolutePath() + ")", e);
            }
        }
    }
}
