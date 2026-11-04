/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static com.frostwire.bittorrent.LocalEngine.await;
import static com.frostwire.bittorrent.LocalEngine.content;
import static com.frostwire.bittorrent.LocalEngine.torrent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.frostwire.bittorrent.LocalEngine.Events;
import com.frostwire.jlibtorrent.TorrentInfo;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * When something goes wrong (a network interface, a settings file, a folder, a listener that
 * throws) the engine reports it and keeps working for everything else.
 */
@Timeout(120)
class BTEngineFailureTest {

  @TempDir Path dir;
  private LocalEngine local;

  @AfterEach
  void stopEngine() {
    if (local != null) {
      local.close();
    }
  }

  @Test
  void anInterfaceThatCannotListenIsReportedAndTheOthersStillListen() throws Exception {
    local = LocalEngine.create(dir);
    // 192.0.2.1 is reserved for documentation: no machine has it.
    local.ctx.interfaces = "127.0.0.1:0,192.0.2.1:0";
    List<String> log = capture("BTEngine");
    try {
      local.engine.start();

      await(() -> local.engine.swig().listen_port() > 0, "listening on 127.0.0.1");
      await(
          () -> log.stream().anyMatch(m -> m.contains("Listen failed") && m.contains("192.0.2.1")),
          "the failure was reported");
    } finally {
      release("BTEngine");
    }
    assertTrue(log.stream().anyMatch(m -> m.contains("Listen succeeded")));
  }

  @Test
  void aSettingsFileThatIsAFolderNeitherStopsStartingNorStopping() throws Exception {
    local = LocalEngine.create(dir);
    File settings = local.engine.settingsFile();
    Files.delete(settings.toPath());
    assertTrue(settings.mkdir());

    local.engine.start();

    assertTrue(local.engine.isRunning());
    assertEquals(10, local.engine.settings().activeDownloads(), "started with the defaults");
    local.engine.stop();
    assertFalse(local.engine.isRunning());
    assertTrue(settings.isDirectory());
  }

  @Test
  void withoutAHomeFolderNothingIsRestored() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo kept = album("kept");
    local.ctx.homeDir = dir.resolve("missing").toFile();

    local.engine.restoreDownloads();
    local.engine.download(kept, dir.resolve("save").toFile(), null, null);

