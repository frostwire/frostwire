/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.soundcloud;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzes {@link SoundcloudUtils#fromJson}, which reads SoundCloud's {@code /resolve} response for a
 * track, playlist or search URL the user pasted (desktop and Android). The JSON comes from
 * api-v2.soundcloud.com over the network, so it is untrusted. {@code fromJson} declares no
 * exceptions and returns a list; a throw, a hang, a null list or a null element is a finding.
 */
class SoundcloudUtilsFuzzTest {

  @FuzzTest
  void fromJson(FuzzedDataProvider data) {
    boolean fromPastedUrl = data.consumeBoolean();
    String json = data.consumeRemainingAsString();
    var results = SoundcloudUtils.fromJson(json, fromPastedUrl);
    if (results == null || results.contains(null)) {
      throw new IllegalStateException("fromJson returned null or a null result");
    }
  }
}
