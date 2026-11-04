/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** MPEG audio frame headers: the four bytes that start every frame of an MP3. */
class MpegFrameTest {

  static final int MPEG_2_5 = 0;
  static final int MPEG_2 = 2;
  static final int MPEG_1 = 3;
  static final int LAYER_III = 1;
  static final int LAYER_II = 2;
  static final int LAYER_I = 3;

  /**
   * A frame's length is its samples over 8, times the bitrate over the sample rate, plus padding.
   * Layer I frames hold 384 samples in whole 4-byte slots, Layer II 1152, and Layer III 1152 in
   * MPEG-1 but 576 in MPEG-2 and 2.5, so those frames are half as long.
   */
  @ParameterizedTest
  @CsvSource({
    // version, layer, bitrate index, sample rate index, padding, length
    "3, 1, 9, 0, false, 417", // MPEG-1 Layer III, 128 kbps, 44100 Hz
    "3, 1, 9, 0, true, 418",
    "3, 1, 14, 1, false, 960", // 320 kbps, 48000 Hz
    "3, 2, 12, 2, false, 1152", // MPEG-1 Layer II, 256 kbps, 32000 Hz
    "3, 3, 1, 1, false, 32", // MPEG-1 Layer I, 32 kbps, 48000 Hz
    "3, 3, 1, 1, true, 36",
    "2, 1, 8, 0, false, 208", // MPEG-2 Layer III, 64 kbps, 22050 Hz
    "2, 1, 8, 0, true, 209",
    "2, 1, 4, 2, false, 144", // 32 kbps, 16000 Hz
    "0, 1, 4, 2, false, 288", // MPEG-2.5 Layer III, 32 kbps, 8000 Hz
    "0, 1, 8, 0, false, 417", // 64 kbps, 11025 Hz
    "2, 2, 8, 0, false, 417", // MPEG-2 Layer II, 64 kbps, 22050 Hz
    "3, 3, 1, 0, false, 32", // MPEG-1 Layer I, 32 kbps, 44100 Hz: 8 whole slots
    "2, 3, 8, 0, false, 276", // MPEG-2 Layer I, 128 kbps, 22050 Hz: 69 whole slots
  })
  void frameLengthsFollowTheSamplesPerFrame(
      int version, int layer, int bitrateIndex, int sampleRateIndex, boolean padding, int length)
      throws InvalidDataException {
    MpegFrame frame =
        new MpegFrame(header(version, layer, bitrateIndex, sampleRateIndex, padding ? 1 : 0, 0));
    assertEquals(length, frame.getLengthInBytes());
  }

  /** Four header bytes: sync, version, layer, no CRC, bitrate, sample rate, padding, channels. */
  static byte[] header(
      int version, int layer, int bitrateIndex, int sampleRateIndex, int padding, int channelMode) {
    return header(
        version, layer, 1, bitrateIndex, sampleRateIndex, padding, 0, channelMode, 0, 0, 0, 0);
  }

  static byte[] header(
      int version,
      int layer,
      int protection,
      int bitrateIndex,
      int sampleRateIndex,
      int padding,
      int privateBit,
      int channelMode,
      int modeExtension,
      int copyright,
      int original,
      int emphasis) {
    return new byte[] {
      (byte) 0xff,
      (byte) (0xe0 | version << 3 | layer << 1 | protection),
      (byte) (bitrateIndex << 4 | sampleRateIndex << 2 | padding << 1 | privateBit),
      (byte) (channelMode << 6 | modeExtension << 4 | copyright << 3 | original << 2 | emphasis)
    };
  }
}
