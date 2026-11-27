/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.libtorrent;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.app.Application;
import androidx.test.core.app.ApplicationProvider;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.bittorrent.BTContext;
import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.BTEngineListener;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TorrentBuilder;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.create_flags_t;
import com.frostwire.jlibtorrent.swig.entry;
import com.frostwire.jlibtorrent.swig.save_state_flags_t;
import com.frostwire.jlibtorrent.swig.session_params;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.frostwire.platform.Platforms;
import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * A running {@link BTEngine} of its own, not the application's singleton, with its context in a
 * temporary folder and a libtorrent session that listens on 127.0.0.1 only, with DHT, local
 * discovery, UPnP and NAT-PMP off, so no test reaches the network. {@link #close()} stops the
 * session and clears {@link BTEngine#ctx} again.
 */
public final class TestEngine implements AutoCloseable {

  public static final long TIMEOUT_SECONDS = 30;

  public final BTContext ctx;
  public final BTEngine engine;
  public final Events events = new Events();

  private final Path dir;

  private TestEngine(Path dir, BTContext ctx, BTEngine engine) {
    this.dir = dir;
    this.ctx = ctx;
    this.engine = engine;
    engine.setListener(events);
  }

  /** The folder the engine keeps its files in. */
  public Path dir() {
    return dir;
  }

  /** A started engine whose home, torrents and data folders are under {@code dir}. */
  public static TestEngine start(Path dir) throws Exception {
    TestEngine local = create(dir);
    local.engine.start();
    await(local.engine::isRunning, "the session started");
    return local;
  }

  /** An engine with its context set up but no session, as before startup or after shutdown. */
  public static TestEngine create(Path dir) throws Exception {
    Application app = ApplicationProvider.getApplicationContext();
    Platforms.set(new AndroidPlatform(app));
    BTContext ctx = new BTContext();
    ctx.homeDir = Files.createDirectories(dir.resolve("home")).toFile();
    ctx.torrentsDir = Files.createDirectories(dir.resolve("torrents")).toFile();
    ctx.dataDir = Files.createDirectories(dir.resolve("data")).toFile();
    ctx.interfaces = "127.0.0.1:0";
    ctx.retries = 0;
    ctx.enableDht = false;
    ctx.version[0] = 3;
    ctx.version[1] = 2;
    ctx.version[2] = 1;
    ctx.version[3] = 1;
    writeLocalOnlySettings(new File(ctx.homeDir, "settings.dat"));
    BTEngine.ctx = ctx;
    return new TestEngine(dir, ctx, newEngine());
  }

  /** A fresh engine, never the application's singleton. */
  public static BTEngine newEngine() throws Exception {
    Constructor<BTEngine> constructor = BTEngine.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
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
   * The session state the engine loads from settings.dat on start: nothing talks to the network.
   */
  private static void writeLocalOnlySettings(File settingsFile) throws Exception {
    session_params params = new session_params(localOnlySettings().swig());
    entry state = session_params.write_session_params(params, save_state_flags_t.all());
    state.set("state_version", "1.2.0.6");
    Files.write(settingsFile.toPath(), Vectors.byte_vector2bytes(state.bencode()));
  }

  public static SettingsPack localOnlySettings() {
    SettingsPack sp = new SettingsPack();
    sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), "127.0.0.1:0");
    sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), false);
    sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), false);
    return sp;
  }

  /** A plain session that seeds {@code ti} from {@code dataDir}; stop it when done. */
  public static SessionManager seed(TorrentInfo ti, File dataDir) {
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

  /** Writes {@code size} deterministic bytes to {@code file}, creating its folders. */
  public static File content(Path file, int size, int seed) throws Exception {
    Files.createDirectories(file.getParent());
    byte[] bytes = new byte[size];
    for (int i = 0; i < size; i++) {
      bytes[i] = (byte) (i * 31 + seed);
    }
    Files.write(file, bytes);
    return file.toFile();
  }

  /** The torrent of a file or folder; {@code flags} may be null for the defaults. */
  public static TorrentInfo torrent(File path, create_flags_t flags) throws Exception {
    TorrentBuilder builder = new TorrentBuilder().path(path);
    if (flags != null) {
      builder.flags(flags);
    }
    return TorrentInfo.bdecode(builder.generate().entry().bencode());
  }

  /** Waits for {@code condition}, failing the test after {@link #TIMEOUT_SECONDS}. */
  public static void await(BooleanSupplier condition, String what) {
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
  public static final class Events implements BTEngineListener {
    public final BlockingQueue<BTDownload> added = new LinkedBlockingQueue<>();
    public final BlockingQueue<BTDownload> updated = new LinkedBlockingQueue<>();
    public final BlockingQueue<BTDownload> finished = new LinkedBlockingQueue<>();

    @Override
    public void started(BTEngine engine) {}

    @Override
    public void stopped(BTEngine engine) {}

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

    /** The next download in {@code queue} named {@code name}, skipping others. */
    public static BTDownload next(BlockingQueue<BTDownload> queue, String name) {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
      try {
        while (true) {
          long left = deadline - System.nanoTime();
          BTDownload dl = left > 0 ? queue.poll(left, TimeUnit.NANOSECONDS) : null;
          assertNotNull("timed out waiting for a download named " + name, dl);
          if (name.equals(dl.getName())) {
            return dl;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }
}
