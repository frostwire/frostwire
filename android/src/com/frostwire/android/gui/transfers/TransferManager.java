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

package com.frostwire.android.gui.transfers;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.StatFs;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.NetworkManager;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineAdapter;
import com.frostwire.logging.Logger;
import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.ScrapedTorrentFileSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.soundcloud.SoundcloudSearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class TransferManager {

    private static final Logger LOG = Logger.getLogger(TransferManager.class);

    private final List<DownloadTransfer> httpDownloads;
    private final List<BittorrentDownload> bittorrentDownloads;

    private int downloadsToReview;

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

        this.httpDownloads = new CopyOnWriteArrayList<DownloadTransfer>();
        this.bittorrentDownloads = new CopyOnWriteArrayList<BittorrentDownload>();

        this.downloadsToReview = 0;

        loadTorrents();
    }

    public List<Transfer> getTransfers() {
        List<Transfer> transfers = new ArrayList<Transfer>();

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
            for (DownloadTransfer dt : httpDownloads) {
                if (dt.isDownloading()) {
                    if (dt.getDetailsUrl() != null && dt.getDetailsUrl().equals(detailsUrl)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isDownloadingTorrentByUri(String uri) {
        synchronized (alreadyDownloadingMonitor) {
            for (DownloadTransfer dt : httpDownloads) {
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

    public DownloadTransfer download(SearchResult sr) {
        DownloadTransfer transfer = null;

        if (isBittorrentSearchResultAndMobileDataSavingsOn(sr)) {
            return new InvalidBittorrentDownload(R.string.torrent_transfer_aborted_on_mobile_data);
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
                    if (bd.isResumable()) {
                        bd.cancel();
                    }
                } else {
                    transfer.cancel();
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
        for (DownloadTransfer d : httpDownloads) {
            if (!d.isComplete() && d.isDownloading()) {
                count++;
            }
        }
        return count;
    }

    public int getActiveUploads() {
        int count = 0;
        for (BittorrentDownload d : bittorrentDownloads) {
            if (!d.isComplete() && d.isSeeding()) {
                count++;
            }
        }
        return count;
    }

    public long getDownloadsBandwidth() {
        long torrentDownloadsBandwidth = BTEngine.getInstance().getDownloadRate();
        long peerDownloadsBandwidth = 0;
        for (DownloadTransfer d : httpDownloads) {
            peerDownloadsBandwidth += d.getDownloadSpeed();
        }
        return torrentDownloadsBandwidth + peerDownloadsBandwidth;
    }

    public double getUploadsBandwidth() {
        return BTEngine.getInstance().getUploadRate();
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

        BTEngine engine = BTEngine.getInstance();

        engine.setListener(new BTEngineAdapter() {
            @Override
            public void downloadAdded(BTEngine engine, BTDownload dl) {
                String name = dl.getName();
                if (name != null && name.contains("fetch_magnet:")) {
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
                                if (bt.getHash().equals(dl.getInfoHash())) {
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

        engine.restoreDownloads();
    }

    boolean remove(Transfer transfer) {
        if (transfer instanceof BittorrentDownload) {
            return bittorrentDownloads.remove(transfer);
        } else if (transfer instanceof DownloadTransfer) {
            return httpDownloads.remove(transfer);
        }
        return false;
    }

    public void pauseTorrents() {
        for (BittorrentDownload d : bittorrentDownloads) {
            d.pause();
        }
    }

    public BittorrentDownload downloadTorrent(String uri) {
        String url = uri.trim();
        try {
            if (url.contains("urn%3Abtih%3A")) {
                //fixes issue #129: over-encoded url coming from intent
                url = url.replace("urn%3Abtih%3A", "urn:btih:");
            }

            URI u = URI.create(url);

            BittorrentDownload download = null;

            if (u.getScheme().equalsIgnoreCase("file")) {
                BTEngine.getInstance().download(new File(u.getPath()), null);
            } else if (u.getScheme().equalsIgnoreCase("http") || u.getScheme().equalsIgnoreCase("magnet")) {
                if (!isDownloadingTorrentByUri(url)) {
                    download = new TorrentFetcherDownload(this, new TorrentUrlInfo(u.toString()));
                    bittorrentDownloads.add(download);
                }
            } else {
                download = new InvalidBittorrentDownload(R.string.torrent_scheme_download_not_supported);
            }

            return download;
        } catch (Throwable e) {
            LOG.warn("Error creating download from uri: " + url);
            return new InvalidBittorrentDownload(R.string.empty_string);
        }
    }

    private static BittorrentDownload createBittorrentDownload(TransferManager manager, TorrentSearchResult sr) {
        if (sr instanceof TorrentCrawledSearchResult) {
            BTEngine.getInstance().download((TorrentCrawledSearchResult) sr, null);
        } else if (sr instanceof ScrapedTorrentFileSearchResult) {
            return new TorrentFetcherDownload(manager, new TorrentSearchResultInfo(sr, ((ScrapedTorrentFileSearchResult) sr).getReferrerUrl()));
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
            return null;
        } catch (Throwable e) {
            LOG.warn("Error creating download from search result: " + sr);
            return new InvalidBittorrentDownload(R.string.empty_string);
        }
    }

    private HttpDownload newHttpDownload(HttpSlideSearchResult sr) {
        HttpDownload download = new HttpDownload(this, sr.getDownloadLink());

        httpDownloads.add(download);
        download.start();

        return download;
    }

    private DownloadTransfer newYouTubeDownload(YouTubeCrawledSearchResult sr) {
        YouTubeDownload download = new YouTubeDownload(this, sr);

        httpDownloads.add(download);
        download.start();

        return download;
    }

    private DownloadTransfer newSoundcloudDownload(SoundcloudSearchResult sr) {
        SoundcloudDownload download = new SoundcloudDownload(this, sr);

        httpDownloads.add(download);
        download.start();

        return download;
    }

    private DownloadTransfer newHttpDownload(HttpSearchResult sr) {
        HttpDownload download = new HttpDownload(this, new HttpSearchResultDownloadLink(sr));

        httpDownloads.add(download);
        download.start();

        return download;
    }

    private boolean isBittorrentDownload(DownloadTransfer transfer) {
        return transfer instanceof UIBittorrentDownload || transfer instanceof TorrentFetcherDownload;
    }

    public boolean isBittorrentSearchResultAndMobileDataSavingsOn(SearchResult sr) {
        return sr instanceof TorrentSearchResult &&
                NetworkManager.instance().isDataMobileUp() &&
                !ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA);
    }


    public boolean isBittorrentDownloadAndMobileDataSavingsOn(DownloadTransfer transfer) {
        return isBittorrentDownload(transfer) &&
                NetworkManager.instance().isDataMobileUp() &&
                !ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA);
    }

    public boolean isBittorrentDownloadAndMobileDataSavingsOff(DownloadTransfer transfer) {
        return isBittorrentDownload(transfer) &&
                NetworkManager.instance().isDataMobileUp() &&
                ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA);
    }

    public boolean isBittorrentDisconnected() {
        return Engine.instance().isStopped() || Engine.instance().isStopping() || Engine.instance().isDisconnected();
    }

    public void resumeResumableTransfers() {
        List<Transfer> transfers = getTransfers();

        for (Transfer t : transfers) {
            if (t instanceof BittorrentDownload) {
                BittorrentDownload bt = (BittorrentDownload) t;
                if (bt.isResumable()) {
                    bt.resume();
                }
            } else if (t instanceof HttpDownload) {
                if (t.getDetailsUrl().contains("archive.org")) {
                    if (!t.isComplete() && !((HttpDownload) t).isDownloading()) {
                        ((HttpDownload) t).start(true);
                    }
                }
            }
        }
    }

    /**
     * Stops all HttpDownloads (Cloud and Wi-Fi)
     */
    public void stopHttpTransfers() {
        List<Transfer> transfers = new ArrayList<Transfer>();
        transfers.addAll(httpDownloads);
        for (Transfer t : transfers) {
            if (t instanceof DownloadTransfer && !t.isComplete() && ((DownloadTransfer)t).isDownloading()) {
                t.cancel();
            }
        }
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
                    e.setDownloadSpeedLimit((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_UPLOAD_SPEED)) {
                    e.setUploadSpeedLimit((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_DOWNLOADS)) {
                    e.setMaxActiveDownloads((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_UPLOADS)) {
                    e.setMaxActiveSeeds((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_TOTAL_CONNECTIONS)) {
                    e.setMaxConnections((int) ConfigurationManager.instance().getLong(key));
                } else if (key.equals(Constants.PREF_KEY_TORRENT_MAX_PEERS)) {
                    e.setMaxPeers((int) ConfigurationManager.instance().getLong(key));
                }
            }
        };
        ConfigurationManager.instance().registerOnPreferenceChange(preferenceListener);
    }
}
