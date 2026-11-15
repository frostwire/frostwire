/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.android.gui.fragments;

import com.frostwire.jlibtorrent.FileSlice;
import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.PartialPieceInfo;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.Sha1Hash;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the Pieces tab shows about one piece, the same as desktop's piece card: read off the main
 * thread with {@link #read}, then shown by {@link PieceDetailsPanel}. Immutable.
 */
final class PieceDetails {
  enum State {
    DOWNLOADED,
    DOWNLOADING,
    NOT_DOWNLOADING,
    MISSING
  }

  /** A file the piece holds bytes of. */
  static final class FilePart {
    final String name;
    final long size;

    FilePart(String name, long size) {
      this.name = name;
      this.size = size;
    }
  }

  /** Files listed for a piece, the others are counted in {@link #moreFiles}. */
  static final int MAX_LISTED_FILES = 4;

  final String infoHash;
  final int piece;
  final int numPieces;
  final State state;

  /** Blocks of a piece being downloaded: finished, all of them and requested ones. */
  final int finishedBlocks;

  final int blocks;
  final int requestedBlocks;
  final int size;
  final long firstByte;

  /** Peers that have the piece, -1 when libtorrent doesn't say. */
  final int peers;

  final Priority priority;

  /** Null for a v2-only torrent, which has no per-piece SHA-1. */
  final String sha1;

  final List<FilePart> files;
  final int moreFiles;

  PieceDetails(
      String infoHash,
      int piece,
      int numPieces,
      State state,
      int finishedBlocks,
      int blocks,
      int requestedBlocks,
      int size,
      long firstByte,
      int peers,
      Priority priority,
      String sha1,
      List<FilePart> files,
      int moreFiles) {
    this.infoHash = infoHash;
    this.piece = piece;
    this.numPieces = numPieces;
    this.state = state;
    this.finishedBlocks = finishedBlocks;
    this.blocks = blocks;
    this.requestedBlocks = requestedBlocks;
    this.size = size;
    this.firstByte = firstByte;
    this.peers = peers;
    this.priority = priority;
    this.sha1 = sha1;
    this.files = Collections.unmodifiableList(new ArrayList<>(files));
    this.moreFiles = moreFiles;
  }

  boolean downloaded() {
    return state == State.DOWNLOADED;
  }

  /**
   * Reads a piece's details from libtorrent, never on the main thread.
   *
   * @return null when the handle is gone or there's no such piece
   */
  static PieceDetails read(String infoHash, TorrentHandle handle, TorrentInfo info, int piece) {
    if (handle == null
        || info == null
        || !handle.isValid()
        || piece < 0
        || piece >= info.numPieces()) {
      return null;
    }
    Priority priority = handle.piecePriority(piece);
    State state = State.MISSING;
    int finished = 0;
    int blocks = 0;
    int requested = 0;
    if (handle.havePiece(piece)) {
      state = State.DOWNLOADED;
    } else {
      for (PartialPieceInfo partial : handle.getDownloadQueue()) {
        if (partial.pieceIndex() == piece) {
          state = State.DOWNLOADING;
          finished = partial.finished();
          blocks = partial.blocksInPiece();
          requested = partial.requested();
          break;
        }
      }
      if (state == State.MISSING && priority == Priority.IGNORE) {
        state = State.NOT_DOWNLOADING;
      }
    }
    int size = info.pieceSize(piece);
    int[] availability = handle.pieceAvailability();
    int peers = availability != null && piece < availability.length ? availability[piece] : -1;
    Sha1Hash hash = info.hashForPiece(piece);
    String sha1 = hash != null && !hash.isAllZeros() ? hash.toHex() : null;
    List<FilePart> files = new ArrayList<>();
    int moreFiles = 0;
    FileStorage storage = info.files();
    for (FileSlice slice : info.mapBlock(piece, 0, size)) {
      if (isPadFile(storage.filePath(slice.fileIndex()))) {
        continue; // BEP 47 padding files aren't real content
      }
      if (files.size() < MAX_LISTED_FILES) {
        files.add(new FilePart(storage.fileName(slice.fileIndex()), slice.size()));
      } else {
        moreFiles++;
      }
    }
    return new PieceDetails(
        infoHash,
        piece,
        info.numPieces(),
        state,
        finished,
        blocks,
        requested,
        size,
        (long) piece * info.pieceLength(),
        peers,
        priority,
        sha1,
        files,
        moreFiles);
  }

  static boolean isPadFile(String path) {
    return path != null
        && (path.startsWith(".pad") || path.contains("/.pad/") || path.contains("\\.pad\\"));
  }
}
