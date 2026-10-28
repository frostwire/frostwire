/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp4;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;

/**
 * Fuzzes ISO-BMFF (MP4/M4A) box parsing the way {@link IsoFile#head} does when the desktop library
 * reads tags of a downloaded file ({@code MP4Parser}), but from memory instead of a {@code
 * RandomAccessFile} so each run is fast. {@link IsoMedia#read} declares only {@link IOException}
 * (truncated or inconsistent boxes); any other throwable is a bug.
 */
class IsoMediaReadFuzzTest {

  // Same buffer size the callers use (MP4Parser, Mp4Demuxer).
  private static final int BUFFER_SIZE = 100 * 1024;

  @FuzzTest
  void head(FuzzedDataProvider data) {
    byte[] bytes = data.consumeRemainingAsBytes();
    InputChannel ch = new InputChannel(Channels.newChannel(new ByteArrayInputStream(bytes)));
    try {
      IsoMedia.read(ch, ByteBuffer.allocate(BUFFER_SIZE), b -> b.type != Box.mdat);
    } catch (IOException expected) {
      // Documented rejection of a truncated or inconsistent file.
    }
  }
}
