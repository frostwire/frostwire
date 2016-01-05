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
import com.frostwire.jlibtorrent.swig.torrent_handle;
import com.frostwire.logging.Logger;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;
import java.util.concurrent.CountDownLatch;
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
            DHT_STATS.getSwig()};

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

            Pair<Integer, Integer> prange = new Pair<>(ctx.port0, ctx.port1);
            session = new Session(prange, ctx.iface);

            downloader = new Downloader(session);

            loadSettings();
            session.addListener(innerListener);

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

    /**
     * Resets Bittorrent's folders for .torrents, data files, homeDir, ports and network interface with
     * the given value.
     *
     * @param dotTorrentsDir - The folder where the .torrent files are saved.
     * @param torrentDataDir - The folder where the torrent data files are saved.
     * @param homeDir        - Seems like the library's configuration files folder
     * @param port0          - port range start
     * @param port1          - port range end
     * @param iface          - network interface
     * @param start          - Should we start the core after reloading the context
     * @param stop           - Should we stop the core after reloading the context. Stop happens first than start if both are on
     */
    public void reloadBTContext(File dotTorrentsDir, File torrentDataDir, File homeDir, int port0, int port1, String iface, boolean stop, boolean start) {
        BTContext ctx = new BTContext();
        ctx.homeDir = homeDir;
        ctx.torrentsDir = dotTorrentsDir;
        ctx.dataDir = torrentDataDir;
        ctx.port0 = port0;
        ctx.port1 = port1;
        ctx.iface = iface;
        BTEngine.ctx = ctx;

        // Let's keep the session transfer states on a map, pause the entire session
        // and then bring everything back to life manually so the session won't screw up
        // my updated resume files.
        List<TorrentHandle> torrents;
        Map<String, TorrentStatus.State> snapshot = null;
        if (session != null) {
            torrents = session.getTorrents();
            snapshot = sessionSnapshot(torrents, true);
        }

        if (BTEngine.getInstance().getSession() != null && snapshot != null) {
            pauseAndMoveActiveIncompleteTorrents(torrentDataDir);
        }

        if (stop) {
            BTEngine.getInstance().stop();
        }

        if (start) {
            BTEngine.getInstance().start();
        }

        // now let's manually restart the ones that were paused.
        if (session != null && snapshot != null) {
            restartTorrents(session, snapshot);
        }

    }

    private void restartTorrents(Session session, Map<String, TorrentStatus.State> sessionSnapshot) {
        final Set<String> infohashes = sessionSnapshot.keySet();
        for (final String infohash : infohashes) {
            final TorrentHandle th = session.findTorrent(new Sha1Hash(infohash));
            if (th == null) {
                continue;
            }
            TorrentStatus.State state = sessionSnapshot.get(th.getInfoHash().toString());
            if (state != null && state.getSwig() == TorrentStatus.State.DOWNLOADING.getSwig()) {
                th.resume();
            }
        }
    }

    private class PausedTorrentAlertListener implements AlertListener {
        private CountDownLatch pauseLatch;
        private final File torrentDataDir;

        public PausedTorrentAlertListener(File torrentDataDir) {
            this.torrentDataDir = torrentDataDir;
        }

        public void setCountdownLatch(CountDownLatch latch) {
            pauseLatch = latch;
        }

        @Override
        public int[] types() {
            return new int[]{AlertType.TORRENT_PAUSED.getSwig()};
        }

        @Override
        public void alert(Alert<?> alert) {
            if (alert.getType().getSwig() != AlertType.TORRENT_PAUSED.getSwig()) {
                return;
            }

            TorrentPausedAlert pausedAlert = (TorrentPausedAlert) alert;
            TorrentHandle th = pausedAlert.getHandle();
            if (!th.isValid()) {
                return;
            }

            th.getSwig().move_storage(torrentDataDir.getAbsolutePath());
            th.saveResumeData();

            if (pauseLatch != null) {
                pauseLatch.countDown();
            }
        }
    }

    /**
     * Get the ongoing transfers and have them move to the new location.
     * Make sure the .resume files for these .torrents are also updated.
     */
    private void pauseAndMoveActiveIncompleteTorrents(final File torrentDataDir) {
        final List<TorrentHandle> torrents = session.getTorrents();
        if (torrents != null) {
            PausedTorrentAlertListener torrentPausedListener = new PausedTorrentAlertListener(torrentDataDir);
            session.addListener(torrentPausedListener);
            // Pause all torrents individually.
            int paused = 0;
            for (TorrentHandle th : torrents) {
                if (th.isValid() && !th.getStatus().isSeeding() && !th.getStatus().isFinished()) {
                    torrent_handle sth = th.getSwig();
                    sth.auto_managed(false);
                    sth.flush_cache();
                    //non-graceful pause, disconnect everyone.
                    sth.pause(0);  //when pause signal is delivered, migration occurs on alertListener
                    paused++;
                }
            }
            final CountDownLatch pauseLatch = new CountDownLatch(paused);
            torrentPausedListener.setCountdownLatch(pauseLatch);
            try {
                pauseLatch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            session.removeListener(torrentPausedListener);
        }
    }

    private Map<String, TorrentStatus.State> sessionSnapshot(List<TorrentHandle> torrents, boolean cleanInvalid) {
        Map<String, TorrentStatus.State> torrentHandleStatuses = new HashMap<>();
        for (final TorrentHandle th : torrents) {
            if (th != null && th.isValid()) {
                torrentHandleStatuses.put(th.getInfoHash().toString(), th.getStatus().getState());
                System.out.println(th.getStatus().getState().toString() + " -> " + th.getInfoHash().toString() + " " + th.getTorrentInfo().getName());
            } else if (cleanInvalid) {
                // use opportunity to cleanup.
                session.removeTorrent(th);
            }
        }
        return torrentHandleStatuses;
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

    public byte[] fetchMagnet(String uri, long timeout) {
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
            entry e = entry.bdecode(Vectors.bytes2char_vector(arr));
            torrent = new File(e.dict().get(TORRENT_ORIG_PATH_KEY).string());
        } catch (Throwable e) {
            // can't recover original torrent path
        }

        return torrent;
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

            FileUtils.writeByteArrayToFile(torrentFile, arr);
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
            byte[] arr = Vectors.char_vector2bytes(e.bencode());
            FileUtils.writeByteArrayToFile(resumeTorrentFile(ti.getInfoHash().toString()), arr);
        } catch (Throwable e) {
            LOG.warn("Error saving resume torrent", e);
        }
    }

    private void doResumeData(TorrentAlert<?> alert) {
        try {
            TorrentHandle th = session.findTorrent(alert.getHandle().getInfoHash());
            if (th != null && th.isValid() && th.needSaveResumeData()) {
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

        if (result != null && !result.isDirectory() && !result.mkdirs()) {
            result = null;
            LOG.warn("Failed to create save dir to download");
        }

        if (result != null && !result.canWrite()) {
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
                    doResumeData(torrentAlert);
                    runNextRestoreDownloadTask();
                    break;
                case PIECE_FINISHED:
                    doResumeData((TorrentAlert<?>) alert);
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

    public int getTotalDHTNodes() {
        return totalDHTNodes;
    }
}
