/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.bittorrent;

import com.frostwire.gui.DigestUtils;
import com.frostwire.gui.DigestUtils.DigestProgressListener;
import com.frostwire.transfers.TransferState;
import com.frostwire.util.Logger;
import com.frostwire.util.ThreadPool;
import com.frostwire.util.http.HttpClient;
import com.frostwire.util.http.HttpClient.HttpClientListener;
import com.frostwire.util.http.HttpClient.RangeNotSupportedException;
import com.limegroup.gnutella.settings.SharingSettings;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author gubatron
 * @author aldenml
 */
public class HttpDownload extends HttpBTDownload {
    private static final Executor HTTP_THREAD_POOL = new ThreadPool("HttpDownloaders", 1, 6, 5000, new LinkedBlockingQueue<>(), true); // daemon=true, doesn't hold VM from shutting down.
    private static final Logger LOG = Logger.getLogger(HttpDownload.class);
    private final String url;
    private final String title;
    private final String saveAs;
    private final File completeFile;
    private final File incompleteFile;
    private final String md5; //optional
    /**
     * If false it should delete any temporary data and start from the beginning.
     */
    private final boolean deleteDataWhenCancelled;
    private File saveFile;
    private int md5CheckingProgress;
    private boolean isResumable;

    HttpDownload(String theURL, String theTitle, String saveFileAs, double fileSize, String md5hash, boolean shouldResume, boolean deleteFileWhenTransferCancelled) {
        super(saveFileAs, fileSize);
        url = theURL;
        title = theTitle;
        saveAs = saveFileAs;
        md5 = md5hash;
        deleteDataWhenCancelled = deleteFileWhenTransferCancelled;
        completeFile = FileUtils.buildFile(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), saveAs);
        incompleteFile = buildIncompleteFile(completeFile);
        isResumable = shouldResume;
        start(shouldResume);
    }

    private static File buildIncompleteFile(File file) {
        String prefix = FilenameUtils.getBaseName(file.getName());
        String ext = FilenameUtils.getExtension(file.getAbsolutePath());
        return new File(HttpBTDownload.getIncompleteFolder(), prefix + ".incomplete." + ext);
    }

    @Override
    HttpClientListener createHttpClientListener() {
        return new HttpDownloadListenerImpl(this);
    }

    @Override
    public String getName() {
        return saveFile.getName();
    }

    @Override
    public String getDisplayName() {
        return title;
    }

    @Override
    public boolean isResumable() {
        return isResumable && state == TransferState.PAUSED && size > 0;
    }

    @Override
    public boolean isPausable() {
        return isResumable && state == TransferState.DOWNLOADING && size > 0;
    }

    @Override
    public void pause() {
        if (state != TransferState.FINISHED) {
            if (isPausable()) {
                state = TransferState.PAUSING;
            } else {
                state = TransferState.CANCELING;
            }
            httpClient.cancel();
        }
    }

    @Override
    public File getSaveLocation() {
        return saveFile;
    }

    @Override
    public void resume() {
        start(true);
    }

    @Override
    public int getProgress() {
        if (state == TransferState.CHECKING) {
            return md5CheckingProgress;
        }
        if (size <= 0) {
            return -1;
        }
        int progress = (int) ((bytesReceived * 100) / size);
        return Math.min(100, progress);
    }

    @Override
    public String getHash() {
        return md5;
    }

    private void start(final boolean resume) {
        state = TransferState.WAITING;
        saveFile = completeFile;
        HTTP_THREAD_POOL.execute(() -> {
            try {
                File expectedFile = new File(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), saveAs);
                if (md5 != null &&
                        expectedFile.length() == size &&
                        checkMD5(expectedFile)) {
                    saveFile = expectedFile;
                    bytesReceived = expectedFile.length();
                    state = TransferState.FINISHED;
                    onComplete();
                    return;
                }
                if (resume) {
                    if (incompleteFile.exists()) {
                        bytesReceived = incompleteFile.length();
                    }
                }
                httpClient.save(url, incompleteFile, resume);
            } catch (IOException e) {
                e.printStackTrace();
                httpClientListener.onError(httpClient, e);
            }
        });
    }

    @Override
    void cleanupIncomplete() {
        cleanupFile(incompleteFile);
    }

    private boolean checkMD5(File file) {
        state = TransferState.CHECKING;
        md5CheckingProgress = 0;
        return file.exists() && DigestUtils.checkMD5(file, md5, new DigestProgressListener() {
            @Override
            public void onProgress(int progressPercentage) {
                md5CheckingProgress = progressPercentage;
            }

            @Override
            public boolean stopDigesting() {
                return httpClient.isCanceled();
            }
        });
    }

    /**
     * Meant to be overwritten by children classes that want to do something special
     * after the download is completed.
     */
    void onComplete() {
    }

    @Override
    public boolean canPreview() {
        return false;
    }

    @Override
    public File getPreviewFile() {
        return null;
    }

    private final class HttpDownloadListenerImpl implements HttpClientListener {
        private final HttpDownload dl;

        HttpDownloadListenerImpl(HttpDownload httpDownload) {
            dl = httpDownload;
        }

        @Override
        public void onError(HttpClient client, Throwable e) {
            if (e instanceof RangeNotSupportedException) {
                isResumable = false;
                start(false);
            } else {
                state = TransferState.ERROR;
                cleanup();
            }
        }

        @Override
        public void onData(HttpClient client, byte[] buffer, int offset, int length) {
            if (!state.equals(TransferState.PAUSING) && !state.equals(TransferState.CANCELING)) {
                bytesReceived += length;
                updateAverageDownloadSpeed();
                state = TransferState.DOWNLOADING;
            }
        }

        @Override
        public void onComplete(HttpClient client) {
            if (md5 != null && !checkMD5(incompleteFile)) {
                state = TransferState.ERROR_HASH_MD5;
                cleanupIncomplete();
                return;
            }
            boolean renameTo = incompleteFile.renameTo(completeFile);
            if (!renameTo) {
                state = TransferState.ERROR_MOVING_INCOMPLETE;
                LOG.error("Could not rename [" + incompleteFile.getAbsolutePath() + "] into [" + completeFile.getAbsolutePath() + "]");
            } else {
                state = TransferState.FINISHED;
                cleanupIncomplete();
                if (SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
                    BittorrentDownload.RendererHelper.onSeedTransfer(dl, false);
                }
                HttpDownload.this.onComplete();
            }
        }

        @Override
        public void onCancel(HttpClient client) {
            if (state.equals(TransferState.CANCELING)) {
                if (deleteDataWhenCancelled) {
                    cleanup();
                }
                state = TransferState.CANCELED;
            } else if (state.equals(TransferState.PAUSING)) {
                state = TransferState.PAUSED;
                isResumable = true;
            } else {
                state = TransferState.CANCELED;
            }
        }

        @Override
        public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {
            if (headerFields == null) {
                isResumable = false;
                size = -1;
                return;
            }
            if (headerFields.containsKey("Accept-Ranges")) {
                isResumable = headerFields.get("Accept-Ranges").contains("bytes");
            } else {
                isResumable = headerFields.containsKey("Content-Range");
            }
            if (headerFields.containsKey("Content-Length")) {
                try {
                    size = Long.parseLong(headerFields.get("Content-Length").get(0));
                } catch (Throwable ignored) {
                }
            }
            //try figuring out file size from HTTP headers depending on the response.
            if (size < 0) {
                String responseCodeStr = headerFields.get(null).get(0);
                if (responseCodeStr.contains(String.valueOf(HttpURLConnection.HTTP_OK))) {
                    if (headerFields.containsKey("Content-Length")) {
                        try {
                            size = Long.parseLong(headerFields.get("Content-Length").get(0));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }
}
