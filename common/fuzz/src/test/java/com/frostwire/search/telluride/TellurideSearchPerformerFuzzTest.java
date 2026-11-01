/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.util.List;

/**
 * Fuzzes how yt-dlp's JSON metadata becomes search results: {@code getValidResults} for a single
 * video (its formats) and {@code getValidPlaylistResults} for a playlist (its entries). Both read
 * the JSON Telluride prints for a URL the user pasted (desktop, and Android through its Python VM),
 * and yt-dlp copies most of it from the media site, so it is untrusted. These are two readers of
 * one format, so they share this harness, and the first byte picks one. Neither declares an
 * exception; a throw, a hang, a null list or a null element is a finding.
 */
class TellurideSearchPerformerFuzzTest {

  private static final Gson GSON = new GsonBuilder().create();

  @FuzzTest
  void getValidResults(FuzzedDataProvider data) {
    boolean playlist = data.consumeBoolean();
    String json = data.consumeRemainingAsString();
    List<TellurideSearchResult> results;
    try {
      results =
          playlist
              ? TellurideSearchPerformer.getValidPlaylistResults(json, GSON, null, -1, "fuzz")
              : TellurideSearchPerformer.getValidResults(json, GSON, null, -1, "fuzz");
    } catch (JsonParseException documented) {
      return;
    }
    if (results == null || results.contains(null)) {
      throw new IllegalStateException("returned null or a null result");
    }
  }
}
