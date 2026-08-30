/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;

class TellurideSearchPerformerBestFormatsTest {

  @Test
  void youtubeDashPicksBestVideoAndBestAudio() {
    String json =
        "{"
            + "\"id\":\"abc\","
            + "\"title\":\"Test Video\","
            + "\"extractor\":\"youtube\","
            + "\"webpage_url\":\"https://www.youtube.com/watch?v=abc\","
            + "\"thumbnail\":\"https://i.ytimg.com/x.jpg\","
            + "\"upload_date\":\"20240101\","
            + "\"formats\":["
            + "{\"url\":\"https://ex/audio-small.m4a\",\"ext\":\"m4a\",\"acodec\":\"mp4a.40.2\","
            + "\"vcodec\":\"none\",\"filesize\":100,\"height\":0,\"width\":0},"
            + "{\"url\":\"https://ex/audio-best.m4a\",\"ext\":\"m4a\",\"acodec\":\"mp4a.40.2\","
            + "\"vcodec\":\"none\",\"filesize\":4000,\"height\":0,\"width\":0},"
            + "{\"url\":\"https://ex/v1080.mp4\",\"ext\":\"mp4\",\"acodec\":\"none\","
            + "\"vcodec\":\"avc1\",\"filesize\":8000,\"height\":1080,\"width\":1920},"
            + "{\"url\":\"https://ex/v720.mp4\",\"ext\":\"mp4\",\"acodec\":\"none\","
            + "\"vcodec\":\"avc1\",\"filesize\":3000,\"height\":720,\"width\":1280},"
            + "{\"url\":\"https://ex/hls.m3u8\",\"ext\":\"mp4\",\"acodec\":\"none\","
            + "\"vcodec\":\"avc1\",\"filesize\":90000,\"height\":1080,\"width\":1920}"
            + "]}";
    Gson gson = new GsonBuilder().create();
    List<TellurideSearchResult> results =
        TellurideSearchPerformer.getValidResults(
            json, gson, null, -1, "https://www.youtube.com/watch?v=abc");

    assertEquals(2, results.size());
    assertTrue(results.get(0).getFilename().contains("1920x1080"));
    assertTrue(results.get(0).getDownloadUrl().contains("v1080.mp4"));
    assertTrue(results.get(1).getFilename().contains("(audio)"));
    assertTrue(results.get(1).getDownloadUrl().contains("audio-best.m4a"));
  }

  @Test
  void muxedProgressiveStillOffersAudioRow() {
    String json =
        "{"
            + "\"id\":\"abc\","
            + "\"title\":\"LAGOS\","
            + "\"extractor\":\"youtube\","
            + "\"webpage_url\":\"https://www.youtube.com/watch?v=abc\","
            + "\"thumbnail\":\"https://i.ytimg.com/x.jpg\","
            + "\"upload_date\":\"20240101\","
            + "\"formats\":["
            + "{\"url\":\"https://ex/itag18.mp4\",\"ext\":\"mp4\",\"acodec\":\"mp4a.40.2\","
            + "\"vcodec\":\"avc1.42001E\",\"filesize\":4494476,\"height\":360,\"width\":360}"
            + "]}";
    List<TellurideSearchResult> results =
        TellurideSearchPerformer.getValidResults(
            json, new GsonBuilder().create(), null, -1, "https://www.youtube.com/watch?v=abc");
    assertEquals(2, results.size());
    assertTrue(results.get(0).getFilename().contains("360x360"));
    assertTrue(results.get(1).getFilename().contains("(audio)"));
  }
}
