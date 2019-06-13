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

import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.ThreadPool;
import com.frostwire.util.http.HttpClient;
import org.apache.commons.io.FilenameUtils;

import javax.net.ssl.SSLException;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class BaseHttpDownload implements Transfer {
    private static final Logger LOG = Logger.getLogger(BaseHttpDownload.class);
    // is 20 concurrent downloads enough?
    private static final ExecutorService THREAD_POOL = ThreadPool.newThreadPool("HttpDownload", 20, true);
    protected final Info info;
    protected final File savePath;
    protected final File tempPath;
    protected final Date created;
    protected TransferState state;
    protected SpeedStat stat;
    protected boolean complete;

    protected BaseHttpDownload(Info info) {
        this.info = info;
        File saveDir = Platforms.data();
        File tempDir = Platforms.temp();
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
        this.state = TransferState.WAITING;
        this.complete = false;
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
        if (baseName.length() > 127) { // the goal is 255, but unsafe after 127 in some systems
            baseName = baseName.substring(0, 127); // end index is exclusive
        }
        String ext = FilenameUtils.getExtension(name);
        File f = new File(saveDir, baseName + "." + ext);
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

    @Override
    public File previewFile() {
        return isComplete() ? savePath : null;
    }

    @Override
    public double getSize() {
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
        return !complete ? stat.progress(info.size()) : 100;
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
        if (complete) {
            return;
        }
        complete(state = TransferState.CANCELED);
        FileSystem fs = Platforms.fileSystem();
        if (fs.delete(tempPath)) {
            LOG.warn("Error deleting temporary file: " + tempPath);
        }
        if (deleteData) {
            if (fs.delete(savePath)) {
                LOG.warn("Error deleting download data file: " + savePath);
            }
        }
    }

    protected void start(final String url, final File temp, final boolean resume) {
        if (complete) {
            return;
        }
        THREAD_POOL.execute(new Thread(getDisplayName()) {
            public void run() {
                try {
                    if (complete) {
                        return;
                    }
                    state = TransferState.DOWNLOADING;
                    HttpClient client = HttpClientFactory.getInstance(HttpClientFactory.HttpContext.DOWNLOAD);
                    client.setListener(new DownloadListener());
                    client.save(url, temp, resume);
                } catch (Throwable e) {
                    error(e);
                }
            }
        });
    }

    protected final void complete(TransferState state) {
        this.state = state;
        if (!complete) {
            complete = true;
            if (state == TransferState.COMPLETE) {
                try {
                    onComplete();
                } catch (Throwable e) {
                    error(e);
                }
            }
        }
    }

    protected final void finish() {
        if (complete) {
            return;
        }
        state = TransferState.FINISHING;
        THREAD_POOL.execute(() -> {
            try {
                if (complete) {
                    return;
                }
                onFinishing();
            } catch (Throwable e) {
                error(e);
            }
        });
    }

    protected final void error(Throwable e) {
        if (state != TransferState.CANCELED) {
            complete(TransferState.ERROR);
            LOG.error("General error in download " + info, e);
            if (e.getMessage() != null && e.getMessage().contains("No space left on device")) {
                complete(TransferState.ERROR_DISK_FULL);
            }
            if (e instanceof SSLException || e instanceof SocketTimeoutException) {
                complete(TransferState.ERROR_CONNECTION_TIMED_OUT);
            }
            if (e instanceof UnknownHostException) {
                complete(TransferState.ERROR_NO_INTERNET);
            }
        }
    }

    protected void moveAndComplete(File src, File dst) {
        FileSystem fs = Platforms.fileSystem();
        if (fs.copy(src, dst)) {
            if (!fs.delete(src)) {
                LOG.warn("Error deleting source file while moving: " + src);
            }
            state = TransferState.SCANNING;
            fs.scan(dst);
            complete(TransferState.COMPLETE);
        } else {
            complete(TransferState.ERROR_MOVING_INCOMPLETE);
        }
    }

    protected void onHttpComplete() {
        finish();
    }

    protected void onFinishing() {
    }

    protected void onComplete() {
    }

    public static final class Info {
        private final String url;
        private final String filename;
        private final String displayName;
        private final double size;

        public Info(String url, String filename, String displayName, double size) {
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

        public double size() {
            return size;
        }

        @Override
        public String toString() {
            return "{BaseHttpDownload.Info@" + hashCode() + " url=" + url + " filename=" + filename + " displayname=" + displayName + " size=" + size + "}";
        }
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
            if (complete) {
                // ok, this is not the most elegant solution but it effectively breaks the
                // download logic flow.
                throw new RuntimeException("Invalid status, transfer cancelled");
            }
        }

        @Override
        public void onComplete(HttpClient client) {
            try {
                onHttpComplete();
            } catch (Throwable e) {
                error(e);
            }
        }
    }
}
