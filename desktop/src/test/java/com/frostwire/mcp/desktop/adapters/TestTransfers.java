/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.adapters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.create_torrent;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.file_progress_flags_t;
import com.frostwire.jlibtorrent.swig.file_storage;
import com.frostwire.jlibtorrent.swig.int64_vector;
import com.frostwire.jlibtorrent.swig.int_vector;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.pause_flags_t;
import com.frostwire.jlibtorrent.swig.resume_data_flags_t;
import com.frostwire.jlibtorrent.swig.set_piece_hashes_listener;
import com.frostwire.jlibtorrent.swig.sha1_hash;
import com.frostwire.jlibtorrent.swig.status_flags_t;
import com.frostwire.jlibtorrent.swig.torrent_flags_t;
import com.frostwire.jlibtorrent.swig.torrent_handle;
import com.frostwire.jlibtorrent.swig.torrent_info;
import com.frostwire.jlibtorrent.swig.torrent_status;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Real {@link BTDownload}s for MCP tests, without a BitTorrent session or the Swing transfer table.
 * Each download is made from a real torrent of real files and a {@link FakeHandle} that answers
 * from memory and records what the tools ask of it. The engine is a fresh, unstarted one, so its
 * downloads report STOPPED. {@link #use} makes them the transfers the MCP adapter sees.
 */
public final class TestTransfers {

  private TestTransfers() {}

  /** Makes {@code downloads} the transfers MCP tools see, until {@link #reset()}. */
  public static void use(List<BTDownload> downloads) {
    List<BTDownload> snapshot = List.copyOf(downloads);
    TransferAdapter.setDownloadsSource(() -> snapshot);
  }

  /** Makes MCP tools fail to read the transfers, as when the transfer table is not there. */
  public static void fail(String message) {
    TransferAdapter.setDownloadsSource(
        () -> {
          throw new IllegalStateException(message);
        });
  }

  /** Points MCP tools back at the transfer table. */
  public static void reset() {
    TransferAdapter.setDownloadsSource(null);
  }

  /** A torrent of {@code sizes.length} files, file{i}.bin, created in {@code dir}. */
  public static TorrentInfo torrent(Path dir, String name, int... sizes) throws IOException {
    Path root = Files.createDirectories(dir.resolve(name));
    FileStorage files = new FileStorage(new file_storage());
    for (int i = 0; i < sizes.length; i++) {
      Files.write(root.resolve("file" + i + ".bin"), new byte[sizes[i]]);
      files.addFile(name + "/file" + i + ".bin", sizes[i]);
    }
    // v1 only, so no pad files: sizes and file counts are the ones given.
    create_torrent creator = new create_torrent(files.swig(), 16384, create_torrent.v1_only);
    error_code error = new error_code();
    libtorrent.set_piece_hashes_ex(
        creator,
        dir.toString(),
        new set_piece_hashes_listener() {
          @Override
          public void progress(int piece) {}
        },
        error);
    assertEquals(0, error.value(), error.message());
    return TorrentInfo.bdecode(Vectors.byte_vector2bytes(creator.generate().bencode()));
  }

  /** A download of {@code torrent} saved in {@code saveDir}, with its handle. */
  public static Download download(TorrentInfo torrent, Path saveDir) throws Exception {
    FakeHandle handle = new FakeHandle(torrent, saveDir.toString());
    // Built before the metadata "arrives", so the download keeps no piece tracker: filling one
    // reads the status's piece bitfield, which a status that no session filled in doesn't have.
    handle.metadata = false;
    BTDownload download = new BTDownload(newEngine(), new TorrentHandle(handle));
    handle.metadata = true;
    return new Download(download, handle);
  }

  public record Download(BTDownload download, FakeHandle handle) {
    public String id() {
      return download.getInfoHash();
    }
  }

  private static BTEngine newEngine() throws Exception {
    Constructor<BTEngine> constructor = BTEngine.class.getDeclaredConstructor();
    constructor.setAccessible(true);
    return constructor.newInstance();
  }

  /**
   * A torrent handle that answers from memory. It points at the default (invalid) handle inside a
   * native torrent_status, so native code given it, like the magnet link builder, sees a real
   * handle; every method a transfer calls on it is answered here instead.
   */
  public static final class FakeHandle extends torrent_handle {
    private final torrent_status status;
    private final TorrentInfo torrent;
    public boolean valid = true;
    boolean metadata = true;
    public final List<String> calls = new ArrayList<>();
    public int[] priorities;
    public long[] progress;
    public int downloadLimit = -1;
    public int uploadLimit = -1;

    FakeHandle(TorrentInfo torrent, String savePath) {
      this(new torrent_status(), torrent, savePath);
    }

    private FakeHandle(torrent_status status, TorrentInfo torrent, String savePath) {
      super(getCPtr(status.getHandle()), false);
      this.status = status;
      this.torrent = torrent;
      status.setSave_path(savePath);
      status.setName(torrent.name());
      priorities = new int[torrent.numFiles()];
      Arrays.fill(priorities, Priority.FOUR.swig());
      progress = new long[torrent.numFiles()];
    }

    /** The status the transfer reads: progress, rates, peers and flags. */
    public torrent_status status() {
      return status;
    }

    @Override
    public boolean is_valid() {
      return valid;
    }

    @Override
    public long id() {
      return 0;
    }

    @Override
    public torrent_status status(status_flags_t flags) {
      return status;
    }

    @Override
    public torrent_info torrent_file_ptr() {
      return metadata ? torrent.swig() : null;
    }

    @Override
    public sha1_hash info_hash() {
      return torrent.infoHashV1().swig();
    }

    @Override
    public int_vector get_file_priorities2() {
      int_vector vector = new int_vector();
      for (int priority : priorities) {
        vector.add(priority);
      }
      return vector;
    }

    @Override
    public void prioritize_files2(int_vector files) {
      calls.add("prioritize_files");
      for (int i = 0; i < priorities.length; i++) {
        priorities[i] = files.get(i);
      }
    }

    @Override
    public int file_priority2(int index) {
      return priorities[index];
    }

    @Override
    public void file_progress(int64_vector out, file_progress_flags_t flags) {
      for (long bytes : progress) {
        out.add(bytes);
      }
    }

    @Override
    public void pause() {
      calls.add("pause");
    }

    @Override
    public void pause(pause_flags_t flags) {
      calls.add("pause");
    }

    @Override
    public void resume() {
      calls.add("resume");
    }

    @Override
    public void set_flags(torrent_flags_t flags) {
      calls.add("set_flags");
    }

    @Override
    public void set_flags(torrent_flags_t flags, torrent_flags_t mask) {
      calls.add("set_flags");
    }

    @Override
    public void unset_flags(torrent_flags_t flags) {
      calls.add("unset_flags");
    }

    @Override
    public int download_limit() {
      return downloadLimit;
    }

    @Override
    public void set_download_limit(int limit) {
      downloadLimit = limit;
    }

    @Override
    public int upload_limit() {
      return uploadLimit;
    }

    @Override
    public void set_upload_limit(int limit) {
      uploadLimit = limit;
    }

    @Override
    public void force_reannounce() {
      calls.add("force_reannounce");
    }

    @Override
    public void force_recheck() {
      calls.add("force_recheck");
    }

    @Override
    public void save_resume_data() {}

    @Override
    public void save_resume_data(resume_data_flags_t flags) {}

    @Override
    public boolean need_save_resume_data() {
      return false;
    }
  }
}
