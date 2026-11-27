/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.libtorrent;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentInfo;
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
    TestEngine.content(seedDir.toPath().resolve("album/one.mp3"), 40_000, 1);
    TestEngine.content(seedDir.toPath().resolve("album/two.txt"), 20_000, 2);
    album = TestEngine.torrent(new File(seedDir, "album"), create_torrent.v1_only);
  }

  /** Where {@code album} is seeded; the seeder is stopped by {@link #close()}. */
  public TcpEndpoint seederEndpoint() {
    SessionManager seeder = TestEngine.seed(album, seedDir);
    seeders.add(seeder);
    TestEngine.await(() -> seeder.swig().listen_port() > 0, "the seeder listens");
    return new TcpEndpoint("127.0.0.1", seeder.swig().listen_port());
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
    File saveDir = Files.createDirectories(root.resolve("save" + downloads++)).toFile();
    List<TcpEndpoint> peers = withSeeder ? List.of(seederEndpoint()) : null;
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
