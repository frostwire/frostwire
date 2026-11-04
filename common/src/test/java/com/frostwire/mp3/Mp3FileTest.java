/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import static com.frostwire.mp3.Id3Bytes.bytes;
import static com.frostwire.mp3.MpegFrameTest.LAYER_III;
import static com.frostwire.mp3.MpegFrameTest.MPEG_1;
import static com.frostwire.mp3.MpegFrameTest.MPEG_2;
import static com.frostwire.mp3.MpegFrameTest.MPEG_2_5;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Mp3File finds the MPEG frames of a file between its tags, and saves them with new tags. */
class Mp3FileTest {

  /** MPEG-1 Layer III, 44100 Hz, stereo: 417-byte frames at 128 kbps, 522 at 160. */
  private static final byte[] HEADER_128 = MpegFrameTest.header(MPEG_1, LAYER_III, 9, 0, 0, 0);

  private static final byte[] HEADER_160 = MpegFrameTest.header(MPEG_1, LAYER_III, 10, 0, 0, 0);

  @TempDir Path dir;

  @Test
  void aFileOfFramesOnly() throws Exception {
    byte[] audio = frames(HEADER_128, 417, 30);
    Mp3File file = new Mp3File(write("a.mp3", audio).toString());
    assertEquals(30, file.getFrameCount());
    assertEquals(0, file.getStartOffset());
    assertEquals(audio.length - 1, file.getEndOffset());
    assertEquals(128, file.getBitrate());
    assertFalse(file.isVbr());
    assertEquals(Map.of(128, new MutableInteger(30)), file.getBitrates());
    assertEquals(44100, file.getSampleRate());
    assertEquals("1.0", file.getVersion());
    assertEquals("III", file.getLayer());
    assertEquals("Stereo", file.getChannelMode());
    assertEquals("n/a", file.getModeExtension());
    assertEquals("None", file.getEmphasis());
    assertFalse(file.isCopyright());
    assertFalse(file.isOriginal());
    // 8 bits per byte over 128 bits per millisecond: 12509 bytes are 782 ms.
    assertEquals(782, file.getLengthInMilliseconds());
    assertEquals(1, file.getLengthInSeconds());
    assertFalse(file.hasXingFrame());
    assertEquals(-1, file.getXingOffset());
    assertFalse(file.hasId3v1Tag());
    assertFalse(file.hasId3v2Tag());
    assertFalse(file.hasCustomTag());
    assertNull(file.getCustomTag());
    assertEquals(audio.length, file.getLength());
    assertTrue(file.getLastModified() > 0);
    assertEquals(dir.resolve("a.mp3").toString(), file.getFilename());
  }

  @Test
  void tagsBeforeAndAfterTheFrames() throws Exception {
    ID3v23Tag v2 = new ID3v23Tag();
    v2.setTitle("Title");
    v2.setPadding(true);
    ID3v1Tag v1 = new ID3v1Tag();
    v1.setArtist("Artist");
    byte[] tag = v2.toBytes();
    byte[] audio = frames(HEADER_128, 417, 10);
    Mp3File file = new Mp3File(write("a.mp3", bytes(tag, audio, v1.toBytes())).toString());
    assertTrue(file.hasId3v2Tag());
    assertEquals("Title", file.getId3v2Tag().getTitle());
    assertTrue(file.hasId3v1Tag());
    assertEquals("Artist", file.getId3v1Tag().getArtist());
    assertEquals(tag.length, file.getStartOffset());
    assertEquals(tag.length + audio.length - 1, file.getEndOffset());
    assertEquals(10, file.getFrameCount());
    assertFalse(file.hasCustomTag());
  }

  /** Bytes between the last frame and the ID3v1 tag, such as an APE tag, are kept as they are. */
  @Test
  void bytesAfterTheFramesAreACustomTag() throws Exception {
    byte[] custom = bytes("APETAGEX", new byte[92]);
    byte[] audio = frames(HEADER_128, 417, 10);
    Mp3File file =
        new Mp3File(write("a.mp3", bytes(audio, custom, new ID3v1Tag().toBytes())).toString());
    assertEquals(10, file.getFrameCount());
    assertTrue(file.hasCustomTag());
    assertArrayEquals(custom, file.getCustomTag());
  }

