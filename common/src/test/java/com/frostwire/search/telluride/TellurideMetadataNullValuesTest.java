/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The yt-dlp metadata readers on empty or {@code null} JSON. Gson reads an empty body, or the
 * literal {@code null}, as a null object, and a playlist's {@code entries} may hold a {@code null};
 * the readers dereferenced both and threw a NullPointerException (desktop then says it found no
 * media; on Android {@code TellurideCourier.queryPage} does not catch it). Found by {@code
 * TellurideSearchPerformerFuzzTest}. Malformed JSON is reported with Gson's JsonParseException, as
 * documented.
 */
class TellurideMetadataNullValuesTest {

  private static final Gson GSON = new GsonBuilder().create();

  @Test
  void emptyOrNullMetadataGivesNoResults() {
    for (String json : new String[] {"", "  ", "null"}) {
      assertEquals(0, TellurideSearchPerformer.getValidResults(json, GSON, null, -1, "t").size());
      assertEquals(
          0, TellurideSearchPerformer.getValidPlaylistResults(json, GSON, null, -1, "t").size());
    }
  }

  @Test
  void nullPlaylistEntryIsSkipped() {
    List<TellurideSearchResult> results =
        TellurideSearchPerformer.getValidPlaylistResults(
            "{\"extractor\":\"youtube:tab\",\"entries\":[null,"
                + "{\"id\":\"abc\",\"title\":\"First\",\"url\":\"https://www.youtube.com/watch?v=abc\","
                + "\"upload_date\":\"20231231\"},null]}",
            GSON,
            null,
            -1,
            "t");
    assertEquals(1, results.size());
    assertEquals("First", results.get(0).getDisplayName());
    assertEquals("https://www.youtube.com/watch?v=abc", results.get(0).getDetailsUrl());
  }

  @Test
  void missingFormatsOrEntriesGiveNoResults() {
    assertEquals(
        0, TellurideSearchPerformer.getValidResults("{\"id\":\"a\"}", GSON, null, -1, "t").size());
    assertEquals(
        0,
        TellurideSearchPerformer.getValidPlaylistResults("{\"entries\":null}", GSON, null, -1, "t")
            .size());
  }

  @Test
  void malformedMetadataThrowsJsonParseException() {
    assertThrows(
        JsonParseException.class,
        () -> TellurideSearchPerformer.getValidResults("{\"formats\":[", GSON, null, -1, "t"));
    assertThrows(
        JsonParseException.class,
        () -> TellurideSearchPerformer.getValidPlaylistResults("[1,2]", GSON, null, -1, "t"));
  }
}
