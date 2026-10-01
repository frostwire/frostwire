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
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import org.apache.commons.io.FilenameUtils;
import org.limewire.util.FileUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public class HttpDownload extends HttpBTDownload {
  // IMPORTANT: Regardless of the Pools defined in HttpClientFactory, If you make this corePoolSize
  // to one, you'll be able to do only 1 HTTP download at the time
  private static final Executor HTTP_THREAD_POOL =
      new ThreadPool(
          "HttpDownloaders",
          4,
          6,
          60,
          new LinkedBlockingQueue<>(),
          true); // daemon=true, doesn't hold VM from shutting down.
  private static final Logger LOG = Logger.getLogger(HttpDownload.class);
  private static final int MAX_TRANSIENT_RETRIES = 3;
  private final String url;
  private final String title;
  private final String saveAs;
  private final File completeFile;
  private final File incompleteFile;
  private final String md5; // optional
  private final Executor executor;
  private final Consumer<HttpDownload> seedTransfer;
  private final Object completionLock = new Object();
  private boolean completionStarted;

  /** If false it should delete any temporary data and start from the beginning. */
  private final boolean deleteDataWhenCancelled;

  private final Map<String, String> httpHeaders;
  private File saveFile;
  private int md5CheckingProgress;
  private boolean isResumable;

  HttpDownload(
      String theURL,
      String theTitle,
      String saveFileAs,
      long fileSize,
      String md5hash,
      boolean shouldResume,
      boolean deleteFileWhenTransferCancelled) {
    this(
        theURL,
        theTitle,
        saveFileAs,
        fileSize,
        md5hash,
        shouldResume,
        deleteFileWhenTransferCancelled,
        null);
  }

  HttpDownload(
      String theURL,
      String theTitle,
      String saveFileAs,
      long fileSize,
      String md5hash,
      boolean shouldResume,
      boolean deleteFileWhenTransferCancelled,
      Map<String, String> httpHeaders) {
    this(
        theURL,
        theTitle,
        saveFileAs,
        fileSize,
        md5hash,
        shouldResume,
        deleteFileWhenTransferCancelled,
        httpHeaders,
        HTTP_THREAD_POOL,
        dl -> BittorrentDownload.RendererHelper.onSeedTransfer(dl, false));
  }

  // Scheduling and seeding seams keep completion regressions independent of Swing/native code.
  HttpDownload(
      String theURL,
      String theTitle,
      String saveFileAs,
      long fileSize,
      String md5hash,
      boolean shouldResume,
      boolean deleteFileWhenTransferCancelled,
      Map<String, String> httpHeaders,
      Executor executor,
      Consumer<HttpDownload> seedTransfer) {
    super(saveFileAs, fileSize);
    this.executor = executor;
    this.seedTransfer = seedTransfer;
    url = theURL;
    title = theTitle;
    saveAs = saveFileAs;
    md5 = md5hash;
    deleteDataWhenCancelled = deleteFileWhenTransferCancelled;
    this.httpHeaders = httpHeaders == null ? null : new HashMap<>(httpHeaders);
    completeFile = FileUtils.buildFile(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), saveAs);
    incompleteFile = buildIncompleteFile(completeFile);
    isResumable = shouldResume;
    start(shouldResume);
  }

  private static File buildIncompleteFile(File file) {
    String prefix = FilenameUtils.getBaseName(file.getName());
    String ext = FilenameUtils.getExtension(file.getAbsolutePath());
    return org.apache.commons.io.FileUtils.validFilepathLengthFile(
        new File(HttpBTDownload.getIncompleteFolder(), prefix + ".incomplete." + ext));
  }

  @Override
  HttpClientListener createHttpClientListener() {
    return new HttpDownloadListenerImpl();
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
    synchronized (completionLock) {
      if (state != TransferState.FINISHED) {
        if (isPausable()) {
          state = TransferState.PAUSING;
        } else {
          state = TransferState.CANCELING;
        }
        httpClient.cancel();
      }
    }
  }

  @Override
  public void remove() {
    synchronized (completionLock) {
      super.remove();
    }
  }

  @Override
  public boolean isCompleted() {
    synchronized (completionLock) {
      // Receiving all HTTP bytes is not completion while a subclass is replacing the file.
      return bytesReceived > 0 && state == TransferState.FINISHED;
    }
  }

  @Override
  public TransferState getState() {
    synchronized (completionLock) {
      return state;
    }
  }

  @Override
  public File getSaveLocation() {
    return saveFile;
  }

  @Override
  public void resume() {
    httpClient.resetCancellation();
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
    synchronized (completionLock) {
      completionStarted = false;
      state = TransferState.WAITING;
    }
    saveFile = org.apache.commons.io.FileUtils.validFilepathLengthFile(completeFile);
    executor.execute(
        () -> {
          try {
            File expectedFile =
                org.apache.commons.io.FileUtils.validFilepathLengthFile(
                    new File(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), saveAs));
            if (md5 != null && expectedFile.length() == size && checkMD5(expectedFile)) {
              saveFile = expectedFile;
              bytesReceived = expectedFile.length();
              if (beginCompletion()) {
                // Preserve the cached-file path's existing no-auto-seed behavior.
                completeDownload(false);
              }
              return;
            }
            if (resume) {
              if (incompleteFile.exists()) {
                bytesReceived = incompleteFile.length();
              }
            }
            int attempts = 0;
            while (true) {
              try {
                if (httpClient.isCanceled()) {
                  return;
                }
                httpClient.save(url, incompleteFile, resume || attempts > 0, httpHeaders);
                break;
              } catch (SocketException e) {
                if (httpClient.isCanceled()
                    || attempts >= MAX_TRANSIENT_RETRIES
                    || incompleteFile.length() == 0) {
                  throw e;
                }
                attempts++;
                bytesReceived = incompleteFile.length();
                LOG.info(
                    "Retrying partial HTTP download after connection reset, attempt " + attempts);
              }
            }
          } catch (IOException | StackOverflowError e) {
            LOG.error(url, e);
            if (httpClientListener != null) {
              httpClientListener.onError(httpClient, e);
            }
            if (httpClient.getListener() != null) {
              httpClient.getListener().onError(httpClient, e);
            }
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
    return file.exists()
        && DigestUtils.checkMD5(
            file,
            md5,
            new DigestProgressListener() {
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
   * Synchronous postprocessing hook. The downloaded file is at getSaveLocation(), but the transfer
   * is not completed or eligible for seeding until this returns. Handled failures may leave the
   * original file in place (for example, a failed DASH mux).
   */
  void onComplete() {}

  private boolean beginCompletion() {
    synchronized (completionLock) {
      if (completionStarted) {
        return false;
      }
      if (httpClient.isCanceled()) {
        httpClient.getListener().onCancel(httpClient);
        return false;
      }
      completionStarted = true;
      return true;
    }
  }

  private void completeDownload(boolean autoSeed) {
    synchronized (completionLock) {
      if (httpClient.isCanceled()) {
        httpClient.getListener().onCancel(httpClient);
        return;
      }
      state = TransferState.CHECKING;
    }
    // Do not hold the lock across audio fetch/mux; cancellation must remain responsive.
    onComplete();
    synchronized (completionLock) {
      if (httpClient.isCanceled()) {
        httpClient.getListener().onCancel(httpClient);
        return;
      }
      if (saveFile.isFile()) {
        size = saveFile.length();
        bytesReceived = size;
      }
      state = TransferState.FINISHED;
      // SlideDownload can consume/delete its zip in the hook; there is then nothing to seed.
      if (autoSeed && saveFile.isFile() && SharingSettings.SEED_FINISHED_TORRENTS.getValue()) {
        seedTransfer.accept(this);
      }
    }
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
      if (!beginCompletion()) {
        return;
      }
      if (md5 != null && !checkMD5(incompleteFile)) {
        if (httpClient.isCanceled()) {
          onCancel(client);
          return;
        }
        state = TransferState.ERROR_HASH_MD5;
        cleanupIncomplete();
        return;
      }
      synchronized (completionLock) {
        if (httpClient.isCanceled()) {
          onCancel(client);
          return;
        }
        if (!incompleteFile.renameTo(completeFile)) {
          state = TransferState.ERROR_MOVING_INCOMPLETE;
          LOG.error(
              "Could not rename ["
                  + incompleteFile.getAbsolutePath()
                  + "] into ["
                  + completeFile.getAbsolutePath()
                  + "]");
          return;
        }
        cleanupIncomplete();
      }
      completeDownload(true);
    }

    @Override
    public void onCancel(HttpClient client) {
      synchronized (completionLock) {
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
      if (headerFields.containsKey("Content-Range")) {
        try {
          String contentRange = headerFields.get("Content-Range").get(0);
          int slash = contentRange.lastIndexOf('/');
          if (slash >= 0) {
            size = Long.parseLong(contentRange.substring(slash + 1));
          }
        } catch (Throwable ignored) {
        }
      } else if (headerFields.containsKey("Content-Length")) {
        try {
          size = Long.parseLong(headerFields.get("Content-Length").get(0));
        } catch (Throwable ignored) {
        }
      }
      // try figuring out file size from HTTP headers depending on the response.
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
