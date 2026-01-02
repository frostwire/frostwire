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

package com.frostwire.android.gui.transfers;

import static com.frostwire.android.util.SystemUtils.postToHandler;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.StatFs;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineAdapter;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.SoundcloudDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TransferManager {

    private static final Logger LOG = Logger.getLogger(TransferManager.class);

    private final List<Transfer> httpDownloads;
    private final List<BittorrentDownload> bittorrentDownloadsList;
    private final Map<String, BittorrentDownload> bittorrentDownloadsMap;
    private int downloadsToReview;
    private int startedTransfers = 0;
    private final Object alreadyDownloadingMonitor = new Object();
    private final Object downloadsListMonitor = new Object();
    private final Object downloadsMapMonitor = new Object();
    private static final Object instanceLock = new Object();
    private final SharedPreferences.OnSharedPreferenceChangeListener onSharedPreferenceChangeListener;
    private volatile static TransferManager instance;


    public static TransferManager instance() {
        if (instance == null) {
            synchronized (instanceLock) {
                instance = new TransferManager();
            }
        }
        return instance;
    }

    private TransferManager() {
        onSharedPreferenceChangeListener = (sharedPreferences, key) -> onPreferenceChanged(key);
        registerPreferencesChangeListener();
        this.httpDownloads = new CopyOnWriteArrayList<>();
        this.bittorrentDownloadsList = new CopyOnWriteArrayList<>();
        this.bittorrentDownloadsMap = new HashMap<>(0);
        this.downloadsToReview = 0;
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, this::loadTorrentsTask);
    }

    public void reset() {
        registerPreferencesChangeListener();
        clearTransfers();
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER, this::loadTorrentsTask);

    }

    public void onShutdown(boolean disconnected) {
        if (!disconnected) {
            clearTransfers();
        }
        unregisterPreferencesChangeListener();
    }

    public void forceReannounceTorrents() {
        synchronized (downloadsListMonitor) {
            for (BittorrentDownload d : bittorrentDownloadsList) {
                if (d instanceof BTDownload) {
                    BTDownload bt = (BTDownload) d;
                    bt.getTorrentHandle().forceReannounce(0, -1, TorrentHandle.IGNORE_MIN_INTERVAL);
                }
            }
        }
    }

    private void clearTransfers() {
        this.httpDownloads.clear();
        synchronized (downloadsListMonitor) {
            this.bittorrentDownloadsList.clear();
        }
        synchronized (downloadsMapMonitor) {
            this.bittorrentDownloadsMap.clear();
        }
        this.downloadsToReview = 0;
    }