    // Alerts arrive in order: anything restored would have been added before this.
    Events.next(local.events.added, "kept");
    assertTrue(local.events.added.isEmpty());
  }

  @Test
  void aTransferWithNowhereToGoIsNotRestored() throws Exception {
    local = LocalEngine.start(dir);
    TorrentInfo ti = album("homeless");
    Path saveDir = dir.resolve("removable");
    local.engine.download(ti, saveDir.toFile(), null, null);
    Events.next(local.events.added, "homeless");
    String hash = ti.infoHashV1().toHex();
    await(() -> local.engine.resumeDataFile(hash).isFile(), "the resume data was saved");
    Path notAFolder = Files.write(dir.resolve("not-a-folder"), new byte[] {1});

    local.restart(
        () -> {
          // Its folder and the data folder are files now: neither can hold it.
          Files.walk(saveDir)
              .sorted(java.util.Comparator.reverseOrder())
              .forEach(p -> p.toFile().delete());
          Files.write(saveDir, new byte[] {1});
          local.ctx.dataDir = notAFolder.resolve("data").toFile();
        });
    local.engine.restoreDownloads();
    TorrentInfo kept = album("kept");
    local.engine.download(kept, dir.resolve("save").toFile(), null, null);

    Events.next(local.events.added, "kept");
    assertTrue(local.events.added.isEmpty(), "the transfer with nowhere to go wasn't restored");
    assertTrue(local.engine.resumeTorrentFile(hash).isFile(), "it is kept for a later restore");
  }

  @Test
  void aSaveFolderWithoutWriteAccessAddsNothing() throws Exception {
    local = LocalEngine.start(dir);
    File readOnly = Files.createDirectories(dir.resolve("read-only")).toFile();
    assertTrue(readOnly.setWritable(false));
    try {
      assumeFalse(readOnly.canWrite(), "running with rights to write anywhere");
      TorrentInfo lost = album("lost");
      TorrentInfo kept = album("kept");

      local.engine.download(lost, readOnly, null, null);
      local.engine.download(kept, dir.resolve("save").toFile(), null, null);

      Events.next(local.events.added, "kept");
      assertTrue(local.events.added.isEmpty());
    } finally {
      readOnly.setWritable(true);
    }
  }

  @Test
  void withNeitherASaveFolderNorADataFolderNothingIsAdded() throws Exception {
    local = LocalEngine.start(dir);
    File dataDir = local.ctx.dataDir;
    TorrentInfo lost = album("lost");
    TorrentInfo kept = album("kept");

    local.ctx.dataDir = null;
    local.engine.download(lost, null, null, null);
    local.ctx.dataDir = dataDir;
    local.engine.download(kept, null, null, null);

    assertEquals(dataDir, Events.next(local.events.added, "kept").getSavePath());
    assertTrue(local.events.added.isEmpty());
  }

  @Test
  void listenersThatThrowDoNotStopTheEngineOrTheDownload() throws Exception {
    local = LocalEngine.start(dir);
    Throwing engineListener = new Throwing();
    local.engine.setListener(engineListener);
    TorrentInfo first = album("first");
    TorrentInfo second = album("second");
    File content = dir.resolve("content").toFile(); // has the data: it checks and finishes
    // Paused, so the download's listener is in place before it finishes.
    local.engine.pause();

    local.engine.download(first, content, null, null);

    BTDownload dl = Events.next(engineListener.added, "first");
    ThrowingDownloadListener downloadListener = new ThrowingDownloadListener();
    dl.setListener(downloadListener);
    local.engine.resume();
    assertSame(dl, poll(downloadListener.finished));
    // The engine heard it too, after the download's listener threw.
    Events.next(engineListener.finished, "first");
    local.engine.download(second, content, null, null);
    Events.next(engineListener.added, "second");

    dl.remove();

    assertSame(dl, poll(downloadListener.removed));
  }

  private TorrentInfo album(String name) throws Exception {
    Path folder = dir.resolve("content").resolve(name);
    content(folder.resolve("one.mp3"), 64 * 1024, name.hashCode());
    content(folder.resolve("two.flac"), 128 * 1024, name.hashCode() + 1);
    return torrent(folder.toFile(), null);
  }

  private static <T> T poll(BlockingQueue<T> queue) throws InterruptedException {
    T value = queue.poll(LocalEngine.TIMEOUT_SECONDS, TimeUnit.SECONDS);
    if (value == null) {
      throw new AssertionError("timed out waiting for a listener call");
    }
    return value;
  }

  private static final Handler[] CAPTURE = new Handler[1];

  /** The messages logged to {@code logger} from now on. */
  private static List<String> capture(String logger) {
    List<String> messages = new CopyOnWriteArrayList<>();
    CAPTURE[0] =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            messages.add(String.valueOf(record.getMessage()));
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    Logger.getLogger(logger).addHandler(CAPTURE[0]);
    return messages;
  }

  private static void release(String logger) {
    Logger.getLogger(logger).removeHandler(CAPTURE[0]);
  }

  /** Records each call, then throws, as a buggy app listener would. */
  private static final class Throwing extends BTEngineAdapter {
    final BlockingQueue<BTDownload> added = new LinkedBlockingQueue<>();
    final BlockingQueue<BTDownload> updated = new LinkedBlockingQueue<>();
    final BlockingQueue<BTDownload> finished = new LinkedBlockingQueue<>();

    @Override
    public void downloadAdded(BTEngine engine, BTDownload dl) {
      added.add(dl);
      throw new IllegalStateException("downloadAdded");
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload dl) {
      updated.add(dl);
      throw new IllegalStateException("downloadUpdate");
    }

    @Override
    public void downloadFinished(BTEngine engine, BTDownload dl) {
      finished.add(dl);
      throw new IllegalStateException("downloadFinished");
    }
  }

  /** Records each call, then throws. */
  private static final class ThrowingDownloadListener implements BTDownloadListener {
    final BlockingQueue<BTDownload> finished = new LinkedBlockingQueue<>();
    final BlockingQueue<BTDownload> removed = new LinkedBlockingQueue<>();

    @Override
    public void finished(BTDownload dl) {
      finished.add(dl);
      throw new IllegalStateException("finished");
    }

    @Override
    public void removed(BTDownload dl, Set<File> incompleteFiles) {
      removed.add(dl);
      throw new IllegalStateException("removed");
    }

    @Override
    public void stateChanged(BTDownload dl) {
      throw new IllegalStateException("stateChanged");
    }
  }
}
