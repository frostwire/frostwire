/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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
import com.frostwire.jlibtorrent.swig.entry;
import com.frostwire.jlibtorrent.swig.string_entry_map;
import com.frostwire.jlibtorrent.swig.string_vector;
import com.frostwire.platform.Platforms;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.TransferItem;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class BTDownload implements BittorrentDownload {

    private static final Logger LOG = Logger.getLogger(BTDownload.class);

    private static final long SAVE_RESUME_RESOLUTION_MILLIS = 10000;

    private static final int[] ALERT_TYPES = {
            AlertType.TORRENT_FINISHED.swig(),
            AlertType.TORRENT_REMOVED.swig(),
            AlertType.TORRENT_CHECKED.swig(),
            AlertType.SAVE_RESUME_DATA.swig(),
            AlertType.PIECE_FINISHED.swig(),
            AlertType.STORAGE_MOVED.swig()};

    private static final String EXTRA_DATA_KEY = "extra_data";
    private static final String WAS_PAUSED_EXTRA_KEY = "was_paused";

    private final BTEngine engine;
    private final TorrentHandle th;
    private final File savePath;
    private final Date created;
    private final PiecesTracker piecesTracker;
    private final File parts;

    private final Map<String, String> extra;

    private BTDownloadListener listener;

    private Set<File> incompleteFilesToRemove;

    private long lastSaveResumeTime;
    private final PaymentOptions paymentOptions;

    private final InnerListener innerListener;

    public BTDownload(BTEngine engine, TorrentHandle th) {
        this.engine = engine;
        this.th = th;
        this.savePath = new File(th.savePath());
        this.created = new Date(th.status().addedTime());
        TorrentInfo ti = th.torrentFile();
        this.piecesTracker = ti != null ? new PiecesTracker(ti) : null;
        this.parts = ti != null ? new File(savePath, "." + ti.infoHash() + ".parts") : null;

        this.extra = createExtra();
        this.paymentOptions = loadPaymentOptions(ti);

        this.innerListener = new InnerListener();
        engine.addListener(innerListener);
    }

    public Map<String, String> getExtra() {
        return extra;
    }

    @Override
    public String getName() {
        if (th == null) {
            return null;
        }
        return th.name();
    }

    @Override
    public String getDisplayName() {
        Priority[] priorities = th.filePriorities();

        int count = 0;
        int index = 0;
        for (int i = 0; i < priorities.length; i++) {
            if (!Priority.IGNORE.equals(priorities[i])) {
                count++;
                index = i;
            }
        }

        return count != 1 ? th.name() : FilenameUtils.getName(th.torrentFile().files().filePath(index));
    }

    public long getSize() {
        TorrentInfo ti = th.torrentFile();
        return ti != null ? ti.totalSize() : 0;
    }

    public PaymentOptions getPaymentOptions() {
        return paymentOptions;
    }

    public boolean isPaused() {
        if (!th.isValid()) {
            return false;
        }
        return th.status().isPaused() || engine.isPaused() || !engine.isRunning();
    }

    public boolean isSeeding() {
        if (!th.isValid()) {
            return false;
        }
        return th.status().isSeeding();
    }

    public boolean isFinished() {
        if (!th.isValid()) {
            return false;
        }
        return th.status().isFinished();
    }

    public TransferState getState() {
        if (!engine.isRunning()) {
            return TransferState.STOPPED;
        }

        if (engine.isPaused()) {
            return TransferState.PAUSED;
        }

        if (!th.isValid()) {
            return TransferState.ERROR;
        }

        final TorrentStatus status = th.status();

        if (status.isPaused() && status.isFinished()) {
            return TransferState.FINISHED;
        }

        if (status.isPaused() && !status.isFinished()) {
            return TransferState.PAUSED;
        }

        if (!status.isPaused() && status.isFinished()) { // see the docs of isFinished
            return TransferState.SEEDING;
        }

        final TorrentStatus.State state = status.state();

        switch (state) {
            case CHECKING_FILES:
                return TransferState.CHECKING;
            case DOWNLOADING_METADATA:
                return TransferState.DOWNLOADING_METADATA;
            case DOWNLOADING:
                return TransferState.DOWNLOADING;
            case FINISHED:
                return TransferState.FINISHED;
            case SEEDING:
                return TransferState.SEEDING;
            case ALLOCATING:
                return TransferState.ALLOCATING;
            case CHECKING_RESUME_DATA:
                return TransferState.CHECKING;
            case UNKNOWN:
                return TransferState.UNKNOWN;
            default:
                return TransferState.UNKNOWN;
        }
    }

    /**
     * If we have a torrent which is downloaded as a folder, this will return the parent of that folder.
     * (e.g. default save location for torrents)
     * <p/>
     * If you want to have the folder where the torrent's data files are located you
     * want to use getContentSavePath().
     */
    @Override
    public File getSavePath() {
        return savePath;
    }

    @Override
    public File previewFile() {
        return null;
    }

    @Override
    public int getProgress() {
        if (th == null) {
            return 0;
        }

        if (th.torrentFile() == null) {
            return 0;
        }

        // TODO: Add logic to check completion logic for merkle based torrents.
        //if (th.getTorrentInfo().isMerkleTorrent()) {
        //final ArrayList<Sha1Hash> sha1Hashes = th.getTorrentInfo().merkleTree();
        //perform sha1Hash check
        //}

        if (th.status() == null) {
            return 0;
        }

        float fp = th.status().progress();
        TorrentStatus.State state = th.status().state();

        if (Float.compare(fp, 1f) == 0 && state != TorrentStatus.State.CHECKING_FILES) {
            return 100;
        }

        int p = (int) (th.status().progress() * 100);
        if (p > 0 && state != TorrentStatus.State.CHECKING_FILES) {
            return Math.min(p, 100);
        }
        final long received = getTotalBytesReceived();
        final long size = getSize();
        if (size == received) {
            return 100;
        }
        if (size > 0) {
            p = (int) ((received * 100) / size);
            return Math.min(p, 100);
        }

        return 0;
    }

    @Override
    public boolean isComplete() {
        return getProgress() == 100;
    }

    public long getBytesReceived() {
        return th.status().totalDownload();
    }

    public long getTotalBytesReceived() {
        return th.status().allTimeDownload();
    }

    public long getBytesSent() {
        return th.status().totalUpload();
    }

    public long getTotalBytesSent() {
        return th.status().allTimeUpload();
    }

    public long getDownloadSpeed() {
        return (isFinished() || isPaused() || isSeeding()) ? 0 : th.status().downloadPayloadRate();
    }

    public long getUploadSpeed() {
        return ((isFinished() && !isSeeding()) || isPaused()) ? 0 : th.status().uploadPayloadRate();
    }

    @Override
    public boolean isDownloading() {
        return getDownloadSpeed() > 0;
    }

    public int getConnectedPeers() {
        return th.status().numPeers();
    }

    public int getTotalPeers() {
        return th.status().listPeers();
    }

    public int getConnectedSeeds() {
        return th.status().numSeeds();
    }

    public int getTotalSeeds() {
        return th.status().listSeeds();
    }

    @Override
    public File getContentSavePath() {
        try {
            if (!th.isValid()) {
                return null;
            }

            TorrentInfo ti = th.torrentFile();
            if (ti != null && ti.swig() != null) {
                return new File(savePath.getAbsolutePath(), ti.numFiles() > 1 ? th.name() : ti.files().filePath(0));
            }
        } catch (Throwable e) {
            LOG.warn("Could not retrieve download content save path", e);
        }

        return null;
    }

    public String getInfoHash() {
        return th.infoHash().toString();
    }

    @Override
    public Date getCreated() {
        return created;
    }

    public long getETA() {
        TorrentInfo ti = th.torrentFile();
        if (ti == null) {
            return 0;
        }

        TorrentStatus status = th.status();
        long left = ti.totalSize() - status.totalDone();
        long rate = status.downloadPayloadRate();

        if (left <= 0) {
            return 0;
        }

        if (rate <= 0) {
            return -1;
        }

        return left / rate;
    }

    public void pause() {
        if (!th.isValid()) {
            return;
        }

        extra.put(WAS_PAUSED_EXTRA_KEY, Boolean.TRUE.toString());

        th.setAutoManaged(false);
        th.pause();

        doResumeData(true);
    }

    public void resume() {
        if (!th.isValid()) {
            return;
        }

        extra.put(WAS_PAUSED_EXTRA_KEY, Boolean.FALSE.toString());

        th.setAutoManaged(true);
        th.resume();

        doResumeData(true);
    }

    public void remove() {
        remove(false, false);
    }

    @Override
    public void remove(boolean deleteData) {
        remove(false, deleteData);
    }

    public void remove(boolean deleteTorrent, boolean deleteData) {
        String infoHash = this.getInfoHash();

        incompleteFilesToRemove = getIncompleteFiles();

        if (th.isValid()) {
            if (deleteData) {
                engine.remove(th, SessionHandle.Options.DELETE_FILES);
            } else {
                engine.remove(th);
            }
        }

        if (deleteTorrent) {
            File torrent = engine.readTorrentPath(infoHash);
            if (torrent != null) {
                Platforms.get().fileSystem().delete(torrent);
            }
        }

        engine.resumeDataFile(infoHash).delete();
        engine.resumeTorrentFile(infoHash).delete();
    }

    public BTDownloadListener getListener() {
        return listener;
    }

    public void setListener(BTDownloadListener listener) {
        this.listener = listener;
    }

    private void torrentFinished() {
        if (listener != null) {
            try {
                listener.finished(this);
            } catch (Throwable e) {
                LOG.error("Error calling listener", e);
            }
        }

        doResumeData(true);
    }

    private void torrentRemoved() {
        engine.removeListener(innerListener);

        if (parts != null) {
            parts.delete();
        }

        fireRemoved(incompleteFilesToRemove);
    }

    private void torrentChecked() {
        try {
            if (th.isValid()) {
                // trigger items calculation
                getItems();
            }

        } catch (Throwable e) {
            LOG.warn("Error handling torrent checked logic", e);
        }
    }

    private void pieceFinished(PieceFinishedAlert alert) {
        try {
            if (piecesTracker != null) {
                piecesTracker.setComplete(alert.pieceIndex(), true);
            }
        } catch (Throwable e) {
            LOG.warn("Error handling piece finished logic", e);
        }
    }

    public boolean isPartial() {
        Priority[] priorities = th.filePriorities();

        for (Priority p : priorities) {
            if (Priority.IGNORE.equals(p)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String magnetUri() {
        return th.makeMagnetUri();
    }

    public int getDownloadRateLimit() {
        return th.getDownloadLimit();
    }

    public void setDownloadRateLimit(int limit) {
        th.setDownloadLimit(limit);
    }

    public int getUploadRateLimit() {
        return th.getUploadLimit();
    }

    public void setUploadRateLimit(int limit) {
        th.setUploadLimit(limit);
    }

    public void requestTrackerAnnounce() {
        th.forceReannounce();
    }

    public void requestTrackerScrape() {
        th.scrapeTracker();
    }

    public Set<String> trackers() {
        List<AnnounceEntry> trackers = th.trackers();

        Set<String> urls = new HashSet<>(trackers.size());

        for (AnnounceEntry e : trackers) {
            urls.add(e.url());
        }

        return urls;
    }

    public void trackers(Set<String> trackers) {
        List<AnnounceEntry> list = new ArrayList<>(trackers.size());

        for (String url : trackers) {
            list.add(new AnnounceEntry(url));
        }

        th.replaceTrackers(list);

        doResumeData(true);
    }

    @Override
    public List<TransferItem> getItems() {
        ArrayList<TransferItem> items = new ArrayList<>();

        if (th.isValid()) {
            TorrentInfo ti = th.torrentFile();
            if (ti != null && ti.isValid()) {
                FileStorage fs = ti.files();
                int numFiles = ti.numFiles();

                for (int i = 0; i < numFiles; i++) {
                    BTDownloadItem item = new BTDownloadItem(th, i, fs.filePath(i), fs.fileSize(i), piecesTracker);
                    items.add(item);
                }

                if (piecesTracker != null) {
                    int numPieces = ti.numPieces();

                    // perform piece complete check
                    for (int i = 0; i < numPieces; i++) {
                        if (th.havePiece(i)) {
                            piecesTracker.setComplete(i, true);
                        }
                    }
                }
            }
        }

        return items;
    }

    public File getTorrentFile() {
        return engine.readTorrentPath(this.getInfoHash());
    }

    public Set<File> getIncompleteFiles() {
        Set<File> s = new HashSet<>();

        try {
            if (!th.isValid()) {
                return s;
            }

            long[] progress = th.fileProgress(TorrentHandle.FileProgressFlags.PIECE_GRANULARITY);

            TorrentInfo ti = th.torrentFile();
            if (ti == null) {
                // still downloading the info (from magnet)
                return s;
            }
            FileStorage fs = ti.files();
            String prefix = savePath.getAbsolutePath();

            long createdTime = created.getTime();

            for (int i = 0; i < progress.length; i++) {
                String fePath = fs.filePath(i);
                long feSize = fs.fileSize(i);

                if (progress[i] < feSize) {
                    // lets see if indeed the file is incomplete
                    File f = new File(prefix, fePath);

                    if (!f.exists()) {
                        continue; // nothing to do here
                    }

                    if (f.lastModified() >= createdTime) {
                        // we have a file modified (supposedly) by this transfer
                        s.add(f);
                    }
                }
            }
        } catch (Throwable e) {
            LOG.error("Error calculating the incomplete files set", e);
        }

        return s;
    }

    public boolean isSequentialDownload() {
        return th.status().isSequentialDownload();
    }

    public void setSequentialDownload(boolean sequential) {
        th.setSequentialDownload(sequential);
    }

    public File partsFile() {
        return parts;
    }

    private PaymentOptions loadPaymentOptions(TorrentInfo ti) {
        try {
            BTInfoAdditionalMetadataHolder holder = new BTInfoAdditionalMetadataHolder(ti, getDisplayName());
            return holder.getPaymentOptions();
        } catch (Throwable e) {
            return null;
        }
    }

    private void serializeResumeData(SaveResumeDataAlert alert) {
        try {
            if (th.isValid()) {
                String infoHash = th.infoHash().toString();
                File file = engine.resumeDataFile(infoHash);

                Entry e = alert.resumeData();
                e.swig().dict().set(EXTRA_DATA_KEY, Entry.fromMap(extra).swig());

                FileUtils.writeByteArrayToFile(file, e.bencode());
            }
        } catch (Throwable e) {
            LOG.warn("Error saving resume data", e);
        }
    }

    private void doResumeData(boolean force) {
        long now = System.currentTimeMillis();
        if (force || (now - lastSaveResumeTime) >= SAVE_RESUME_RESOLUTION_MILLIS) {
            lastSaveResumeTime = now;
        } else {
            // skip, too fast, see SAVE_RESUME_RESOLUTION_MILLIS
            return;
        }
        try {
            if (th != null && th.isValid()) {
                th.saveResumeData();
            }
        } catch (Throwable e) {
            LOG.warn("Error triggering resume data", e);
        }
    }

    private Map<String, String> createExtra() {
        Map<String, String> map = new HashMap<>();

        try {
            String infoHash = getInfoHash();
            File file = engine.resumeDataFile(infoHash);

            if (file.exists()) {
                byte[] arr = FileUtils.readFileToByteArray(file);
                entry e = entry.bdecode(Vectors.bytes2byte_vector(arr));
                string_entry_map d = e.dict();

                if (d.has_key(EXTRA_DATA_KEY)) {
                    readExtra(d.get(EXTRA_DATA_KEY).dict(), map);
                }
            }

        } catch (Throwable e) {
            LOG.error("Error reading extra data from resume file", e);
        }

        return map;
    }

    private void readExtra(string_entry_map dict, Map<String, String> map) {
        string_vector keys = dict.keys();
        int size = (int) keys.size();
        for (int i = 0; i < size; i++) {
            String k = keys.get(i);
            entry e = dict.get(k);
            if (e.type() == entry.data_type.string_t) {
                map.put(k, e.string());
            }
        }
    }

    public boolean wasPaused() {
        boolean flag = false;
        if (extra.containsKey(WAS_PAUSED_EXTRA_KEY)) {
            try {
                flag = Boolean.parseBoolean(extra.get(WAS_PAUSED_EXTRA_KEY));
            } catch (Throwable e) {
                // ignore
            }
        }

        return flag;
    }

    private void fireRemoved(Set<File> incompleteFiles) {
        if (listener != null) {
            try {
                listener.removed(this, incompleteFiles);
            } catch (Throwable e) {
                LOG.error("Error calling listener", e);
            }
        }
    }

    private final class InnerListener implements AlertListener {

        @Override
        public int[] types() {
            return ALERT_TYPES;
        }

        @Override
        public void alert(Alert<?> alert) {
            if (!(alert instanceof TorrentAlert<?>)) {
                return;
            }

            if (!((TorrentAlert<?>) alert).handle().swig().op_eq(th.swig())) {
                return;
            }

            AlertType type = alert.type();

            switch (type) {
                case TORRENT_FINISHED:
                    torrentFinished();
                    break;
                case TORRENT_REMOVED:
                    torrentRemoved();
                    break;
                case TORRENT_CHECKED:
                    torrentChecked();
                    break;
                case SAVE_RESUME_DATA:
                    serializeResumeData((SaveResumeDataAlert) alert);
                    break;
                case PIECE_FINISHED:
                    pieceFinished((PieceFinishedAlert) alert);
                    doResumeData(false);
                    break;
                case STORAGE_MOVED:
                    doResumeData(true);
                    break;
            }
        }
    }
}
