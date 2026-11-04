/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.swig.bitfield;
import com.frostwire.jlibtorrent.swig.file_progress_flags_t;
import com.frostwire.jlibtorrent.swig.int64_vector;
import com.frostwire.jlibtorrent.swig.int_vector;
import com.frostwire.jlibtorrent.swig.resume_data_flags_t;
import com.frostwire.jlibtorrent.swig.sha1_hash;
import com.frostwire.jlibtorrent.swig.status_flags_t;
import com.frostwire.jlibtorrent.swig.torrent_flags_t;
import com.frostwire.jlibtorrent.swig.torrent_handle;
import com.frostwire.jlibtorrent.swig.torrent_info;
import com.frostwire.jlibtorrent.swig.torrent_status;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A libtorrent torrent handle with no native torrent behind it: what BTDownload and BTDownloadItem
 * read comes from its fields, and what they ask of it is recorded. Only the calls they make are
 * answered; any other would reach native code with a null handle, so tests never make them.
 */
final class FakeTorrentHandle extends torrent_handle {
  volatile boolean valid = true;
  volatile FakeStatus status = new FakeStatus();
  volatile RuntimeException statusFailure;
  final TorrentInfo metadata;
  String hash = "0123456789abcdef0123456789abcdef01234567";
  RuntimeException hashFailure;
  int[] priorities;
  RuntimeException priorityFailure;
  long[] progress;
  RuntimeException progressFailure;
  final AtomicInteger resumeDataRequests = new AtomicInteger();
  final List<String> calls = new ArrayList<>();

  FakeTorrentHandle(TorrentInfo metadata) {
    super(0, false);
    this.metadata = metadata;
    int files = metadata != null ? metadata.numFiles() : 0;
    priorities = new int[files];
    java.util.Arrays.fill(priorities, Priority.NORMAL.swig());
    progress = new long[files];
  }

  @Override
  public boolean is_valid() {
    return valid;
  }

  /** No native torrent: the engine routes this handle's alerts by handle comparison. */
  @Override
  public long id() {
    return 0;
  }

  @Override
  public torrent_status status(status_flags_t flags) {
    if (statusFailure != null) {
      throw statusFailure;
    }
    return status;
  }

  @Override
  public torrent_info torrent_file_ptr() {
    return metadata != null ? metadata.swig() : null;
  }

  @Override
  public torrent_info torrent_file_with_hashes_copy() {
    return torrent_file_ptr();
  }

  @Override
  public sha1_hash info_hash() {
    if (hashFailure != null) {
      throw hashFailure;
    }
    return new Sha1Hash(hash).swig();
  }

  @Override
  public int_vector get_file_priorities2() {
    int_vector v = new int_vector();
    for (int p : priorities) {
      v.add(p);
    }
    return v;
  }

  @Override
  public int file_priority2(int index) {
    if (priorityFailure != null) {
      throw priorityFailure;
    }
    return priorities[index];
  }

  @Override
  public void file_priority2(int index, int priority) {
    priorities[index] = priority;
  }

  @Override
  public void file_progress(int64_vector v, file_progress_flags_t flags) {
    if (progressFailure != null) {
      throw progressFailure;
    }
    for (long p : progress) {
      v.add(p);
    }
  }

  @Override
  public void save_resume_data() {
    resumeDataRequests.incrementAndGet();
  }

  @Override
  public void save_resume_data(resume_data_flags_t flags) {
    resumeDataRequests.incrementAndGet();
  }

  @Override
  public boolean need_save_resume_data() {
    return false;
  }

  @Override
  public void pause() {
    record("pause");
  }

  @Override
  public void resume() {
    record("resume");
  }

  @Override
  public void set_flags(torrent_flags_t flags) {
    record("set " + flags.to_int());
  }

  @Override
  public void unset_flags(torrent_flags_t flags) {
    record("unset " + flags.to_int());
  }

  @Override
  public void force_recheck() {
    record("recheck");
  }

  private synchronized void record(String call) {
    calls.add(call);
  }

  synchronized List<String> calls() {
    return new ArrayList<>(calls);
  }

  /** A torrent status whose durations and pieces come from fields, too. */
  static final class FakeStatus extends torrent_status {
    long activeSeconds;
    long seedingSeconds;
    int pieces;
    int havePieces;

    FakeStatus() {
      setSave_path(System.getProperty("java.io.tmpdir"));
      setName("fake");
      setState(state_t.downloading);
      setFlags(new torrent_flags_t());
    }

    @Override
    public long get_active_duration() {
      return activeSeconds;
    }

    @Override
    public long get_seeding_duration() {
      return seedingSeconds;
    }

    @Override
    public bitfield get_pieces() {
      bitfield bits = new bitfield(pieces, false);
      for (int i = 0; i < havePieces; i++) {
        bits.set_bit(i);
      }
      return bits;
    }
  }
}