  /** A Xing or Info frame holds VBR data, not audio: it is kept apart from the frames. */
  @Test
  void aXingFrameIsNotCountedAsAudio() throws Exception {
    byte[] xing = frames(HEADER_160, 522, 1);
    System.arraycopy(bytes("Xing"), 0, xing, 36, 4);
    ID3v24Tag v2 = new ID3v24Tag();
    v2.setTitle("Title");
    byte[] tag = v2.toBytes();
    byte[] audio = frames(HEADER_128, 417, 10);
    Path mp3 = write("a.mp3", bytes(tag, xing, audio));
    Mp3File file = new Mp3File(mp3.toString());
    assertTrue(file.hasXingFrame());
    assertEquals(tag.length, file.getXingOffset());
    assertEquals(160, file.getXingBitrate());
    assertEquals(tag.length + xing.length, file.getStartOffset());
    assertEquals(10, file.getFrameCount());
    assertEquals("Title", file.getId3v2Tag().getTitle());

    // Saving keeps the Xing frame in front of the audio.
    Path saved = dir.resolve("saved.mp3");
    file.save(saved.toString());
    assertArrayEquals(Files.readAllBytes(mp3), Files.readAllBytes(saved));
  }

  @Test
  void infoFramesAtEachOffsetAreXingFramesToo() throws Exception {
    for (int offset : new int[] {13, 21, 36}) {
      byte[] info = frames(HEADER_128, 417, 1);
      System.arraycopy(bytes(offset == 21 ? "Xing" : "Info"), 0, info, offset, 4);
      Mp3File file =
          new Mp3File(write("a.mp3", bytes(info, frames(HEADER_128, 417, 5))).toString());
      assertEquals(0, file.getXingOffset(), "offset " + offset);
      assertEquals(417, file.getStartOffset());
      // A file starting with a Xing frame has no room for an ID3v2 tag.
      assertFalse(file.hasId3v2Tag());
    }
  }

  @Test
  void framesOfDifferentBitratesAreVbr() throws Exception {
    byte[] audio = bytes(frames(HEADER_128, 417, 10), frames(HEADER_160, 522, 10));
    Mp3File file = new Mp3File(write("a.mp3", audio).toString());
    assertTrue(file.isVbr());
    assertEquals(20, file.getFrameCount());
    assertEquals(10, file.getBitrates().get(128).getValue());
    assertEquals(10, file.getBitrates().get(160).getValue());
    assertEquals(144, file.getBitrate());
    assertEquals(audio.length - 1, file.getEndOffset());
  }

  /** A buffer smaller than a frame still finds every frame, block after block. */
  @Test
  void smallBuffersScanTheWholeFile() throws Exception {
    byte[] audio = frames(HEADER_128, 417, 10);
    Path mp3 = write("a.mp3", bytes(new byte[300], audio));
    Mp3File file = new Mp3File(mp3.toString(), 100);
    assertEquals(300, file.getStartOffset());
    assertEquals(10, file.getFrameCount());
    assertEquals(300 + audio.length - 1, file.getEndOffset());

    Path saved = dir.resolve("saved.mp3");
    file.save(saved.toString());
    assertArrayEquals(audio, Files.readAllBytes(saved));
  }

  /** Without a full scan, the file is read up to its first frame: tags and audio format only. */
  @Test
  void aQuickScanStopsAtTheFirstFrame() throws Exception {
    ID3v23Tag v2 = new ID3v23Tag();
    v2.setTitle("Title");
    byte[] tag = v2.toBytes();
    Mp3File file =
        new Mp3File(
            write("a.mp3", bytes(tag, frames(HEADER_128, 417, 10), "custom")).toString(), false);
    assertEquals(tag.length, file.getStartOffset());
    assertEquals(1, file.getFrameCount());
    assertEquals(44100, file.getSampleRate());
    assertEquals("Title", file.getId3v2Tag().getTitle());
    assertFalse(file.hasCustomTag());
  }

  /** What looks like a frame header but isn't followed by another frame is skipped. */
  @Test
  void aFalseSyncBeforeTheFramesIsSkipped() throws Exception {
    byte[] audio = frames(HEADER_128, 417, 10);
    Mp3File file = new Mp3File(write("a.mp3", bytes(HEADER_128, new byte[20], audio)).toString());
    assertEquals(24, file.getStartOffset());
    assertEquals(10, file.getFrameCount());
    assertFalse(file.hasId3v2Tag());
  }

