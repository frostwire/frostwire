/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.webm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * WebM merge tests on tiny synthetic fixtures ({@code v-in.webm}: VP9, 2
 * clusters x 2 blocks; {@code a-in.webm}: Opus with a real OpusHead, 2 x 3,
 * both files on track 1 to exercise renumbering). Output is re-demuxed and
 * every packet payload is byte-compared.
 */
class WebMMuxerTest {

  private static File fixture(String name) throws Exception {
    URL resource = WebMMuxerTest.class.getResource("/webm/" + name);
    assertNotNull(resource, "missing fixture " + name);
    File tmp = File.createTempFile("webmmuxer-test-", "-" + name);
    tmp.deleteOnExit();
    try (java.io.InputStream in = resource.openStream()) {
      Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    return tmp;
  }

  private static WebMDemuxer.WebMTrack trackByType(WebMDemuxer.Media media, int type) {
    for (WebMDemuxer.WebMTrack track : media.tracks) {
      if (track.type == type) {
        return track;
      }
    }
    return null;
  }

  private static List<byte[]> payloads(RandomAccessFile raf, WebMDemuxer.WebMTrack track)
      throws Exception {
    List<byte[]> out = new ArrayList<>();
    for (WebMDemuxer.Packet packet : track.packets) {
      byte[] payload = new byte[packet.size];
      raf.seek(packet.fileOffset);
      raf.readFully(payload);
      out.add(payload);
    }
    return out;
  }

  private static List<Long> timestamps(WebMDemuxer.WebMTrack track) {
    List<Long> out = new ArrayList<>();
    for (WebMDemuxer.Packet packet : track.packets) {
      out.add(packet.timestamp);
    }
    return out;
  }

  @Test
  void mergesVideoAndAudioTracks() throws Exception {
    File video = fixture("v-in.webm");
    File audio = fixture("a-in.webm");
    File out = File.createTempFile("webmmuxer-test-out-", ".webm");
    out.deleteOnExit();
    WebMMuxer.mux(video, audio, out);
    assertTrue(out.length() > 0);

    WebMDemuxer.Media media;
    try (RandomAccessFile raf = new RandomAccessFile(out, "r")) {
      media = WebMDemuxer.read(raf);
      assertEquals(2, media.tracks.size());
      assertEquals(1000000, media.timecodeScale);

      WebMDemuxer.WebMTrack v = trackByType(media, 1);
      WebMDemuxer.WebMTrack a = trackByType(media, 2);
      assertNotNull(v);
      assertNotNull(a);
      assertEquals(1, v.number);
      assertEquals(2, a.number);
      assertEquals("V_VP9", v.codecId);
      assertEquals("A_OPUS", a.codecId);
      assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, v.codecPrivate);

      assertEquals(List.of(0L, 42L, 35000L, 35042L), timestamps(v));
      assertEquals(List.of(0L, 20L, 40L, 35000L, 35020L, 35040L), timestamps(a));

      List<byte[]> vPayloads = payloads(raf, v);
      assertEquals(4, vPayloads.size());
      assertEquals(200, vPayloads.get(0).length);
      assertEquals((byte) 0xA0, vPayloads.get(0)[0]);
      assertEquals((byte) 0xA3, vPayloads.get(3)[0]);

      List<byte[]> aPayloads = payloads(raf, a);
      assertEquals(6, aPayloads.size());
      assertEquals(50, aPayloads.get(0).length);
      assertEquals((byte) 0xB0, aPayloads.get(0)[0]);
      assertEquals((byte) 0xB5, aPayloads.get(5)[0]);

      // Duration covers the longest track end (~35084ms).
      assertTrue(media.segmentDuration > 35000.0 && media.segmentDuration < 35100.0);
    }
  }

  @Test
  void mergePutsVideoFirstOnTimestampTies() {
    WebMDemuxer.WebMTrack video = new WebMDemuxer.WebMTrack();
    WebMDemuxer.WebMTrack audio = new WebMDemuxer.WebMTrack();
    WebMDemuxer.Packet vp = new WebMDemuxer.Packet();
    vp.timestamp = 100;
    video.packets.add(vp);
    WebMDemuxer.Packet ap = new WebMDemuxer.Packet();
    ap.timestamp = 100;
    audio.packets.add(ap);
    List<WebMMuxer.OutBlock> merged = WebMMuxer.merge(video, audio);
    assertEquals(2, merged.size());
    assertTrue(merged.get(0).video);
    assertTrue(!merged.get(1).video);
  }

  @Test
  void clusterizeSplitsBeyondInt16Range() {
    WebMDemuxer.WebMTrack video = new WebMDemuxer.WebMTrack();
    WebMDemuxer.WebMTrack audio = new WebMDemuxer.WebMTrack();
    for (long ts : new long[]{0, 40000}) {
      WebMDemuxer.Packet vp = new WebMDemuxer.Packet();
      vp.timestamp = ts;
      video.packets.add(vp);
    }
    List<WebMMuxer.OutBlock> merged = WebMMuxer.merge(video, audio);
    List<WebMMuxer.OutCluster> clusters = WebMMuxer.clusterize(merged);
    assertEquals(2, clusters.size());
    assertEquals(0, clusters.get(0).timecode);
    assertEquals(40000, clusters.get(1).timecode);
  }

  @Test
  void rejectsAudioAsVideo() throws Exception {
    File audio = fixture("a-in.webm");
    File out = File.createTempFile("webmmuxer-test-err-", ".webm");
    out.deleteOnExit();
    assertThrows(java.io.IOException.class, () -> WebMMuxer.mux(audio, audio, out));
  }
}
