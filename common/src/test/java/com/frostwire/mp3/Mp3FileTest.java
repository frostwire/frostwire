/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static com.frostwire.mp3.MpegFrameTest.LAYER_III;
import static com.frostwire.mp3.MpegFrameTest.MPEG_2;
import static com.frostwire.mp3.MpegFrameTest.MPEG_2_5;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Mp3File finds the MPEG frames of a file between its tags, and saves them with new tags. */
class Mp3FileTest {

  @TempDir Path dir;

  /**
   * MPEG-2 and 2.5 Layer III frames are half the length of MPEG-1 ones. They were measured as if
   * they were MPEG-1, so the scan jumped into the middle of the next frame, kept only the last one,
   * and saving the file with new tags dropped the rest of the audio.
   */
  @Test
  void mpeg2AndMpeg25LayerIIIFilesKeepAllTheirFrames() throws Exception {
    // 64 kbps at 22050 Hz (208 bytes) and 32 kbps at 8000 Hz (288 bytes).
    assertAllFramesFound(MpegFrameTest.header(MPEG_2, LAYER_III, 8, 0, 0, 0), 208);
    assertAllFramesFound(MpegFrameTest.header(MPEG_2_5, LAYER_III, 4, 2, 0, 0), 288);
  }

  private void assertAllFramesFound(byte[] header, int frameLength) throws Exception {
    byte[] audio = frames(header, frameLength, 20);
    Path mp3 = write("in.mp3", audio);
    Mp3File file = new Mp3File(mp3.toString());
    assertEquals(20, file.getFrameCount());
    assertEquals(0, file.getStartOffset());
    assertEquals(audio.length - 1, file.getEndOffset());
    Path saved = dir.resolve("out.mp3");
    file.save(saved.toString());
    assertArrayEquals(audio, Files.readAllBytes(saved));
  }

  /** {@code count} frames of {@code length} bytes, each a header followed by silence. */
  static byte[] frames(byte[] header, int length, int count) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (int i = 0; i < count; i++) {
      out.writeBytes(header);
      out.writeBytes(new byte[length - header.length]);
    }
    return out.toByteArray();
  }

  private Path write(String name, byte[] content) throws IOException {
    return Files.write(dir.resolve(name), content);
  }
}
