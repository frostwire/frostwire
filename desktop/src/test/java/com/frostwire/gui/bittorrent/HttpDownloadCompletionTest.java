/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.*;

import com.frostwire.gui.DigestUtils;
import com.frostwire.transfers.TransferState;
import com.limegroup.gnutella.settings.SharingSettings;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HttpDownloadCompletionTest {
  private static final byte[] VIDEO = "silent video".getBytes(StandardCharsets.UTF_8);
  private static final byte[] MUXED =
      "video with audio, different bytes and size".getBytes(StandardCharsets.UTF_8);
  @TempDir Path directory;
  private File previousDirectory;
  private boolean previousAutoSeed;

  @BeforeEach
  void setUp() throws IOException {
    previousDirectory = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
    previousAutoSeed = SharingSettings.SEED_FINISHED_TORRENTS.getValue();
    SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(
        Files.createDirectory(directory.resolve("complete")).toFile());
    SharingSettings.SEED_FINISHED_TORRENTS.setValue(true);
  }

  @AfterEach
  void tearDown() {
    SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(previousDirectory);
    SharingSettings.SEED_FINISHED_TORRENTS.setValue(previousAutoSeed);
  }

  @Test
  void seedsReplacementBytesOnlyAfterPostprocessingReturnsExactlyOnce() throws Exception {
    assertCompletion(false, false);
  }

  @Test
  void handledMuxFailureSeedsOriginalVideoOnlyAfterFallbackReturnsExactlyOnce() throws Exception {
    assertCompletion(true, false);
  }

  @Test
  void verifiedExistingFileAlsoWaitsForPostprocessing() throws Exception {
    assertCompletion(false, true);
  }

  private void assertCompletion(boolean muxFails, boolean cached) throws Exception {
    try (Fixture fixture = new Fixture(muxFails, cached)) {
      Future<?> completion = fixture.runCompletion(cached);
      try {
        assertTrue(
            fixture.entered.await(5, TimeUnit.SECONDS), "Real completion hook was not entered");
        assertArrayEquals(VIDEO, Files.readAllBytes(fixture.download.getSaveLocation().toPath()));
        assertNotEquals(TransferState.FINISHED, fixture.download.getState());
        assertFalse(fixture.download.isCompleted(), "Byte count alone must not publish completion");
        assertEquals(0, fixture.seedCalls.get(), "Seeding must not start during the mux");
        // A duplicate listener notification while the first completion is blocked must be ignored.
        fixture.download.httpClient.getListener().onComplete(fixture.download.httpClient);
        assertEquals(1, fixture.postprocessCalls.get());
      } finally {
        fixture.release.countDown();
      }
      completion.get(5, TimeUnit.SECONDS);
      fixture.download.httpClient.getListener().onComplete(fixture.download.httpClient);
      byte[] expected = muxFails ? VIDEO : MUXED;
      assertArrayEquals(expected, Files.readAllBytes(fixture.download.getSaveLocation().toPath()));
      if (!cached) {
        assertArrayEquals(
            MessageDigest.getInstance("SHA-256").digest(expected), fixture.seedHash.get());
      }
      assertEquals(TransferState.FINISHED, fixture.download.getState());
      assertTrue(fixture.download.isCompleted());
      assertEquals(expected.length, fixture.download.getSize());
      assertEquals(expected.length, fixture.download.getBytesReceived());
      assertEquals(1, fixture.postprocessCalls.get());
      assertEquals(cached ? 0 : 1, fixture.seedCalls.get());
      assertEquals(muxFails ? 1 : 0, fixture.fallbackCalls.get());
      assertFalse(fixture.incomplete.exists());
    }
  }

  @Test
  void cancellationDuringPostprocessingDoesNotPublishOrSeed() throws Exception {
    assertCancellation(true);
  }

  @Test
  void cancellationCanRetainDataWithoutPublishingOrSeeding() throws Exception {
    assertCancellation(false);
  }

  private void assertCancellation(boolean deleteData) throws Exception {
    try (Fixture fixture = new Fixture(false, false, null, deleteData)) {
      Future<?> completion = fixture.runCompletion(false);
      try {
        assertTrue(fixture.entered.await(5, TimeUnit.SECONDS));
        fixture.download.pause();
      } finally {
        fixture.release.countDown();
      }
      completion.get(5, TimeUnit.SECONDS);
      assertEquals(TransferState.CANCELED, fixture.download.getState());
      assertFalse(fixture.download.isCompleted());
      assertEquals(0, fixture.seedCalls.get());
      assertEquals(!deleteData, fixture.download.getSaveLocation().exists());
      if (!deleteData) {
        assertArrayEquals(MUXED, Files.readAllBytes(fixture.download.getSaveLocation().toPath()));
      }
    }
  }

  @Test
  void cancellationBeforeListenerCompletionKeepsCancellationCleanup() throws Exception {
    try (Fixture fixture = new Fixture(false, false)) {
      Files.write(fixture.incomplete.toPath(), VIDEO);
      fixture.download.pause();
      fixture.download.httpClient.getListener().onComplete(fixture.download.httpClient);
      assertEquals(TransferState.CANCELED, fixture.download.getState());
      assertFalse(fixture.incomplete.exists());
      assertEquals(0, fixture.postprocessCalls.get());
      assertEquals(0, fixture.seedCalls.get());
    }
  }

  @Test
  void consumedFileIsNotSeededAfterSlideStylePostprocessing() throws Exception {
    AtomicInteger postprocessCalls = new AtomicInteger();
    AtomicInteger seedCalls = new AtomicInteger();
    HttpDownload download =
        new HttpDownload(
            "http://127.0.0.1:1/unused",
            "slide",
            "slide.zip",
            VIDEO.length,
            null,
            false,
            true,
            null,
            task -> {},
            dl -> seedCalls.incrementAndGet()) {
          @Override
          void onComplete() {
            postprocessCalls.incrementAndGet();
            assertTrue(getSaveLocation().delete());
          }
        };
    File incomplete = new File(HttpBTDownload.getIncompleteFolder(), "slide.incomplete.zip");
    Files.write(incomplete.toPath(), VIDEO);
    download.httpClient.getListener().onData(download.httpClient, VIDEO, 0, VIDEO.length);
    download.httpClient.getListener().onComplete(download.httpClient);
    download.httpClient.getListener().onComplete(download.httpClient);
    assertTrue(download.isCompleted());
    assertEquals(1, postprocessCalls.get());
    assertEquals(0, seedCalls.get());
    assertFalse(download.getSaveLocation().exists());
  }

  @Test
  void validatesDownloadedMd5BeforeReplacingBytes() throws Exception {
    Path source = Files.write(directory.resolve("source.mp4"), VIDEO);
    try (Fixture fixture = new Fixture(false, false, DigestUtils.getMD5(source.toFile()))) {
      fixture.release.countDown();
      fixture.runCompletion(false).get(5, TimeUnit.SECONDS);
      assertArrayEquals(MUXED, Files.readAllBytes(fixture.download.getSaveLocation().toPath()));
      assertTrue(fixture.download.isCompleted());
      assertEquals(1, fixture.postprocessCalls.get());
      assertEquals(1, fixture.seedCalls.get());
    }
  }

  @Test
  void md5MismatchDoesNotPostprocessMoveOrSeed() throws Exception {
    try (Fixture fixture = new Fixture(false, false, "00000000000000000000000000000000")) {
      fixture.runCompletion(false).get(5, TimeUnit.SECONDS);
      assertEquals(TransferState.ERROR_HASH_MD5, fixture.download.getState());
      assertEquals(0, fixture.postprocessCalls.get());
      assertEquals(0, fixture.seedCalls.get());
      assertFalse(fixture.incomplete.exists());
      assertFalse(fixture.download.getSaveLocation().exists());
    }
  }

  @Test
  void moveFailureDoesNotPostprocessOrSeed() throws Exception {
    try (Fixture fixture = new Fixture(false, false)) {
      Path destination = fixture.download.getSaveLocation().toPath();
      Files.createDirectory(destination);
      Files.write(destination.resolve("prevent-replacement"), VIDEO);
      fixture.runCompletion(false).get(5, TimeUnit.SECONDS);
      assertEquals(TransferState.ERROR_MOVING_INCOMPLETE, fixture.download.getState());
      assertEquals(0, fixture.postprocessCalls.get());
      assertEquals(0, fixture.seedCalls.get());
      assertTrue(fixture.incomplete.exists());
    }
  }

  @Test
  void disabledAutoSeedStillCompletesPostprocessing() throws Exception {
    SharingSettings.SEED_FINISHED_TORRENTS.setValue(false);
    try (Fixture fixture = new Fixture(false, false)) {
      fixture.release.countDown();
      fixture.runCompletion(false).get(5, TimeUnit.SECONDS);
      assertArrayEquals(MUXED, Files.readAllBytes(fixture.download.getSaveLocation().toPath()));
      assertTrue(fixture.download.isCompleted());
      assertEquals(1, fixture.postprocessCalls.get());
      assertEquals(0, fixture.seedCalls.get());
    }
  }

  // Intercept only scheduling and the UI/native seeder. File validation, move, completion hook,
  // cancellation and publication all run through HttpDownload's installed production listener.
  private final class Fixture implements AutoCloseable {
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    final AtomicInteger postprocessCalls = new AtomicInteger();
    final AtomicInteger seedCalls = new AtomicInteger();
    final AtomicInteger fallbackCalls = new AtomicInteger();
    final AtomicReference<byte[]> seedHash = new AtomicReference<>();
    final AtomicReference<Runnable> scheduled = new AtomicReference<>();
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final HttpDownload download;
    final File incomplete;

    Fixture(boolean muxFails, boolean cached) throws IOException {
      this(muxFails, cached, null);
    }

    Fixture(boolean muxFails, boolean cached, String md5) throws IOException {
      this(muxFails, cached, md5, true);
    }

    Fixture(boolean muxFails, boolean cached, String md5, boolean deleteData) throws IOException {
      File complete = new File(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), "video.mp4");
      if (cached) {
        Files.write(complete.toPath(), VIDEO);
        md5 = DigestUtils.getMD5(complete);
      }
      download =
          new HttpDownload(
              "http://127.0.0.1:1/unused",
              "video",
              "video.mp4",
              VIDEO.length,
              md5,
              false,
              deleteData,
              null,
              scheduled::set,
              dl -> {
                assertEquals(TransferState.FINISHED, dl.getState());
                assertTrue(dl.isCompleted());
                seedCalls.incrementAndGet();
                try {
                  seedHash.set(
                      MessageDigest.getInstance("SHA-256")
                          .digest(Files.readAllBytes(dl.getSaveLocation().toPath())));
                } catch (Exception e) {
                  throw new AssertionError(e);
                }
              }) {
            @Override
            void onComplete() {
              postprocessCalls.incrementAndGet();
              entered.countDown();
              try {
                assertTrue(release.await(5, TimeUnit.SECONDS), "Postprocessing was not released");
                // Same replacement/fallback contract as muxDashAudio; no native muxer or UI.
                if (muxFails) {
                  throw new IOException("Audio fetch/mux failed");
                }
                Path merged = directory.resolve("merged.mp4");
                Files.write(merged, MUXED);
                Files.move(merged, getSaveLocation().toPath(), StandardCopyOption.REPLACE_EXISTING);
              } catch (IOException e) {
                fallbackCalls.incrementAndGet();
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
              }
            }
          };
      incomplete = new File(HttpBTDownload.getIncompleteFolder(), "video.incomplete.mp4");
    }

    Future<?> runCompletion(boolean cached) throws IOException {
      if (cached) {
        return worker.submit(scheduled.get());
      }
      Files.write(incomplete.toPath(), VIDEO);
      download.httpClient.getListener().onData(download.httpClient, VIDEO, 0, VIDEO.length);
      return worker.submit(() -> download.httpClient.getListener().onComplete(download.httpClient));
    }

    @Override
    public void close() throws InterruptedException {
      release.countDown();
      worker.shutdownNow();
      assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
    }
  }
}
