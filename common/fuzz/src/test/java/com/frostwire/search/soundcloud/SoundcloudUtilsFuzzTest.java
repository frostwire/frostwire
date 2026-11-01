/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.soundcloud;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import com.google.gson.JsonParseException;
import java.util.List;

/**
 * Fuzzes {@link SoundcloudUtils#fromJson}, which reads SoundCloud's {@code /resolve} response for a
 * track, playlist or search URL the user pasted (desktop and Android). The JSON comes from
 * api-v2.soundcloud.com over the network, so it is untrusted. {@code fromJson} documents Gson's
 * {@link JsonParseException} for a body that is not the JSON SoundCloud documents; any other throw,
 * a hang, a null list or a null element is a finding.
 */
class SoundcloudUtilsFuzzTest {

  @FuzzTest
  void fromJson(FuzzedDataProvider data) {
    boolean fromPastedUrl = data.consumeBoolean();
    String json = data.consumeRemainingAsString();
    List<SoundcloudSearchResult> results;
    try {
      results = SoundcloudUtils.fromJson(json, fromPastedUrl);
    } catch (JsonParseException documented) {
      return;
    }
    if (results == null || results.contains(null)) {
      throw new IllegalStateException("fromJson returned null or a null result");
    }
  }
}
