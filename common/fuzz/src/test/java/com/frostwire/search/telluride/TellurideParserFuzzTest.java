/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

/**
 * Fuzzes {@link TellurideParser}, which reads the Telluride (yt-dlp) process output line by line:
 * progress lines, the destination file name, errors, and in metadata mode the JSON it collects and
 * hands to {@code onMeta} when the process ends. yt-dlp prints what the media site sent it, so the
 * lines are untrusted. {@code parse} and {@code done} declare no exceptions; a throw or a hang is a
 * finding, and so is a run that ends in metadata mode without reporting either metadata or an
 * error.
 */
class TellurideParserFuzzTest {

  @FuzzTest
  void parse(FuzzedDataProvider data) {
    boolean metaOnly = data.consumeBoolean();
    RecordingListener listener = new RecordingListener();
    TellurideParser parser = new TellurideParser(listener, metaOnly);
    int lines = data.consumeInt(0, 16);
    for (int i = 0; i < lines; i++) {
      parser.parse(data.consumeString(512));
    }
    parser.parse(data.consumeRemainingAsString());
    parser.done();
    if (metaOnly && listener.metaOrErrors == 0) {
      throw new IllegalStateException("done() reported neither metadata nor an error");
    }
  }

  private static final class RecordingListener extends TellurideAbstractListener {
    int metaOrErrors;

    @Override
    public void onError(String errorMessage) {
      metaOrErrors++;
    }

    @Override
    public void onMeta(String json) {
      metaOrErrors++;
    }
  }
}
