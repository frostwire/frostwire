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

import android.net.Uri;
import android.util.Log;
import com.frostwire.android.LollipopFileSystem;
import com.frostwire.android.R;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.youtube.YouTubeCrawledSearchResult;
import com.frostwire.search.youtube.YouTubeExtractor.LinkInfo;
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.MP4Muxer;
import com.frostwire.util.MP4Muxer.MP4Metadata;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.HttpClient.HttpClientListener;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author gubatron
 * @author aldenml
 */
public final class YouTubeDownload implements DownloadTransfer {

    private static final String TAG = "FW.HttpDownload";

    private static final int STATUS_DOWNLOADING = 1;
    private static final int STATUS_COMPLETE = 2;
    private static final int STATUS_ERROR = 3;
    private static final int STATUS_CANCELLED = 4;
    private static final int STATUS_WAITING = 5;
    private static final int STATUS_DEMUXING = 6;
    private static final int STATUS_SAVE_DIR_ERROR = 7;
    private static final int STATUS_ERROR_DISK_FULL = 8;

    private static final int SPEED_AVERAGE_CALCULATION_INTERVAL_MILLISECONDS = 1000;

    private final TransferManager manager;
    private final YouTubeCrawledSearchResult sr;
    private final DownloadType downloadType;

    private File completeFile;
    private final File tempVideo;
    private final File tempAudio;

    private final HttpClient httpClient;
    private final HttpClientListener httpClientListener;
    private final Date dateCreated;

    private final long size;
    private int status;
    private long bytesReceived;
    private long averageSpeed; // in bytes

    // variables to keep the download rate of file transfer
    private long speedMarkTimestamp;
    private long totalReceivedSinceLastSpeedStamp;

    YouTubeDownload(TransferManager manager, YouTubeCrawledSearchResult sr) {
        this.manager = manager;
        this.sr = sr;
        this.downloadType = buildDownloadType(sr);
        this.size = sr.getSize();

        String filename = sr.getFilename();

        File tempDir = Platforms.temp();
        File savePath = tempDir;

        if (!ensureDirectoryExits(tempDir)) {
            this.status = STATUS_SAVE_DIR_ERROR;
        }

        completeFile = buildFile(savePath, filename);
        tempVideo = buildTempFile(FilenameUtils.getBaseName(filename), ".temp.m4v");
        tempAudio = buildTempFile(FilenameUtils.getBaseName(filename), ".temp.m4a");

        bytesReceived = 0;
        dateCreated = new Date();

        httpClientListener = new HttpDownloadListenerImpl();

        httpClient = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
        httpClient.setListener(httpClientListener);

        if (TransferManager.isCurrentMountAlmostFull()) {
            this.status = STATUS_ERROR_DISK_FULL;
        }
    }

    static boolean ensureDirectoryExits(File dir) {
        if (dir == null || !dir.isDirectory() && !dir.mkdirs()) {
            return false;
        } else {
            return true;
        }
    }

    static File buildFile(File savePath, String name) {
        String baseName = FilenameUtils.getBaseName(name);
        String ext = FilenameUtils.getExtension(name);

        File f = new File(savePath, name);
        int i = 1;
        while (f.exists() && i < 30) {
            f = new File(savePath, baseName + " (" + i + ")." + ext);
            i++;
        }
        return f;
    }

    private static File buildTempFile(String name, String ext) {
        return new File(Platforms.temp(), name + "." + ext);
    }

    private DownloadType buildDownloadType(YouTubeCrawledSearchResult sr) {
        DownloadType dt;

        if (sr.getVideo() != null && sr.getAudio() == null) {
            dt = DownloadType.VIDEO;
        } else if (sr.getVideo() != null && sr.getAudio() != null) {
            dt = DownloadType.DASH;
        } else if (sr.getVideo() == null && sr.getAudio() != null) {
            dt = DownloadType.DEMUX;
        } else {
            throw new IllegalArgumentException("Not track specified");
        }

        return dt;
    }

    public String getDisplayName() {
        return sr.getDisplayName();
    }

    public String getStatus() {
        return getStatusString(status);
    }

    public int getProgress() {
        if (size > 0) {
            return isComplete() ? 100 : (int) ((bytesReceived * 100) / size);
        } else {
            return 0;
        }
    }

