/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.soundcloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link SoundcloudUtils#fromJson} reads SoundCloud's {@code /resolve} response for a pasted URL. A
 * {@code null} in {@code collection} or {@code tracks} threw a NullPointerException out of it, so
 * pasting that link did nothing at all (the callers log it), instead of offering the other tracks.
 * Found by {@code SoundcloudUtilsFuzzTest}. The shapes below (search collection, playlist, single
 * track) must keep decoding as before, and a body that is not JSON is reported with Gson's
 * JsonParseException, as documented.
 */
class SoundcloudUtilsFromJsonTest {

  private static String track() throws IOException {
    try (InputStream in =
        SoundcloudUtilsFromJsonTest.class.getResourceAsStream("downloadable-hls-track.json")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  void nullTrackInCollectionIsSkipped() throws IOException {
    List<SoundcloudSearchResult> results =
        SoundcloudUtils.fromJson("{\"collection\":[null," + track() + ",null]}", false);
    assertEquals(1, results.size());
    assertEquals("Sanitized downloadable track", results.get(0).getDisplayName());
  }

  @Test
  void nullTrackInPlaylistIsSkipped() throws IOException {
    List<SoundcloudSearchResult> results =
        SoundcloudUtils.fromJson("{\"tracks\":[" + track() + ",null]}", true);
    assertEquals(1, results.size());
    assertEquals("Sanitized downloadable track", results.get(0).getDisplayName());
  }

  @Test
  void collectionPlaylistAndSingleTrackDecodeAsBefore() throws IOException {
    String t = track();
    assertEquals(
        2, SoundcloudUtils.fromJson("{\"collection\":[" + t + "," + t + "]}", false).size());
    assertEquals(
        1, SoundcloudUtils.fromJson("{\"kind\":\"playlist\",\"tracks\":[" + t + "]}", true).size());
    List<SoundcloudSearchResult> single = SoundcloudUtils.fromJson(t, true);
    assertEquals(1, single.size());
    assertEquals(
        "https://soundcloud.com/example/sanitized-downloadable-track",
        single.get(0).getDetailsUrl());
    assertTrue(single.get(0).isHls());
  }

  @Test
  void emptyAndNullBodiesGiveNoResults() {
    assertEquals(0, SoundcloudUtils.fromJson("", true).size());
    assertEquals(0, SoundcloudUtils.fromJson("null", true).size());
    assertEquals(0, SoundcloudUtils.fromJson("{\"collection\":null}", false).size());
    assertEquals(0, SoundcloudUtils.fromJson("{\"tracks\":[]}", false).size());
  }

  @Test
  void bodyThatIsNotJsonThrowsJsonParseException() {
    assertThrows(
        JsonParseException.class, () -> SoundcloudUtils.fromJson("{\"collection\":[", false));
    assertThrows(JsonParseException.class, () -> SoundcloudUtils.fromJson("<html>", false));
  }
}