  @Test
  void aCutOffLastFrameIsLeftOut() throws Exception {
    byte[] audio = frames(HEADER_128, 417, 10);
    byte[] cut = Arrays.copyOf(frames(HEADER_128, 417, 1), 200);
    Mp3File file = new Mp3File(write("a.mp3", bytes(audio, cut)).toString());
    assertEquals(10, file.getFrameCount());
    assertEquals(audio.length - 1, file.getEndOffset());
    assertArrayEquals(cut, file.getCustomTag());
  }

  /** Frames of another sample rate after the audio aren't part of it. */
  @Test
  void anInconsistentFrameEndsTheAudio() throws Exception {
    byte[] audio = frames(HEADER_128, 417, 10);
    byte[] other = frames(MpegFrameTest.header(MPEG_1, LAYER_III, 9, 1, 0, 0), 384, 3);
    Mp3File file = new Mp3File(write("a.mp3", bytes(audio, other)).toString());
    assertEquals(10, file.getFrameCount());
    assertArrayEquals(other, file.getCustomTag());
  }

  @Test
  void savingWritesTheNewTagsAroundTheSameAudio() throws Exception {
    ID3v23Tag old = new ID3v23Tag();
    old.setTitle("Old");
    byte[] audio = frames(HEADER_128, 417, 10);
    byte[] custom = bytes("custom tag");
    Path mp3 = write("a.mp3", bytes(old.toBytes(), audio, custom, new ID3v1Tag().toBytes()));
    Mp3File file = new Mp3File(mp3.toString(), 100);

    ID3v24Tag v2 = new ID3v24Tag();
    v2.setTitle("New");
    ID3v1Tag v1 = new ID3v1Tag();
    v1.setTitle("New");
    file.setId3v2Tag(v2);
    file.setId3v1Tag(v1);
    Path saved = dir.resolve("saved.mp3");
    file.save(saved.toString());
    assertArrayEquals(bytes(v2.toBytes(), audio, custom, v1.toBytes()), Files.readAllBytes(saved));
    Mp3File reread = new Mp3File(saved.toString());
    assertEquals("New", reread.getId3v2Tag().getTitle());
    assertEquals("New", reread.getId3v1Tag().getTitle());
    assertEquals(10, reread.getFrameCount());

    file.setId3v2Tag(null);
    file.setId3v1Tag(null);
    file.setCustomTag(null);
    Path bare = dir.resolve("bare.mp3");
    file.save(bare.toString());
    assertArrayEquals(audio, Files.readAllBytes(bare));
  }

  @Test
  void savingOverTheSourceFileIsRefused() throws Exception {
    Path mp3 = write("a.mp3", frames(HEADER_128, 417, 10));
    Mp3File file = new Mp3File(mp3.toString());
    assertThrows(IllegalArgumentException.class, () -> file.save(mp3.toString()));
    assertThrows(IllegalArgumentException.class, () -> file.save(mp3.toString().toUpperCase()));
  }

  @Test
  void filesWithoutFramesAreInvalid() throws Exception {
    Path noise = write("noise.mp3", bytes(new byte[1000], HEADER_128, new byte[1000]));
    assertThrows(InvalidDataException.class, () -> new Mp3File(noise.toString()));
    ID3v23Tag v2 = new ID3v23Tag();
    v2.setTitle("Title");
    Path tagOnly = write("tag.mp3", bytes(v2.toBytes(), new ID3v1Tag().toBytes()));
    assertThrows(InvalidDataException.class, () -> new Mp3File(tagOnly.toString()));
  }

  /** The ID3v1 tag is looked for in the last 128 bytes: a shorter file can't be read. */
  @Test
  void filesShorterThanAnId3v1TagCantBeRead() throws Exception {
    Path tiny = write("tiny.mp3", new byte[100]);
    assertThrows(IOException.class, () -> new Mp3File(tiny.toString()));
  }

  @Test
  void missingFilesAndTinyBuffersAreRejected() throws Exception {
    assertThrows(
        FileNotFoundException.class, () -> new Mp3File(dir.resolve("missing.mp3").toString()));
    Path mp3 = write("a.mp3", frames(HEADER_128, 417, 10));
    assertThrows(IllegalArgumentException.class, () -> new Mp3File(mp3.toString(), 40));
    assertEquals(10, new Mp3File(mp3.toString(), 41).getFrameCount());
  }

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