    public long getSize() {
        return size;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public long getBytesReceived() {
        return bytesReceived;
    }

    public long getBytesSent() {
        return 0;
    }

    public long getDownloadSpeed() {
        return (!isDownloading()) ? 0 : averageSpeed;
    }

    public long getUploadSpeed() {
        return 0;
    }

    public long getETA() {
        if (size > 0) {
            long speed = getDownloadSpeed();
            return speed > 0 ? (size - getBytesReceived()) / speed : Long.MAX_VALUE;
        } else {
            return 0;
        }
    }

    public boolean isComplete() {
        if (bytesReceived > 0) {
            return bytesReceived == size || status == STATUS_COMPLETE || status == STATUS_ERROR;
        } else {
            return false;
        }
    }

    public boolean isDownloading() {
        return status == STATUS_DOWNLOADING;
    }

    public List<TransferItem> getItems() {
        return Collections.emptyList();
    }

    public File getSavePath() {
        return completeFile;
    }

    public void cancel() {
        cancel(false);
    }

    public void cancel(boolean deleteData) {
        if (status != STATUS_COMPLETE) {
            status = STATUS_CANCELLED;
        }
        if (status != STATUS_COMPLETE || deleteData) {
            cleanup();
        }
        manager.remove(this);
    }

    public void start() {
        if (downloadType == DownloadType.DEMUX) {
            start(sr.getAudio(), tempAudio);
        } else {
            start(sr.getVideo(), tempVideo);
        }
    }

    @Override
    public File previewFile() {
        if (isComplete()) {
            return completeFile;
        } else {
            if (tempVideo.exists()) {
                return tempVideo;
            }

            if (tempAudio.exists()) {
                return tempAudio;
            }
        }

        return null;
    }

    private void start(final LinkInfo inf, final File temp) {
        if (status == STATUS_SAVE_DIR_ERROR || status == STATUS_ERROR_DISK_FULL || status == STATUS_ERROR) {
            return;
        }

        status = STATUS_WAITING;

        Engine.instance().getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    status = STATUS_DOWNLOADING;
                    httpClient.save(inf.link, temp, false);
                } catch (IOException e) {
                    e.printStackTrace();
                    httpClientListener.onError(httpClient, e);
                }
            }
        });
    }

    private String getStatusString(int status) {
        int resId;
        switch (status) {
            case STATUS_DOWNLOADING:
                resId = R.string.peer_http_download_status_downloading;
                break;
            case STATUS_COMPLETE:
                resId = R.string.peer_http_download_status_complete;
                break;
            case STATUS_ERROR:
                resId = R.string.peer_http_download_status_error;
                break;
            case STATUS_SAVE_DIR_ERROR:
                resId = R.string.http_download_status_save_dir_error;
                break;
            case STATUS_ERROR_DISK_FULL:
                resId = R.string.error_no_space_left_on_device;
                break;
            case STATUS_CANCELLED:
                resId = R.string.peer_http_download_status_cancelled;
                break;
            case STATUS_WAITING:
                resId = R.string.peer_http_download_status_waiting;
                break;
            case STATUS_DEMUXING:
                resId = R.string.transfer_status_demuxing;
                break;
            default:
                resId = R.string.peer_http_download_status_unknown;
                break;
        }
        return String.valueOf(resId);
    }

    private void updateAverageDownloadSpeed() {
        long now = System.currentTimeMillis();

        if (isComplete()) {
            averageSpeed = 0;
            speedMarkTimestamp = now;
            totalReceivedSinceLastSpeedStamp = 0;
        } else if (now - speedMarkTimestamp > SPEED_AVERAGE_CALCULATION_INTERVAL_MILLISECONDS) {
            averageSpeed = ((bytesReceived - totalReceivedSinceLastSpeedStamp) * 1000) / (now - speedMarkTimestamp);
            speedMarkTimestamp = now;
            totalReceivedSinceLastSpeedStamp = bytesReceived;
        }
    }

    private void complete() {
        status = STATUS_COMPLETE;
        manager.incrementDownloadsToReview();

        FileSystem fs = Platforms.get().fileSystem();
        if (fs instanceof LollipopFileSystem) {
            Uri uri = ((LollipopFileSystem) fs).getDocumentUri(Platforms.data());
            if (uri != null) {
                safComplete(fs);
            } else {
                classicComplete();
            }
        } else {
            classicComplete();
        }
    }

    private void classicComplete() {
        if (!completeFile.getAbsoluteFile().exists()) {
            this.status = STATUS_ERROR;
            cleanupIncomplete();
            return;
        }

        File dataDir = Platforms.data();
        if (!ensureDirectoryExits(dataDir)) {
            this.status = STATUS_SAVE_DIR_ERROR;
            cleanup();
            return;
        }

        File finalFile = buildFile(dataDir, completeFile.getName());

        try {
            FileUtils.moveFile(completeFile, finalFile);
            completeFile = finalFile;
            Librarian.instance().scan(getSavePath().getAbsoluteFile());
            String hash = String.valueOf(getDisplayName().hashCode());
            Engine.instance().notifyDownloadFinished(getDisplayName(), completeFile, hash);
        } catch (IOException e) {
            e.printStackTrace();
            Engine.instance().notifyDownloadFinished(getDisplayName(), getSavePath());
        }

        cleanupIncomplete();
    }

    private void safComplete(final FileSystem fs) {
        if (!completeFile.getAbsoluteFile().exists()) {
            this.status = STATUS_ERROR;
            cleanupIncomplete();
            return;
        }

        final File finalFile = new File(Platforms.data(), completeFile.getName());

        Engine.instance().getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (fs.copy(completeFile, finalFile)) {
                        completeFile.delete();
                        completeFile = finalFile;
                        String hash = String.valueOf(getDisplayName().hashCode());
                        Engine.instance().notifyDownloadFinished(getDisplayName(), completeFile, hash);

                        Librarian.instance().scan(Uri.fromFile(finalFile));
                    } else {
                        YouTubeDownload.this.status = STATUS_ERROR;
                    }

                    cleanupIncomplete();
                } catch (Throwable e) {
                    e.printStackTrace();
                    YouTubeDownload.this.status = STATUS_ERROR;
                }
            }
        });
    }

    private void error(Throwable e) {
        if (status != STATUS_CANCELLED) {
            if (e != null) {
                Log.e(TAG, String.format("Error downloading url: %s", sr.getDownloadUrl()), e);
            } else {
                Log.e(TAG, String.format("Error downloading url: %s", sr.getDownloadUrl()));
            }

            status = STATUS_ERROR;

            if (e.getMessage() != null && e.getMessage().contains("No space left on device")) {
                status = STATUS_ERROR_DISK_FULL;
            }

            cleanup();
        }
    }

    private void cleanup() {
        try {
            cleanupComplete();
            cleanupIncomplete();
        } catch (Throwable tr) {
            // ignore
        }
    }

    @Override
    public String getDetailsUrl() {
        return sr.getDetailsUrl();
    }

    private enum DownloadType {
        VIDEO, DASH, DEMUX
    }

    private final class HttpDownloadListenerImpl implements HttpClientListener {
        @Override
        public void onError(HttpClient client, Throwable e) {
            error(e);
        }

        @Override
        public void onData(HttpClient client, byte[] buffer, int offset, int length) {
            if (status != STATUS_COMPLETE && status != STATUS_CANCELLED && status != STATUS_DEMUXING) {
                bytesReceived += length;
                updateAverageDownloadSpeed();
                status = STATUS_DOWNLOADING;
            }
        }

        @Override
        public void onComplete(HttpClient client) {
            if (downloadType == DownloadType.VIDEO) {
                try {
                    FileUtils.moveFile(tempVideo, completeFile);
                    complete();
                } catch (IOException e) {
                    e.printStackTrace();
                    //error(null);
                }
            } else if (downloadType == DownloadType.DEMUX) {
                try {
                    status = STATUS_DEMUXING;
                    new MP4Muxer().demuxAudio(tempAudio.getAbsolutePath(), completeFile.getAbsolutePath(), buildMetadata());

                    if (!completeFile.exists()) {
                        //error(null);
                    } else {
                        complete();
                    }

                } catch (Exception e) {
                    error(e);
                }
            } else if (downloadType == DownloadType.DASH) {
                if (tempVideo.exists() && !tempAudio.exists()) {
                    start(sr.getAudio(), tempAudio);
                } else if (tempVideo.exists() && tempAudio.exists()) {
                    try {
                        status = STATUS_DEMUXING;
                        new MP4Muxer().mux(tempVideo.getAbsolutePath(), tempAudio.getAbsolutePath(), completeFile.getAbsolutePath(), buildMetadata());

                        if (!completeFile.exists()) {
                            //error(null);
                        } else {
                            complete();
                        }

                    } catch (Exception e) {
                        error(e);
                    }
                } else {
                    error(null);
                }
            } else {
                // warning!!! if this point is reached review the logic
                error(null);
            }
        }

        @Override
        public void onCancel(HttpClient client) {
            cleanup();
            status = STATUS_CANCELLED;
        }

        @Override
        public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {

        }
    }

    private void cleanupIncomplete() {
        cleanupFile(tempVideo);
        cleanupFile(tempAudio);
    }

    private void cleanupComplete() {
        cleanupFile(completeFile);
    }

    private void cleanupFile(File f) {
        if (f.exists()) {
            boolean delete = f.delete();
            if (!delete) {
                f.deleteOnExit();
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof YouTubeDownload)) {
            return false;
        }

        return sr.getFilename().equals(((YouTubeDownload) obj).sr.getFilename());
    }

    private MP4Metadata buildMetadata() {
        String title = sr.getDisplayName();
        String author = sr.getSource();
        String source = "YouTube.com";

        if (author != null && author.startsWith("YouTube - ")) {
            author = author.replace("YouTube - ", "") + " (YouTube)";
        } else {
            LinkInfo audioLinkInfo = ((YouTubeCrawledSearchResult) sr.getParent()).getAudio();
            if (audioLinkInfo != null && audioLinkInfo.user != null) {
                author = audioLinkInfo.user + " (YoutTube)";
            }
        }

        String jpgUrl = sr.getVideo() != null ? sr.getVideo().thumbnails.normal : null;
        if (jpgUrl == null && sr.getAudio() != null) {
            jpgUrl = sr.getAudio() != null ? sr.getAudio().thumbnails.normal : null;
        }

        byte[] jpg = jpgUrl != null ? HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD).getBytes(jpgUrl) : null;

        return new MP4Metadata(title, author, source, jpg);
    }
}
