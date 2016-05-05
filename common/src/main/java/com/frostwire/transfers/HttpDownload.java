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

package com.frostwire.transfers;

import com.frostwire.logging.Logger;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platform;
import com.frostwire.platform.Platforms;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.ThreadPool;
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
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 */
public class HttpDownload implements Transfer {

    private static final Logger LOG = Logger.getLogger(HttpDownload.class);

    // is 20 concurrent downloads enough?
    private static final ExecutorService THREAD_POOL = ThreadPool.newThreadPool("HttpDownload", 20, true);

    private final Info info;

    private final File savePath;
    private final File tempPath;
    private final Date created;

    private TransferState state;
    private SpeedStat stat;
    private boolean complete;

    public HttpDownload(Info info, File saveDir, File tempDir) {
        this.info = info;

        FileSystem fs = Platforms.fileSystem();
        if (!fs.isDirectory(saveDir) && !fs.mkdirs(saveDir)) {
            complete(TransferState.ERROR_SAVE_DIR);
        }
        if (!fs.isDirectory(tempDir) && !fs.mkdirs(tempDir)) {
            complete(TransferState.ERROR_TEMP_DIR);
        }

        String filename = cleanupFilename(info.filename());
        this.savePath = buildFile(fs, saveDir, filename);
        this.tempPath = buildFile(fs, tempDir, filename);
        this.created = new Date();

        this.stat = new SpeedStat();
        this.state = TransferState.QUEUED;
        this.complete = false;
    }

    @Override
    public String getName() {
        return info.url();
    }

    @Override
    public String getDisplayName() {
        return info.displayName();
    }

    @Override
    public File getSavePath() {
        return savePath;
    }

    public File tempPath() {
        return tempPath;
    }

    @Override
    public File previewFile() {
        return isComplete() ? savePath : null;
    }

    @Override
    public long getSize() {
        return info.size();
    }

    @Override
    public Date getCreated() {
        return created;
    }

    @Override
    public TransferState getState() {
        return state;
    }

    @Override
    public long getBytesReceived() {
        return stat.totalBytes();
    }

    @Override
    public long getBytesSent() {
        return 0;
    }

    @Override
    public long getDownloadSpeed() {
        return complete ? 0 : stat.averageSpeed();
    }

    @Override
    public long getUploadSpeed() {
        return 0;
    }

    @Override
    public boolean isDownloading() {
        return state == TransferState.DOWNLOADING;
    }

    @Override
    public long getETA() {
        return !complete ? stat.eta(info.size()) : 0;
    }

    @Override
    public int getProgress() {
        if (complete) {
            return 100;
        }

        long size = info.size();
        return size > 0 ? (int) ((stat.totalBytes() * 100) / size) : 0;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public List<TransferItem> getItems() {
        return Collections.emptyList();
    }

    @Override
    public void remove(boolean deleteData) {
        if (!complete) {
            complete(state = TransferState.CANCELED);
        }
    }

    public void start(final boolean resume) {
        if (complete) {
            return;
        }

        THREAD_POOL.execute(new Thread(getDisplayName()) {
            public void run() {
                try {
                    state = TransferState.DOWNLOADING;
                    String uri = info.url();
                    HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
                    client.setListener(new DownloadListener());
                    client.save(uri, tempPath, resume);
                } catch (Throwable e) {
                    error(e);
                }
            }
        });
    }

    protected void onBeforeMove() {
    }

    protected void onAfterMove() {
    }

    private void complete(TransferState state) {
        this.state = state;
        complete = true;

        if (state == TransferState.COMPLETE) {
            THREAD_POOL.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        onBeforeMove();

                        FileSystem fs = Platforms.fileSystem();
                        if (fs.copy(tempPath, savePath)) {

                            onAfterMove();

                            if (!fs.delete(tempPath)) {
                                LOG.warn("Error deleting temporary file");
                            }
                        } else {
                            HttpDownload.this.state = TransferState.ERROR_MOVING_INCOMPLETE;
                        }
                    } catch (Throwable e) {
                        LOG.error("General error in the complete phase", e);
                        HttpDownload.this.state = TransferState.ERROR;
                    }
                }
            });
        }
    }

    private void error(Throwable e) {
        if (state != TransferState.CANCELED) {
            complete(TransferState.ERROR);

            if (e.getMessage() != null && e.getMessage().contains("No space left on device")) {
                complete(TransferState.ERROR_DISK_FULL);
            }

            // TODO: research here for an actual difference between
            // no internet and no network
            if (Platforms.get().networkType() == Platform.NetworkType.NONE) {
                complete(TransferState.ERROR_NO_INTERNET);
            }
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

    static File buildFile(FileSystem fs, File saveDir, String name) {
        String baseName = FilenameUtils.getBaseName(name);
        String ext = FilenameUtils.getExtension(name);

        File f = new File(saveDir, name);
        int i = 1;
        while (fs.exists(f) && i < 30) {
            f = new File(saveDir, baseName + " (" + i + ")." + ext);
            i++;
        }
        return f;
    }

    static String cleanupFilename(String filename) {
        filename = filename.replaceAll("[\\\\/:*?\"<>|\\[\\]]+", "_");
        // put here more replaces if necessary
        return filename;
    }

    private final class DownloadListener extends HttpClient.HttpClientListenerAdapter {

        @Override
        public void onHeaders(HttpClient httpClient, Map<String, List<String>> headerFields) {
        }

        @Override
        public void onError(HttpClient client, Throwable e) {
            error(e);
        }

        @Override
        public void onData(HttpClient client, byte[] buffer, int offset, int length) {
            stat.update(length);
            if (state == TransferState.CANCELED) {
                // ok, this is not the most elegant solution but it effectively breaks the
                // download logic flow.
                throw new RuntimeException("Invalid status, transfer cancelled");
            }
        }

        @Override
        public void onComplete(HttpClient client) {
            complete(TransferState.COMPLETE);
        }
    }

    public static final class Info {

        private final String url;
        private final String filename;
        private final String displayName;
        private final long size;

        public Info(String url, String filename, String displayName, long size) {
            this.url = url;
            this.filename = filename;
            this.displayName = displayName;
            this.size = size;
        }

        public String url() {
            return url;
        }

        public String filename() {
            return filename;
        }

        public String displayName() {
            return displayName;
        }

        public long size() {
            return size;
        }
    }
}
