/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.libtorrent;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.TorrentStatus;
import com.frostwire.jlibtorrent.swig.create_torrent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A two-file album (one.mp3, two.txt) made into a torrent, a real running {@link TestEngine} that
 * downloads it, and seeders on 127.0.0.1 that can feed that download. Nothing leaves the machine.
 */
public final class LoopbackTorrent implements AutoCloseable {

  public final TestEngine local;
  public final TorrentInfo album;
  public final File seedDir;
  private final Path root;
  private final List<SessionManager> seeders = new ArrayList<>();
  private int downloads;

  /** Starts the engine and builds the torrent under {@code root}. */
  public LoopbackTorrent(Path root) throws Exception {
    this.root = root;
    local = TestEngine.start(root.resolve("engine"));
    seedDir = root.resolve("seed").toFile();
    // different bytes, so a different info hash, in every test: the application's engine, which
    // every test restarts, remembers per-torrent settings (such as a pause) by info hash
    int salt = (int) System.nanoTime();
    TestEngine.content(seedDir.toPath().resolve("album/one.mp3"), 40_000, salt);
    TestEngine.content(seedDir.toPath().resolve("album/two.txt"), 20_000, salt + 1);
    album = TestEngine.torrent(new File(seedDir, "album"), create_torrent.v1_only);
  }

  /** Where {@code album} is seeded; the seeder is stopped by {@link #close()}. */
  public TcpEndpoint seederEndpoint() {
    SessionManager seeder = TestEngine.seed(album, seedDir);
    seeders.add(seeder);
    TestEngine.await(() -> seeder.swig().listen_port() > 0, "the seeder listens");
    return new TcpEndpoint("127.0.0.1", seeder.swig().listen_port());
  }

  /**
   * A peer that has only {@code one.mp3}: a download from it gets that file and then waits for the
   * rest, so the peer stays connected to the download instead of leaving when it is done.
   */
  public TcpEndpoint partialSeederEndpoint() throws Exception {
    File partialDir = Files.createDirectories(root.resolve("partial")).toFile();
    TestEngine.content(
        partialDir.toPath().resolve("album/one.mp3"),
        40_000,
        saltOf(new File(seedDir, "album/one.mp3")));
    SessionManager peer = new SessionManager(false);
    peer.start(new SessionParams(TestEngine.localOnlySettings()));
    seeders.add(peer);
    peer.download(album, partialDir);
    TestEngine.await(
        () -> {
          TorrentHandle th = peer.find(album);
          return th != null
              && th.isValid()
              && th.status(true).state() == TorrentStatus.State.DOWNLOADING;
        },
        "the partial peer checked its file");
    TestEngine.await(() -> peer.swig().listen_port() > 0, "the partial peer listens");
    return new TcpEndpoint("127.0.0.1", peer.swig().listen_port());
  }

  private static int saltOf(File mp3) throws Exception {
    // the album's first byte is (0 * 31 + salt)
    return Files.readAllBytes(mp3.toPath())[0];
  }

  /** The index of the file whose path ends with {@code name}. */
  public int fileIndex(String name) {
    for (int i = 0; i < album.numFiles(); i++) {
      if (album.files().filePath(i).endsWith(name)) {
        return i;
      }
    }
    throw new AssertionError("no file named " + name);
  }

  /**
   * The engine's download of {@code album} into a folder of its own (one per call, so a second
   * download of the same torrent starts from nothing), optionally fed by a local seeder.
   */
  public BTDownload add(boolean[] selection, boolean withSeeder) throws Exception {
    return add(selection, withSeeder ? List.of(seederEndpoint()) : null);
  }

  /** {@link #add(boolean[], boolean)} fed by the given peers. */
  public BTDownload add(boolean[] selection, List<TcpEndpoint> peers) throws Exception {
    File saveDir = Files.createDirectories(root.resolve("save" + downloads++)).toFile();
    local.engine.download(album, saveDir, selection, peers);
    return TestEngine.Events.next(local.events.added, "album");
  }

  @Override
  public void close() {
    for (SessionManager seeder : seeders) {
      seeder.stop();
    }
    local.close();
  }
}
