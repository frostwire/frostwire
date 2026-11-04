/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

  /** The bitrate tables of ISO 11172-3 and 13818-3, in kbps, for bitrate indexes 1 to 14. */
  @ParameterizedTest
  @CsvSource({
    "3, 3, 32 64 96 128 160 192 224 256 288 320 352 384 416 448",
    "3, 2, 32 48 56 64 80 96 112 128 160 192 224 256 320 384",
    "3, 1, 32 40 48 56 64 80 96 112 128 160 192 224 256 320",
    "2, 3, 32 48 56 64 80 96 112 128 144 160 176 192 224 256",
    "2, 2, 8 16 24 32 40 48 56 64 80 96 112 128 144 160",
    "2, 1, 8 16 24 32 40 48 56 64 80 96 112 128 144 160",
    "0, 3, 32 48 56 64 80 96 112 128 144 160 176 192 224 256",
    "0, 2, 8 16 24 32 40 48 56 64 80 96 112 128 144 160",
    "0, 1, 8 16 24 32 40 48 56 64 80 96 112 128 144 160",
  })
  void bitratesFollowTheVersionAndLayer(int version, int layer, String kbps)
      throws InvalidDataException {
    String[] expected = kbps.split(" ");
    for (int index = 1; index <= 14; index++) {
      MpegFrame frame = new MpegFrame(header(version, layer, index, 0, 0, 0));
      assertEquals(Integer.parseInt(expected[index - 1]), frame.getBitrate(), "index " + index);
    }
  }

  /** Index 0 is "free format" (no fixed bitrate) and 15 is forbidden: neither can be scanned. */
  @ParameterizedTest
  @ValueSource(ints = {0, 15})
  void freeAndForbiddenBitratesAreInvalid(int index) {
    for (int version : new int[] {MPEG_1, MPEG_2, MPEG_2_5}) {
      for (int layer : new int[] {LAYER_I, LAYER_II, LAYER_III}) {
        assertThrows(
            InvalidDataException.class,
            () -> new MpegFrame(header(version, layer, index, 0, 0, 0)));
      }
    }
  }

  @ParameterizedTest
  @CsvSource({
    "3, 0, 44100, 1.0",
    "3, 1, 48000, 1.0",
    "3, 2, 32000, 1.0",
    "2, 0, 22050, 2.0",
    "2, 1, 24000, 2.0",
    "2, 2, 16000, 2.0",
    "0, 0, 11025, 2.5",
    "0, 1, 12000, 2.5",
    "0, 2, 8000, 2.5",
  })
  void sampleRatesFollowTheVersion(int version, int index, int sampleRate, String name)
      throws InvalidDataException {
    MpegFrame frame = new MpegFrame(header(version, LAYER_III, 9, index, 0, 0));
    assertEquals(sampleRate, frame.getSampleRate());
    assertEquals(name, frame.getVersion());
    assertEquals("III", frame.getLayer());
  }

  @Test
  void layersAreNamedInRomanNumerals() throws InvalidDataException {
    assertEquals("I", new MpegFrame(header(MPEG_1, LAYER_I, 9, 0, 0, 0)).getLayer());
    assertEquals("II", new MpegFrame(header(MPEG_1, LAYER_II, 9, 0, 0, 0)).getLayer());
    assertEquals("III", new MpegFrame(header(MPEG_1, LAYER_III, 9, 0, 0, 0)).getLayer());
  }

  @Test
  void reservedVersionLayerAndSampleRateAreInvalid() {
    // Version 01 and layer 00 are reserved, as is sample rate index 3.
    assertThrows(InvalidDataException.class, () -> new MpegFrame(header(1, LAYER_III, 9, 0, 0, 0)));
    assertThrows(InvalidDataException.class, () -> new MpegFrame(header(MPEG_1, 0, 9, 0, 0, 0)));
    for (int version : new int[] {MPEG_1, MPEG_2, MPEG_2_5}) {
      assertThrows(
          InvalidDataException.class, () -> new MpegFrame(header(version, LAYER_III, 9, 3, 0, 0)));
    }
  }

  @Test
  void aFrameStartsWithElevenSyncBits() {
    byte[] header = header(MPEG_1, LAYER_III, 9, 0, 0, 0);
    header[1] &= (byte) 0xdf; // Clears the last sync bit.
    assertThrows(InvalidDataException.class, () -> new MpegFrame(header));
    assertThrows(InvalidDataException.class, () -> new MpegFrame(new byte[] {(byte) 0xff, -5, 0}));
  }

  @ParameterizedTest
  @CsvSource({"0, Stereo", "1, Joint stereo", "2, Dual mono", "3, Mono"})
  void channelModes(int mode, String name) throws InvalidDataException {
    MpegFrame frame = new MpegFrame(header(MPEG_1, LAYER_III, 9, 0, 0, mode));
    assertEquals(name, frame.getChannelMode());
    if (mode != 1) assertEquals("n/a", frame.getModeExtension());
  }

  /**
   * Joint stereo frames name their mode extension: bands in Layers I and II, stereo coding in III.
   */
  @ParameterizedTest
  @CsvSource({
    "3, 0, Bands 4-31",
    "3, 1, Bands 8-31",
    "3, 2, Bands 12-31",
    "3, 3, Bands 16-31",
    "2, 0, Bands 4-31",
    "2, 3, Bands 16-31",
    "1, 0, None",
    "1, 1, Intensity stereo",
    "1, 2, M/S stereo",
    "1, 3, Intensity & M/S stereo",
  })
  void jointStereoModeExtensions(int layer, int extension, String name)
      throws InvalidDataException {
    MpegFrame frame = new MpegFrame(header(MPEG_1, layer, 1, 9, 0, 0, 0, 1, extension, 0, 0, 0));
    assertEquals(name, frame.getModeExtension());
  }

  @ParameterizedTest
  @CsvSource({"0, None", "1, 50/15 ms", "3, CCITT J.17"})
  void emphasis(int emphasis, String name) throws InvalidDataException {
    MpegFrame frame = new MpegFrame(header(MPEG_1, LAYER_III, 1, 9, 0, 0, 0, 0, 0, 0, 0, emphasis));
    assertEquals(name, frame.getEmphasis());
  }

  @Test
  void reservedEmphasisIsInvalid() {
    assertThrows(
        InvalidDataException.class,
        () -> new MpegFrame(header(MPEG_1, LAYER_III, 1, 9, 0, 0, 0, 0, 0, 0, 0, 2)));
  }

  @Test
  void flagBitsAreReadFromTheirPositions() throws InvalidDataException {
    byte[] b = header(MPEG_1, LAYER_III, 1, 9, 0, 1, 1, 0, 0, 1, 1, 0);
    MpegFrame all = new MpegFrame(b[0], b[1], b[2], b[3]);
    assertTrue(all.isProtection());
    assertTrue(all.hasPadding());
    assertTrue(all.isPrivate());
    assertTrue(all.isCopyright());
    assertTrue(all.isOriginal());
    MpegFrame none = new MpegFrame(header(MPEG_1, LAYER_III, 0, 9, 0, 0, 0, 0, 0, 0, 0, 0));
    assertFalse(none.isProtection());
    assertFalse(none.hasPadding());
    assertFalse(none.isPrivate());
    assertFalse(none.isCopyright());
    assertFalse(none.isOriginal());
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
