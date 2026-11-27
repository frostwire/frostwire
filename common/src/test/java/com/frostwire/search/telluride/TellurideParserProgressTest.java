/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The progress line of yt-dlp must be read with its whole numbers, however big they get. */
class TellurideParserProgressTest {
  private static final class Recorder implements TellurideListener {
    final List<String> progress = new ArrayList<>();

    @Override
    public void onProgress(
        float completionPercentage,
        float fileSize,
        String fileSizeUnits,
        float downloadSpeed,
        String downloadSpeedUnits,
        String ETA) {
      progress.add(
          completionPercentage
              + "|"
              + fileSize
              + fileSizeUnits
              + "|"
              + downloadSpeed
              + downloadSpeedUnits
              + "|"
              + ETA);
    }

    @Override
    public void onError(String errorMessage) {}

    @Override
    public void onFinished(int exitCode) {}

    @Override
    public void onDestination(String outputFilename) {}

    @Override
    public boolean aborted() {
      return false;
    }

    @Override
    public void onMeta(String json) {}
  }

  private static String parse(String line) {
    Recorder recorder = new Recorder();
    new TellurideParser(recorder, false).parse(line);
    assertEquals(1, recorder.progress.size(), "no progress parsed from: " + line);
    return recorder.progress.get(0);
  }

  @Test
  void smallSizesStillParse() {
    assertEquals(
        "30.5|277.93MiB|507.5KiB/s|06:29",
        parse("[download]  30.5% of 277.93MiB at 507.50KiB/s ETA 06:29"));
  }

  @Test
  void sizesOfOneThousandOrMoreKeepTheirLeadingDigits() {
    assertEquals(
        "45.2|1234.56MiB|507.5KiB/s|06:29",
        parse("[download]  45.2% of 1234.56MiB at 507.50KiB/s ETA 06:29"));
    assertEquals(
        "9.9|12345.5GiB|507.5KiB/s|06:29",
        parse("[download]   9.9% of 12345.50GiB at 507.50KiB/s ETA 06:29"));
  }

  @Test
  void ratesOfOneThousandOrMoreKeepTheirLeadingDigits() {
    assertEquals(
        "45.2|277.93MiB|1234.5KiB/s|00:10",
        parse("[download]  45.2% of 277.93MiB at 1234.50KiB/s ETA 00:10"));
  }

  @Test
  void approximateSizesWithATildeStillParse() {
    assertEquals(
        "12.5|1500.25MiB|2.5MiB/s|01:02",
        parse("[download]  12.5% of ~1500.25MiB at 2.50MiB/s ETA 01:02"));
  }
}
