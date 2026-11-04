/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.frostwire.desktop.DesktopPlatform;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentFlags;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.create_flags_t;
import com.frostwire.jlibtorrent.swig.entry;
import com.frostwire.jlibtorrent.swig.save_state_flags_t;
import com.frostwire.jlibtorrent.swig.session_params;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.frostwire.jlibtorrent.swig.string_entry_map;
import com.frostwire.jlibtorrent.swig.string_vector;
import com.frostwire.platform.Platforms;
import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * A {@link BTEngine} of its own, not the application's singleton, with its {@link BTContext} in a
 * temporary folder and a libtorrent session that listens on 127.0.0.1 only, with DHT, local
 * discovery, UPnP and NAT-PMP off. {@link #close()} stops the session and clears {@link
 * BTEngine#ctx} again, so tests that need an engine that isn't set up still see one.
 */
final class LocalEngine implements AutoCloseable {

  static final long TIMEOUT_SECONDS = 30;

  final BTContext ctx;

  /** The running engine; {@link #restart()} replaces it, as a new run of the app does. */
  BTEngine engine;

  final Events events = new Events();

  private LocalEngine(BTContext ctx, BTEngine engine) {
    this.ctx = ctx;
    this.engine = engine;
    engine.setListener(events);
  }

  /** A started engine whose home, torrents and data folders are under {@code dir}. */
  static LocalEngine start(Path dir) throws Exception {
    LocalEngine local = create(dir);
    local.engine.start();
    local.awaitStarted();
    return local;
  }

  /** An engine with its context set up but no session, as before startup or after shutdown. */
  static LocalEngine create(Path dir) throws Exception {
    Platforms.set(new DesktopPlatform());
    BTContext ctx = new BTContext();
    ctx.homeDir = Files.createDirectories(dir.resolve("home")).toFile();
    ctx.torrentsDir = Files.createDirectories(dir.resolve("torrents")).toFile();
    ctx.dataDir = Files.createDirectories(dir.resolve("data")).toFile();
    ctx.interfaces = "127.0.0.1:0";
    ctx.retries = 0;
    ctx.enableDht = false;
    ctx.version[0] = 6;
    ctx.version[1] = 14;
    ctx.version[2] = 2;
    ctx.version[3] = 321;
    writeLocalOnlySettings(new File(ctx.homeDir, "settings.dat"));
    BTEngine.ctx = ctx;
    return new LocalEngine(ctx, newEngine());
  }

  /** A fresh engine, never the application's singleton. */
  static BTEngine newEngine() throws Exception {
    Constructor<BTEngine> constructor = BTEngine.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }

  /**
   * Stops the engine and starts a new one on the same folders, as quitting and opening the app
   * again does: nothing carries over but the files.
   */
  void restart() throws Exception {
    restart(() -> {});
  }

  /** {@link #restart()}, doing {@code whileStopped} between quitting and opening the app. */
  void restart(ThrowingRunnable whileStopped) throws Exception {
    engine.stop();
    whileStopped.run();
    events.clear();
    engine = newEngine();
    engine.setListener(events);
    engine.start();
    awaitStarted();
  }

  interface ThrowingRunnable {
    void run() throws Exception;
  }

  private void awaitStarted() {
    await(engine::isRunning, "the session started");
  }

  @Override
  public void close() {
    try {
      engine.stop();
    } finally {
      BTEngine.ctx = null;
    }
  }

  /**
   * The session state the engine loads from settings.dat on start: local discovery, UPnP and
   * NAT-PMP off, so no test sends anything to the network.
   */
  static void writeLocalOnlySettings(File settingsFile) throws Exception {
    writeSettings(settingsFile, localOnlySettings(), "1.2.0.6");
  }

  /** Writes settings.dat as the engine saves it, tagged with {@code stateVersion}. */
  static void writeSettings(File settingsFile, SettingsPack sp, String stateVersion)
      throws Exception {
    session_params params = new session_params(sp.swig());
    entry state = session_params.write_session_params(params, save_state_flags_t.all());
    state.set("state_version", stateVersion);
    Files.write(settingsFile.toPath(), Vectors.byte_vector2bytes(state.bencode()));
  }

  static SettingsPack localOnlySettings() {
    SettingsPack sp = new SettingsPack();
    sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), "127.0.0.1:0");
    sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false);
    return sp;
  }

  /** A plain session that seeds {@code ti} from {@code dataDir}; stop it when done. */
  static SessionManager seed(TorrentInfo ti, File dataDir) {
    SessionManager seeder = new SessionManager(false);
    seeder.start(new SessionParams(localOnlySettings()));
    seeder.download(ti, dataDir);
    await(
        () -> {
          var th = seeder.find(ti);
          return th != null && th.isValid() && th.status(true).isSeeding();
        },
        "the seeder checked its files");
    return seeder;
  }

  /** Where {@code session} accepts peers. */
  static TcpEndpoint endpoint(SessionManager session) {
    await(() -> session.swig().listen_port() > 0, "the session listens");
    return new TcpEndpoint("127.0.0.1", session.swig().listen_port());
  }

  /** Writes {@code size} deterministic bytes to {@code file}, creating its folders. */
  static File content(Path file, int size, int seed) throws Exception {
    Files.createDirectories(file.getParent());
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = (byte) (i * 31 + seed);
    }
    Files.write(file, bytes);
    return file.toFile();
  }

  /** The torrent of a file or folder, hybrid unless {@code flags} say otherwise. */
  static TorrentInfo torrent(File path, create_flags_t flags) throws Exception {
    TorrentBuilder builder = new TorrentBuilder().path(path);
    if (flags != null) {
      builder.flags(flags);
    }
    return TorrentInfo.bdecode(builder.generate().entry().bencode());
  }

  /** Writes {@code ti} to a .torrent file. */
  static File torrentFile(TorrentInfo ti, Path file) throws Exception {
    Files.createDirectories(file.getParent());
    Files.write(file, ti.bencode());
    return file.toFile();
  }

  /** Whether libtorrent has {@code dl}'s torrent paused right now, not as last cached. */
  static boolean isPaused(BTDownload dl) {
    return dl.getTorrentHandle().status(true).flags().and_(TorrentFlags.PAUSED).nonZero();
  }

  /** The extra data FrostWire keeps in a resume file, or an empty map without one. */
  static Map<String, String> resumeExtra(File resumeFile) {
    Map<String, String> extra = new HashMap<>();
    try {
      entry resume =
          entry.bdecode(Vectors.bytes2byte_vector(Files.readAllBytes(resumeFile.toPath())));
      if (resume.dict().containsKey("extra_data")) {
        string_entry_map dict = resume.dict().get("extra_data").dict();
        for (String key : new string_vector(dict.keySet())) {
          extra.put(key, dict.get(key).string());
        }
      }
    } catch (Exception missingOrBeingWritten) {
      // nothing saved yet
    }
    return extra;
  }

  static void await(BooleanSupplier condition, String what) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        fail("timed out waiting until " + what);
      }
      try {
        Thread.sleep(20);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        fail("interrupted waiting until " + what);
      }
    }
  }

  /** Records what the engine reports to its listener. */
  static final class Events implements BTEngineListener {
    final AtomicInteger started = new AtomicInteger();
    final AtomicInteger stopped = new AtomicInteger();
    final BlockingQueue<BTDownload> added = new LinkedBlockingQueue<>();
    final BlockingQueue<BTDownload> updated = new LinkedBlockingQueue<>();
    final BlockingQueue<BTDownload> finished = new LinkedBlockingQueue<>();

    @Override
    public void started(BTEngine engine) {
      started.incrementAndGet();
    }

    @Override
    public void stopped(BTEngine engine) {
      stopped.incrementAndGet();
    }

    @Override
    public void downloadAdded(BTEngine engine, BTDownload dl) {
      added.add(dl);
    }

    @Override
    public void downloadUpdate(BTEngine engine, BTDownload dl) {
      updated.add(dl);
    }

    @Override
    public void downloadFinished(BTEngine engine, BTDownload dl) {
      finished.add(dl);
    }

    void clear() {
      added.clear();
      updated.clear();
      finished.clear();
    }

    /** The next download in {@code queue} whose name is {@code name}, skipping others. */
    static BTDownload next(BlockingQueue<BTDownload> queue, String name) {
      return next(queue, dl -> name.equals(dl.getName()), "a download named " + name);
    }

    static BTDownload next(
        BlockingQueue<BTDownload> queue, Predicate<BTDownload> match, String what) {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
      try {
        while (true) {
          long left = deadline - System.nanoTime();
          BTDownload dl = left > 0 ? queue.poll(left, TimeUnit.NANOSECONDS) : null;
          assertNotNull(dl, "timed out waiting for " + what);
          if (match.test(dl)) {
            return dl;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }

    static boolean none(BlockingQueue<BTDownload> queue, String name, long millis)
        throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
      while (true) {
        long left = deadline - System.nanoTime();
        BTDownload dl = left > 0 ? queue.poll(left, TimeUnit.NANOSECONDS) : null;
        if (dl == null) {
          return true;
        }
        if (name.equals(dl.getName())) {
          return false;
        }
      }
    }
  }

  static List<TcpEndpoint> peers(SessionManager... sessions) {
    return java.util.Arrays.stream(sessions).map(LocalEngine::endpoint).toList();
  }
}