//    /**
//     * Is it using the SD Card's private (non-persistent after uninstall) app folder to save
//     * downloaded files?
//     */
//    public static boolean isUsingSDCardPrivateStorage() {
//        String primaryPath = Environment.getExternalStorageDirectory().getAbsolutePath();
//        String currentPath = ConfigurationManager.instance().getStoragePath();
//
//        return !primaryPath.equals(currentPath);
//    }

    public List<Transfer> getTransfers() {
        List<Transfer> transfers = new ArrayList<>();

        if (httpDownloads != null) {
            transfers.addAll(httpDownloads);
        }

        if (bittorrentDownloadsList != null) {
            transfers.addAll(bittorrentDownloadsList);
        }

        return transfers;
    }

    public BittorrentDownload getBittorrentDownload(String infoHash) {
        return bittorrentDownloadsMap.get(infoHash);
    }

    private boolean alreadyDownloading(String detailsUrl) {
        synchronized (alreadyDownloadingMonitor) {
            for (Transfer dt : httpDownloads) {
                if (dt.isDownloading()) {
                    if (dt.getName() != null && dt.getName().equals(detailsUrl)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isAlreadyDownloadingTorrentByUri(String uri) {
        synchronized (alreadyDownloadingMonitor) {
            for (Transfer dt : httpDownloads) {
                if (dt instanceof TorrentFetcherDownload) {
                    String torrentUri = ((TorrentFetcherDownload) dt).getTorrentUri();
                    if (torrentUri != null && torrentUri.equals(uri)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Transfer download(SearchResult sr) {
        Transfer transfer = null;

        if (isBittorrentSearchResultAndMobileDataSavingsOn(sr)) {
            return new InvalidBittorrentDownload(R.string.torrent_transfer_aborted_on_mobile_data, sr);
        }

        if (isMobileAndDataSavingsOn()) {
            return new InvalidDownload(R.string.cloud_download_aborted_on_mobile_data, sr);
        }

        if (alreadyDownloading(sr.getDetailsUrl())) {
            transfer = new ExistingDownload();
        } else {
            incrementStartedTransfers();
        }

        if (sr instanceof TorrentSearchResult) {
            transfer = newBittorrentDownload((TorrentSearchResult) sr);
        } else if (sr instanceof HttpSlideSearchResult) {
            transfer = newHttpDownload((HttpSlideSearchResult) sr);
        } else if (sr instanceof SoundcloudSearchResult) {
            transfer = newSoundcloudDownload((SoundcloudSearchResult) sr);
        } else if (sr instanceof HttpSearchResult) {
            transfer = newHttpDownload((HttpSearchResult) sr);
        }

        return transfer;
    }

    public void clearComplete() {
        List<Transfer> transfers = getTransfers();
        for (Transfer transfer : transfers) {
            if (transfer == null) {
                continue;
            }

            if (!(transfer instanceof BittorrentDownload) && transfer.isComplete()) {
                transfer.remove(false);
            } else if (transfer instanceof BittorrentDownload) {
                BittorrentDownload bd = (BittorrentDownload) transfer;
                boolean isFinished = bd.isComplete() && bd.isPaused();
                boolean isErrored = TransferState.isErrored(bd.getState());
                if (isFinished || isErrored) {
                    bd.remove(false);
                }
            }
        }
    }

    public int getActiveDownloads() {
        int count = 0;
        synchronized (downloadsListMonitor) {
            for (BittorrentDownload d : bittorrentDownloadsList) {
                if (!TransferState.isErrored(d.getState()) && !d.isComplete() && d.isDownloading()) {
                    count++;
                }
            }
        }
        for (Transfer d : httpDownloads) {
            if (!TransferState.isErrored(d.getState()) && !d.isComplete() && d.isDownloading()) {
                count++;
            }
        }
        return count;
    }

    public int getActiveUploads() {
        int count = 0;
        synchronized (downloadsListMonitor) {
            for (BittorrentDownload d : bittorrentDownloadsList) {
                if (!TransferState.isErrored(d.getState()) && d.isFinished() && !d.isPaused()) {
                    count++;
                }
            }
        }
        return count;
    }

    public long getDownloadsBandwidth() {
        if (BTEngine.ctx == null) {
            // too early
            return 0;
        }
        long torrentDownloadsBandwidth = BTEngine.getInstance().downloadRate();
        long peerDownloadsBandwidth = 0;
        for (Transfer d : httpDownloads) {
            peerDownloadsBandwidth += d.getDownloadSpeed();
        }
        return torrentDownloadsBandwidth + peerDownloadsBandwidth;
    }

    public double getUploadsBandwidth() {
        if (BTEngine.ctx == null) {
            // too early
            return 0;
        }
        return BTEngine.getInstance().uploadRate();
    }

    public int getDownloadsToReview() {
        return downloadsToReview;
    }

    public void incrementDownloadsToReview() {
        downloadsToReview++;
    }

    public void clearDownloadsToReview() {
        downloadsToReview = 0;
    }

    public void stopSeedingTorrents() {
        synchronized (downloadsListMonitor) {
            for (BittorrentDownload d : bittorrentDownloadsList) {
                if (d.isSeeding() || d.isComplete()) {
                    d.pause();
                }
            }
        }
    }

    public boolean remove(Transfer transfer) {
        if (transfer instanceof BittorrentDownload) {
            synchronized (downloadsMapMonitor) {
                bittorrentDownloadsMap.remove(((BittorrentDownload) transfer).getInfoHash());
            }
            boolean removed;
            synchronized (downloadsListMonitor) {
                removed = bittorrentDownloadsList.remove(transfer);
            }
            return removed;
        } else if (transfer != null) {
            return httpDownloads.remove(transfer);
        }
        return false;
    }

    public void pauseTorrents() {
        synchronized (downloadsListMonitor) {
            for (BittorrentDownload d : bittorrentDownloadsList) {
                if (!d.isSeeding()) {
                    d.pause();
                }
            }
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public BittorrentDownload downloadTorrent(String uri, TorrentFetcherListener fetcherListener) {
        return downloadTorrent(uri, fetcherListener, null);
    }

    public BittorrentDownload downloadTorrent(String uri, TorrentFetcherListener fetcherListener, String tempDownloadTitle) {
        String url = uri.trim();
        try {
            if (url.contains("urn%3Abtih%3A")) {
                //fixes issue #129: over-encoded url coming from intent
                url = url.replace("urn%3Abtih%3A", "urn:btih:");
            }

            if (isAlreadyDownloadingTorrentByUri(url)) {
                return null;
            }

            Uri u = Uri.parse(url);
            String scheme = u.getScheme();
            if (scheme != null && !scheme.equalsIgnoreCase("file") && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https") && !scheme.equalsIgnoreCase("magnet")) {
                LOG.warn("Invalid URI scheme: " + u);
                return new InvalidBittorrentDownload(R.string.torrent_scheme_download_not_supported, null);
            }

            BittorrentDownload download = null;

            if (fetcherListener == null) {
                if (scheme != null && scheme.equalsIgnoreCase("file")) {
                    BTEngine.getInstance().download(new File(Objects.requireNonNull(u.getPath())), null, null);
                } else if (scheme != null && scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("magnet")) {
                    download = new TorrentFetcherDownload(this, new TorrentUrlInfo(u.toString(), tempDownloadTitle));
                    synchronized (downloadsListMonitor) {
                        bittorrentDownloadsList.add(download);
                    }
                    synchronized (downloadsMapMonitor) {
                        bittorrentDownloadsMap.put(download.getInfoHash(), download);
                    }
                }
            } else {
                if (scheme != null && scheme.equalsIgnoreCase("file")) {
                    // download an existing transfer from a .torrent in My Files (partial download)
                    // See com.frostwire.android.gui.adapters.menu.OpenMenuAction::onClick()
                    fetcherListener.onTorrentInfoFetched(FileUtils.readFileToByteArray(new File(Objects.requireNonNull(u.getPath()))), null, new Random(System.currentTimeMillis()).nextLong());
                } else if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("magnet"))) {
                    // this executes the listener method when it fetches the bytes.
                    download = new TorrentFetcherDownload(this, new TorrentUrlInfo(u.toString(), tempDownloadTitle), fetcherListener);
                    synchronized (downloadsListMonitor) {
                        bittorrentDownloadsList.add(download);
                    }
                    synchronized (downloadsMapMonitor) {
                        bittorrentDownloadsMap.put(download.getInfoHash(), download);
                    }
                    incrementStartedTransfers();
                    return download;
                }
                return null;
            }

            incrementStartedTransfers();
            return download;
        } catch (Throwable e) {
            LOG.warn("Error creating download from uri: " + url, e);
            return new InvalidBittorrentDownload(R.string.torrent_scheme_download_not_supported, null);
        }
    }

    private static BittorrentDownload createBittorrentDownload(TransferManager manager, TorrentSearchResult sr) {
        if (sr instanceof TorrentCrawledSearchResult) {
            TorrentCrawledSearchResult torrentCrawledSearchResult = (TorrentCrawledSearchResult) sr;
            BTEngine.getInstance().download(torrentCrawledSearchResult, null, manager.isDeleteStartedTorrentEnabled());
        } else if (sr.getTorrentUrl() != null) {
            return new TorrentFetcherDownload(manager, new TorrentSearchResultInfo(sr));
        }

        return null;
    }

    private BittorrentDownload newBittorrentDownload(TorrentSearchResult sr) {
        try {
            BittorrentDownload bittorrentDownload = createBittorrentDownload(this, sr);
            if (bittorrentDownload != null) {
                synchronized (downloadsListMonitor) {
                    bittorrentDownloadsList.add(bittorrentDownload);
                }
                synchronized (downloadsMapMonitor) {
                    bittorrentDownloadsMap.put(bittorrentDownload.getInfoHash(), bittorrentDownload);
                }
            }
            return bittorrentDownload;
        } catch (Throwable e) {
            LOG.warn("Error creating download from search result: " + sr);
            return new InvalidBittorrentDownload(R.string.empty_string, sr);
        }
    }

    private HttpDownload newHttpDownload(HttpSlideSearchResult sr) {
        HttpDownload download = new UIHttpDownload(this, sr.slide());

        httpDownloads.add(download);
        download.start();

        return download;
    }

    private Transfer newSoundcloudDownload(SoundcloudSearchResult sr) {
        SoundcloudDownload download = new UISoundcloudDownload(this, sr);

        httpDownloads.add(download);
        download.start();

        return download;
    }

    private Transfer newHttpDownload(HttpSearchResult sr) {
        HttpDownload download = new UIHttpDownload(this, sr);

        httpDownloads.add(download);
        download.start();

        return download;
    }

    public boolean isBittorrentDownload(Transfer transfer) {
        return transfer instanceof UIBittorrentDownload || transfer instanceof TorrentFetcherDownload;
    }

    public boolean isMobileAndDataSavingsOn() {
        return NetworkManager.instance().isDataMobileUp() && ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
    }

    public boolean isBittorrentSearchResultAndMobileDataSavingsOn(SearchResult sr) {
        return sr instanceof TorrentSearchResult && isMobileAndDataSavingsOn();
    }

    public boolean isBittorrentDownloadAndMobileDataSavingsOn(Transfer transfer) {
        return isBittorrentDownload(transfer) && isMobileAndDataSavingsOn();
    }

    public boolean isBittorrentDownloadAndMobileDataSavingsOff(Transfer transfer) {
        return isBittorrentDownload(transfer) && isMobileAndDataSavingsOn();
    }

    public boolean isBittorrentDisconnected() {
        return Engine.instance().isStopped() || Engine.instance().isStopping() || Engine.instance().isDisconnected();
    }

    public boolean isDeleteStartedTorrentEnabled() {
        return ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_TORRENT_DELETE_STARTED_TORRENT_FILES);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean isResumable(BittorrentDownload bt) {
        // torrents that are finished because seeding is
        // not enabled, are actually paused
        if (bt.isFinished()) {
            ConfigurationManager CM = ConfigurationManager.instance();
            if (!CM.isSeedFinishedTorrents()) {
                // this implies !isSeedingEnabledOnlyForWifi
                return false;
            }
            boolean isSeedingEnabledOnlyForWifi = CM.isSeedingEnabledOnlyForWifi();
            // TODO: find a better way to express relationship with isSeedingEnabled
            if (isSeedingEnabledOnlyForWifi && !NetworkManager.instance().isDataWIFIUp()) {
                return false;
            }
        }

        return bt.isPaused();
    }

    public void resumeResumableTransfers() {
        List<Transfer> transfers = getTransfers();

        if (!isMobileAndDataSavingsOn()) {
            for (Transfer t : transfers) {
                if (t instanceof BittorrentDownload) {
                    BittorrentDownload bt = (BittorrentDownload) t;

                    if (!isResumable(bt)) {
                        continue;
                    }

                    if (bt.isPaused() && !bt.isFinished()) {
                        bt.resume();
                    }
                }
            }
        }
    }

    public void seedFinishedTransfers() {
        List<Transfer> transfers = getTransfers();

        if (!isMobileAndDataSavingsOn()) {
            for (Transfer t : transfers) {
                if (t instanceof BittorrentDownload) {
                    BittorrentDownload bt = (BittorrentDownload) t;

                    if (!isResumable(bt)) {
                        continue;
                    }

                    if (bt.isFinished()) {
                        bt.resume();
                    }
                }
            }
        }
    }

    public boolean isHttpDownloadInProgress() {
        for (Transfer httpDownload : httpDownloads) {
            if (httpDownload.isDownloading()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Stops all HttpDownloads (Cloud and Wi-Fi)
     */
    public void stopHttpTransfers() {
        List<Transfer> transfers = new ArrayList<>(httpDownloads);
        for (Transfer t : transfers) {
            if (t != null && !t.isComplete() && t.isDownloading()) {
                t.remove(false);
            }
        }
    }

    public void incrementStartedTransfers() {
        ++startedTransfers;
    }

    public void resetStartedTransfers() {
        startedTransfers = 0;
    }

    public int startedTransfers() {
        return startedTransfers;
    }

    public void updateUIBittorrentDownload(TorrentHandle torrentHandle) {
        int index = 0;
        String infoHashString = torrentHandle.infoHash().toHex();
        synchronized (downloadsListMonitor) {
            for (BittorrentDownload bittorrentDownload : bittorrentDownloadsList) {
                if (bittorrentDownload.getInfoHash() != null && bittorrentDownload.getInfoHash().equals(infoHashString)) {
                    break;
                }
                index++;
            }
        }
        UIBittorrentDownload uiBtDownload = new UIBittorrentDownload(this, new BTDownload(BTEngine.getInstance(), torrentHandle));
        synchronized (downloadsListMonitor) {
            if (index >= bittorrentDownloadsList.size()) {
                bittorrentDownloadsList.add(uiBtDownload);
            } else {
                bittorrentDownloadsList.set(index, uiBtDownload);
            }
        }
        synchronized (downloadsMapMonitor) {
            bittorrentDownloadsMap.remove(infoHashString);
            bittorrentDownloadsMap.put(infoHashString, uiBtDownload);
        }
    }


    static long getCurrentMountAvailableBytes() {
        StatFs stat = new StatFs(ConfigurationManager.instance().getStoragePath());
        return (stat.getBlockSizeLong() * stat.getAvailableBlocksLong());
    }

    private void registerPreferencesChangeListener() {
        if (SystemUtils.isUIThread()) {
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> ConfigurationManager.instance().registerOnPreferenceChange(onSharedPreferenceChangeListener));
        } else {
            ConfigurationManager.instance().registerOnPreferenceChange(onSharedPreferenceChangeListener);
        }
    }

    private void unregisterPreferencesChangeListener() {
        if (SystemUtils.isUIThread()) {
            postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> ConfigurationManager.instance().unregisterOnPreferenceChange(onSharedPreferenceChangeListener));

        } else {
            ConfigurationManager.instance().unregisterOnPreferenceChange(onSharedPreferenceChangeListener);
        }
    }

    private void onPreferenceChanged(String key) {
        //LOG.info("onPreferenceChanged(key="+key+")");
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.CONFIG_MANAGER, () -> {
            BTEngine e = BTEngine.getInstance();
            ConfigurationManager CM = ConfigurationManager.instance();
            switch (key) {
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED:
                    e.downloadRateLimit((int) CM.getLong(key));
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED:
                    e.uploadRateLimit((int) CM.getLong(key));
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS:
                    e.maxActiveDownloads((int) CM.getLong(key));
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_UPLOADS:
                    e.maxActiveSeeds((int) CM.getLong(key));
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS:
                    e.maxConnections((int) CM.getLong(key));
                    break;
                case Constants.PREF_KEY_TORRENT_MAX_PEERS:
                    e.maxPeers((int) CM.getLong(key));
                    break;
            }
        });

    }

    private void loadTorrentsTask() {
        synchronized (downloadsListMonitor) {
            bittorrentDownloadsList.clear();
        }
        synchronized (downloadsMapMonitor) {
            bittorrentDownloadsMap.clear();
        }

        UIBittorrentDownload.SEQUENTIAL_DOWNLOADS = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_TORRENT_SEQUENTIAL_TRANSFERS_ENABLED);

        final BTEngine btEngine = BTEngine.getInstance();
        btEngine.setListener(new BTEngineAdapter() {
            @Override
            public void downloadAdded(BTEngine engine, BTDownload dl) {
                if (dl.getInfoHash() == null) {
                    LOG.error("BTEngineAdapter.downloadAdded()@TransferManager::loadTorrentsTask: Check your logic, BTDownload's infoHash is null");
                    return;
                }
                String name = dl.getName();
                if (name != null && name.contains("fetch_magnet")) {
                    return;
                }
                File savePath = dl.getSavePath();
                if (savePath != null && savePath.toString().contains("fetch_magnet")) {
                    return;
                }
                if (dl.getListener() == null) {
                    dl.setListener(new UIBTDownloadListener());
                }
                UIBittorrentDownload uiBittorrentDownload = new UIBittorrentDownload(TransferManager.this, dl);
                synchronized (downloadsListMonitor) {
                    if (!bittorrentDownloadsList.contains(uiBittorrentDownload)) {
                        bittorrentDownloadsList.add(uiBittorrentDownload);
                    }
                }
                synchronized (downloadsMapMonitor) {
                    if (!bittorrentDownloadsMap.containsKey(dl.getInfoHash())) {
                        bittorrentDownloadsMap.put(dl.getInfoHash(), uiBittorrentDownload);
                    }
                }
            }

            @Override
            public void downloadUpdate(BTEngine engine, BTDownload dl) {
                try {
                    if (dl.getInfoHash() == null) {
                        LOG.error("BTEngineAdapter.downloadUpdate()@TransferManager::loadTorrentsTask: Check your logic, cannot update BTDownload with null infoHash");
                        return;
                    }
                    if (dl.getListener() == null) {
                        dl.setListener(new UIBTDownloadListener());
                    }

                    BittorrentDownload bittorrentDownload = bittorrentDownloadsMap.get(dl.getInfoHash());
                    if (bittorrentDownload instanceof UIBittorrentDownload) {
                        UIBittorrentDownload bt = (UIBittorrentDownload) bittorrentDownload;
                        bt.updateUI(dl);
                    }
                } catch (Throwable e) {
                    LOG.error("Error updating bittorrent download", e);
                }
            }
        });
        btEngine.restoreDownloads();
    }
}
