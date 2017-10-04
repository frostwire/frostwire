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

package com.frostwire.android.gui.transfers;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineAdapter;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.transfers.BittorrentDownload;
import com.frostwire.transfers.HttpDownload;
import com.frostwire.transfers.SoundcloudDownload;
import com.frostwire.transfers.Transfer;
import com.frostwire.transfers.YouTubeDownload;
import com.frostwire.util.Logger;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TransferManager {

    private static final Logger LOG = Logger.getLogger(TransferManager.class);

    private final List<Transfer> httpDownloads;
    private final List<BittorrentDownload> bittorrentDownloads;
    private int downloadsToReview;
    private int startedTransfers = 0;
    private final Object alreadyDownloadingMonitor = new Object();
    private volatile static TransferManager instance;

    public static TransferManager instance() {
        if (instance == null) {
            instance = new TransferManager();
        }
        return instance;
    }

    private TransferManager() {
        registerPreferencesChangeListener();
        this.httpDownloads = new CopyOnWriteArrayList<>();
        this.bittorrentDownloads = new CopyOnWriteArrayList<>();
        this.downloadsToReview = 0;
        loadTorrents();
    }

    /**
     * Is it using the SD Card's private (non-persistent after uninstall) app folder to save
     * downloaded files?
     */
    public static boolean isUsingSDCardPrivateStorage() {
        String primaryPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        String currentPath = ConfigurationManager.instance().getStoragePath();

        return !primaryPath.equals(currentPath);
    }

    public List<Transfer> getTransfers() {
        List<Transfer> transfers = new ArrayList<>();

        if (httpDownloads != null) {
            transfers.addAll(httpDownloads);
        }

        if (bittorrentDownloads != null) {
            transfers.addAll(bittorrentDownloads);
        }

        return transfers;
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
            return new InvalidBittorrentDownload(R.string.torrent_transfer_aborted_on_mobile_data);
        }

        if (isMobileAndDataSavingsOn()) {
            return new InvalidDownload(R.string.cloud_download_aborted_on_mobile_data);
        }

        if (alreadyDownloading(sr.getDetailsUrl())) {
            transfer = new ExistingDownload();
        }

        if (sr instanceof TorrentSearchResult) {
            transfer = newBittorrentDownload((TorrentSearchResult) sr);
        } else if (sr instanceof HttpSlideSearchResult) {
            transfer = newHttpDownload((HttpSlideSearchResult) sr);
        } else if (sr instanceof YouTubeCrawledSearchResult) {
            transfer = newYouTubeDownload((YouTubeCrawledSearchResult) sr);
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
            if (transfer != null && transfer.isComplete()) {
                if (transfer instanceof BittorrentDownload) {
                    BittorrentDownload bd = (BittorrentDownload) transfer;
                    if (bd.isPaused()) {
                        bd.remove(false);
                    }
                } else {
                    transfer.remove(false);
                }
            }
        }
    }

    public int getActiveDownloads() {
        int count = 0;
        for (BittorrentDownload d : bittorrentDownloads) {
            if (!d.isComplete() && d.isDownloading()) {
                count++;
            }
        }
        for (Transfer d : httpDownloads) {
            if (!d.isComplete() && d.isDownloading()) {
                count++;
            }
        }
        return count;
    }

    public int getActiveUploads() {
        int count = 0;
        for (BittorrentDownload d : bittorrentDownloads) {
            if (d.isFinished() && !d.isPaused()) {
                count++;
            }
        }
        return count;
    }

    public long getDownloadsBandwidth() {
        long torrentDownloadsBandwidth = BTEngine.getInstance().downloadRate();
        long peerDownloadsBandwidth = 0;
        for (Transfer d : httpDownloads) {
            peerDownloadsBandwidth += d.getDownloadSpeed();
        }
        return torrentDownloadsBandwidth + peerDownloadsBandwidth;
    }

    public double getUploadsBandwidth() {
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
        for (BittorrentDownload d : bittorrentDownloads) {
            if (d.isSeeding() || d.isComplete()) {
                d.pause();
            }
        }
    }

    public void loadTorrents() {
        bittorrentDownloads.clear();

        final BTEngine engine = BTEngine.getInstance();

        engine.setListener(new BTEngineAdapter() {
            @Override
            public void downloadAdded(BTEngine engine, BTDownload dl) {
                String name = dl.getName();
                if (name != null && name.contains("fetch_magnet")) {
                    return;
                }

                File savePath = dl.getSavePath();
                if (savePath != null && savePath.toString().contains("fetch_magnet")) {
                    return;
                }

                bittorrentDownloads.add(new UIBittorrentDownload(TransferManager.this, dl));
            }

            @Override
            public void downloadUpdate(BTEngine engine, BTDownload dl) {
                try {
                    int count = bittorrentDownloads.size();
                    for (int i = 0; i < count; i++) {
                        if (i < bittorrentDownloads.size()) {
                            BittorrentDownload download = bittorrentDownloads.get(i);
                            if (download instanceof UIBittorrentDownload) {
                                UIBittorrentDownload bt = (UIBittorrentDownload) download;
                                if (bt.getInfoHash().equals(dl.getInfoHash())) {
                                    bt.updateUI(dl);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable e) {
                    LOG.error("Error updating bittorrent download", e);
                }
            }
        });

        Engine.instance().getThreadPool().execute(new RestoreDownloadsTask());
    }

    public boolean remove(Transfer transfer) {
        if (transfer instanceof BittorrentDownload) {
            return bittorrentDownloads.remove(transfer);
        } else if (transfer instanceof Transfer) {
            return httpDownloads.remove(transfer);
        }
        return false;
    }

    public void pauseTorrents() {
        for (BittorrentDownload d : bittorrentDownloads) {
            if (!d.isSeeding()) {
                d.pause();
            }
        }
    }

    public BittorrentDownload downloadTorrent(String uri) {
        return downloadTorrent(uri, null, null);
    }

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

            if (!u.getScheme().equalsIgnoreCase("file") &&
                    !u.getScheme().equalsIgnoreCase("http") &&
                    !u.getScheme().equalsIgnoreCase("https") &&
                    !u.getScheme().equalsIgnoreCase("magnet")) {
                LOG.warn("Invalid URI scheme: " + u.toString());
                return new InvalidBittorrentDownload(R.string.torrent_scheme_download_not_supported);
            }

            BittorrentDownload download = null;

            if (fetcherListener == null) {
                if (u.getScheme().equalsIgnoreCase("file")) {
                    BTEngine.getInstance().download(new File(u.getPath()), null, null);
                } else if (u.getScheme().equalsIgnoreCase("http") || u.getScheme().equalsIgnoreCase("https") || u.getScheme().equalsIgnoreCase("magnet")) {
                    download = new TorrentFetcherDownload(this, new TorrentUrlInfo(u.toString(), tempDownloadTitle));
                    bittorrentDownloads.add(download);
                }
            } else {
                if (u.getScheme().equalsIgnoreCase("file")) {
                    fetcherListener.onTorrentInfoFetched(FileUtils.readFileToByteArray(new File(u.getPath())), null, -1);
                } else if (u.getScheme().equalsIgnoreCase("http") || u.getScheme().equalsIgnoreCase("https") || u.getScheme().equalsIgnoreCase("magnet")) {
                    // this executes the listener method when it fetches the bytes.
                    download = new TorrentFetcherDownload(this, new TorrentUrlInfo(u.toString(), tempDownloadTitle), fetcherListener);
                    bittorrentDownloads.add(download);
                    return download;
                }
                return null;
            }

            return download;
        } catch (Throwable e) {
            LOG.warn("Error creating download from uri: " + url);
            return new InvalidBittorrentDownload(R.string.torrent_scheme_download_not_supported);
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
                bittorrentDownloads.add(bittorrentDownload);
            }
            return bittorrentDownload;
        } catch (Throwable e) {
            LOG.warn("Error creating download from search result: " + sr);
            return new InvalidBittorrentDownload(R.string.empty_string);
        }
    }

    private HttpDownload newHttpDownload(HttpSlideSearchResult sr) {
        HttpDownload download = new UIHttpDownload(this, sr.slide());

        httpDownloads.add(download);
        download.start();

        return download;
    }

    private Transfer newYouTubeDownload(YouTubeCrawledSearchResult sr) {
        YouTubeDownload download = new UIYouTubeDownload(this, sr);

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
        NetworkManager networkManager = NetworkManager.instance();
        return networkManager.isDataMobileUp(networkManager.getConnectivityManager()) &&
                ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_WIFI_ONLY);
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

    public static boolean isResumable(BittorrentDownload bt) {
        // torrents that are finished because seeding is
        // not enabled, are actually paused
        if (bt.isFinished()) {
            if (!ConfigurationManager.instance().isSeedFinishedTorrents()) {
                // this implies !isSeedingEnabledOnlyForWifi
                return false;
            }
            boolean isSeedingEnabledOnlyForWifi = ConfigurationManager.instance().isSeedingEnabledOnlyForWifi();
            // TODO: find a better way to express relationship with isSeedingEnabled
            NetworkManager networkManager = NetworkManager.instance();
            if (isSeedingEnabledOnlyForWifi && !networkManager.isDataWIFIUp(networkManager.getConnectivityManager())) {
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
                } else if (t instanceof HttpDownload) {
                    // TODO: review this feature taking care of the SD limitations
                /*if (t.getName().contains("archive.org")) {
                    if (!t.isComplete() && !((HttpDownload) t).isDownloading()) {
                        ((HttpDownload) t).start(true);
                    }
                }*/
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
                } else if (t instanceof HttpDownload) {
                    // TODO: review this feature taking care of the SD limitations
                /*if (t.getName().contains("archive.org")) {
                    if (!t.isComplete() && !((HttpDownload) t).isDownloading()) {
                        ((HttpDownload) t).start(true);
                    }
                }*/
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
        List<Transfer> transfers = new ArrayList<>();
        transfers.addAll(httpDownloads);
        for (Transfer t : transfers) {
            if (t instanceof Transfer && !t.isComplete() && t.isDownloading()) {
                t.remove(false);
            }
        }
    }

    public int getStartedTransfers() {
        return startedTransfers;
    }

    public int incrementStartedTransfers() {
        return ++startedTransfers;
    }

    public void resetStartedTransfers() {
        startedTransfers = 0;
    }

    /**
     * @return true if less than 10MB available
     */
    static boolean isCurrentMountAlmostFull() {
        return getCurrentMountAvailableBytes() < 10000000;
    }

    static long getCurrentMountAvailableBytes() {
        StatFs stat = new StatFs(ConfigurationManager.instance().getStoragePath());
        return ((long) stat.getBlockSize() * (long) stat.getAvailableBlocks());
    }

    private void registerPreferencesChangeListener() {
        OnSharedPreferenceChangeListener preferenceListener = new OnSharedPreferenceChangeListener() {
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                BTEngine e = BTEngine.getInstance();

                if (key.equals(Constants.PREF_KEY_TORRENT_MAX_DOWNLOAD_SPEED)) {
                    e.downloadRateLimit((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED)) {
                    e.uploadRateLimit((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS)) {
                    e.maxActiveDownloads((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_UPLOADS)) {
                    e.maxActiveSeeds((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS)) {
                    e.maxConnections((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_PEERS)) {
                    e.maxPeers((int) ConfigurationManager.instance().getLong(key));
                }
            }
        };
        ConfigurationManager.instance().registerOnPreferenceChange(preferenceListener);
    }

    private static final class RestoreDownloadsTask implements Runnable {

        @Override
        public void run() {
            BTEngine.getInstance().restoreDownloads();
        }
    }
}
