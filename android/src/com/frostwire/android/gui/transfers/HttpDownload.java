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

import android.util.Log;
import com.frostwire.android.R;
import com.frostwire.android.core.SystemPaths;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.transfers.TransferItem;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.ZipUtils;
import com.frostwire.util.http.HttpClient;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public final class HttpDownload implements DownloadTransfer {

    private static final String TAG = "FW.HttpDownload";

    static final int STATUS_DOWNLOADING = 1;
    static final int STATUS_COMPLETE = 2;
    static final int STATUS_ERROR = 3;
    static final int STATUS_CANCELLED = 4;
    static final int STATUS_WAITING = 5;
    static final int STATUS_UNCOMPRESSING = 6;
    static final int STATUS_SAVE_DIR_ERROR = 7;
    static final int STATUS_ERROR_DISK_FULL = 8;

    private static final int SPEED_AVERAGE_CALCULATION_INTERVAL_MILLISECONDS = 1000;

    private final TransferManager manager;
    private final HttpDownloadLink link;
    private final Date dateCreated;
    private final File savePath;

    private int status;
    private long bytesReceived;
    private long averageSpeed; // in bytes

    // variables to keep the download rate of file transfer
    private long speedMarkTimestamp;
    private long totalReceivedSinceLastSpeedStamp;

    private HttpDownloadListener listener;

    HttpDownload(TransferManager manager, File savePath, HttpDownloadLink link) {
        this.manager = manager;
        this.link = link;
        this.dateCreated = new Date();

        this.savePath = new File(savePath, link.getFileName());
        this.status = STATUS_DOWNLOADING;

        if (savePath == null || !savePath.isDirectory() && !savePath.mkdirs()) {
            this.status = STATUS_SAVE_DIR_ERROR;
        }

        if (TransferManager.isCurrentMountAlmostFull()) {
            this.status = STATUS_ERROR_DISK_FULL;
        }
    }

    HttpDownload(TransferManager manager, HttpDownloadLink link) {
        this(manager, SystemPaths.getTorrentData(), link);
    }

    public HttpDownloadListener getListener() {
        return listener;
    }

    public void setListener(HttpDownloadListener listener) {
        this.listener = listener;
    }

    public String getDisplayName() {
        return link.getDisplayName();
    }

    public String getStatus() {
        return getStatusString(status);
    }

    public int getProgress() {
        if (link.getSize() > 0) {
            return isComplete() ? 100 : (int) ((bytesReceived * 100) / link.getSize());
        } else {
            return 0;
        }
    }

    public long getSize() {
        return link.getSize();
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
        if (link.getSize() > 0) {
            long speed = getDownloadSpeed();
            return speed > 0 ? (link.getSize() - getBytesReceived()) / speed : Long.MAX_VALUE;
        } else {
            return 0;
        }
    }

    public boolean isComplete() {
        if (bytesReceived > 0) {
            return (bytesReceived == link.getSize() && status == STATUS_COMPLETE) || status == STATUS_COMPLETE || status == STATUS_ERROR;
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
        return savePath;
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

    @Override
    public File previewFile() {
        return getSavePath();
    }

    int getStatusCode() {
        return status;
    }

    public void start() {
        start(false);
    }

    public void start(final boolean resume) {
        if (status == STATUS_SAVE_DIR_ERROR || status == STATUS_ERROR_DISK_FULL || status == STATUS_ERROR) {
            return;
        }

        Engine.instance().getThreadPool().execute(new Thread(getDisplayName()) {
            public void run() {
                try {
                    status = STATUS_DOWNLOADING;
                    String uri = link.getUrl();
                    HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
                    client.setListener(new DownloadListener());
                    client.save(uri, savePath, resume);
                    Librarian.instance().scan(savePath);
                } catch (Throwable e) {
                    error(e);
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
            case STATUS_UNCOMPRESSING:
                resId = R.string.http_download_status_uncompressing;
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
        boolean success = true;
        String location = null;
        if (link.isCompressed()) {
            status = STATUS_UNCOMPRESSING;
            location = FilenameUtils.removeExtension(savePath.getAbsolutePath());
            success = ZipUtils.unzip(savePath, new File(location));
        }

        if (success) {
            if (listener != null) {
                listener.onComplete(this);
            }

            status = STATUS_COMPLETE;

            manager.incrementDownloadsToReview();
            Engine.instance().notifyDownloadFinished(getDisplayName(), getSavePath());

            if (savePath.getAbsoluteFile().exists()) {
                Librarian.instance().scan(link.isCompressed() ? new File(location) : getSavePath().getAbsoluteFile());
            }
        } else {
            error(new Exception("Error"));
        }
    }

    private void error(Throwable e) {
        if (status != STATUS_CANCELLED) {
            Log.e(TAG, String.format("Error downloading url: %s", link.getUrl()), e);
            status = STATUS_ERROR;

            if (e.getMessage() !=null && e.getMessage().contains("No space left on device")) {
                status = STATUS_ERROR_DISK_FULL;
            }

            cleanup();
        }
    }

    private void cleanup() {
        try {
            savePath.delete();
        } catch (Throwable tr) {
            // ignore
        }
    }

    private final class DownloadListener extends HttpClient.HttpClientListenerAdapter {
        public DownloadListener() {
        }

        @Override
        public void onError(HttpClient client, Throwable e) {
            error(e);
        }

        @Override
        public void onData(HttpClient client, byte[] buffer, int offset, int length) {
            bytesReceived += length;
            updateAverageDownloadSpeed();

            if (status == STATUS_CANCELLED) {
                // ok, this is not the most elegant solution but it effectively breaks the
                // download logic flow.
                throw new RuntimeException("Invalid status, transfer cancelled");
            }
        }

        @Override
        public void onComplete(HttpClient client) {
            complete();
        }
    }

    static void simpleHTTP(String url, OutputStream out, int timeout) throws Throwable {
        URL u = new URL(url);
        URLConnection con = u.openConnection();
        con.setConnectTimeout(timeout);
        con.setReadTimeout(timeout);
        InputStream in = con.getInputStream();
        try {

            byte[] b = new byte[1024];
            int n = 0;
            while ((n = in.read(b, 0, b.length)) != -1) {
                out.write(b, 0, n);
            }
        } finally {
            try {
                out.close();
            } catch (Throwable e) {
                // ignore   
            }
            try {
                in.close();
            } catch (Throwable e) {
                // ignore   
            }
        }
    }

    @Override
    public String getDetailsUrl() {
        return link.getUrl();
    }
}
