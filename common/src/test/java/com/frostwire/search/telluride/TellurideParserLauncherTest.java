/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TellurideParserLauncherTest {

  /** Records every callback in order. */
  private static class Recorder implements TellurideListener {
    final List<String> events = new ArrayList<>();
    final CountDownLatch finished = new CountDownLatch(1);
    volatile boolean abort;

    public void onProgress(float pct, float size, String su, float rate, String ru, String eta) {
      events.add("progress " + pct + " " + size + su + " " + rate + ru + " " + eta);
    }

    public void onError(String message) {
      events.add("error " + message);
    }

    public void onFinished(int exitCode) {
      events.add("finished " + exitCode);
      finished.countDown();
    }

    public void onDestination(String outputFilename) {
      events.add("destination " + outputFilename);
    }

    public boolean aborted() {
      return abort;
    }

    public void onMeta(String json) {
      events.add("meta " + json);
    }
  }

  @Test
  void parsesDownloadProgressDestinationAndErrors() {
    Recorder rec = new Recorder();
    TellurideParser parser = new TellurideParser(rec, false);
    parser.parse("");
    parser.parse("[download] Destination: video.mp4");
    parser.parse("[download]  30.5% of 277.93MiB at 507.50KiB/s ETA 06:29");
    parser.parse("some unrelated chatter");
    parser.parse("ERROR: boom");
    parser.done(); // only meta mode reports at the end
    assertEquals(
        List.of(
            "destination video.mp4",
            "progress 30.5 277.93MiB 507.5KiB/s 06:29",
            "error ERROR: boom"),
        rec.events);
  }

  @Test
  void metaModeAccumulatesOutputAndReportsTheJsonAtTheEnd() {
    Recorder rec = new Recorder();
    TellurideParser parser = new TellurideParser(rec, true);
    parser.parse("warning: noise before {\"title\":");
    parser.parse("\"x\"}");
    parser.done();
    assertEquals(List.of("meta {\"title\":\"x\"}"), rec.events);
  }

  @Test
  void metaModeWithoutJsonOrOutputReportsAnError() {
    Recorder none = new Recorder();
    new TellurideParser(none, true).done();
    assertEquals(List.of("error Could not find downloadable media at the given URL"), none.events);

    Recorder noJson = new Recorder();
    TellurideParser parser = new TellurideParser(noJson, true);
    parser.parse("nothing useful");
    parser.done();
    assertEquals(
        List.of("error Could not find downloadable media at the given URL"), noJson.events);
  }

  @Test
  void metaCallbackFailuresBecomeErrors() {
    Recorder rec =
        new Recorder() {
          @Override
          public void onMeta(String json) {
            throw new IllegalStateException("bad json");
          }
        };
    TellurideParser parser = new TellurideParser(rec, true);
    parser.parse("{}");
    parser.done();
    assertEquals(List.of("error Could not find downloadable media at the given URL"), rec.events);
  }

  @Test
  void launcherRejectsMissingOrNonExecutableFiles(@TempDir File tmp) throws IOException {
    assertThrows(
        IllegalArgumentException.class,
        () -> TellurideLauncher.launch(null, "u", tmp, false, false, false, false, null));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            TellurideLauncher.launch(
                new File(tmp, "missing"), "u", tmp, false, false, false, false, null));
    File plain = new File(tmp, "plain");
    Files.writeString(plain.toPath(), "data");
    assertTrue(plain.setExecutable(false));
    Assumptions.assumeFalse(plain.canExecute(), "filesystem ignores the executable bit");
    assertThrows(
        IllegalArgumentException.class,
        () -> TellurideLauncher.launch(plain, "u", tmp, false, false, false, false, null));
  }

  private static File fakeTelluride(File dir) throws IOException {
    File script = new File(dir, "telluride.sh");
    Files.writeString(
        script.toPath(),
        "#!/bin/sh\n"
            + "echo \"ARGS:$*\"\n"
            + "echo '[download] Destination: out.mp4'\n"
            + "echo '[download]  50.0% of 10.00MiB at 1.00MiB/s ETA 00:05'\n"
            + "exit 3\n");
    assertTrue(script.setExecutable(true));
    return script;
  }

  @Test
  void launcherRunsTheExecutableAndStreamsItsOutputToTheListener(@TempDir File tmp)
      throws Exception {
    Assumptions.assumeTrue(
        new File("/bin/sh").canExecute() && !System.getProperty("os.name").startsWith("Windows"));
    File script = fakeTelluride(tmp);
    Recorder rec = new Recorder();
    TellurideLauncher.launch(script, "http://example.test/v", tmp, false, false, false, false, rec);
    assertTrue(rec.finished.await(30, TimeUnit.SECONDS));
    assertEquals(
        List.of("destination out.mp4", "progress 50.0 10.0MiB 1.0MiB/s 00:05", "finished 3"),
        rec.events);
  }

  @Test
  void launcherPassesModeFlagsAndHonoursAbort(@TempDir File tmp) throws Exception {
    Assumptions.assumeTrue(
        new File("/bin/sh").canExecute() && !System.getProperty("os.name").startsWith("Windows"));
    File script = fakeTelluride(tmp);
    Recorder meta = new Recorder();
    TellurideLauncher.launch(script, "http://example.test/v", tmp, false, true, false, false, meta);
    assertTrue(meta.finished.await(30, TimeUnit.SECONDS));
    // metadata mode collects the whole output and reports an error: it holds no JSON object
    assertTrue(meta.events.contains("error Could not find downloadable media at the given URL"));

    Recorder aborted = new Recorder();
    aborted.abort = true;
    TellurideLauncher.launch(
        script, "http://example.test/v", tmp, true, false, true, false, aborted);
    assertTrue(aborted.finished.await(30, TimeUnit.SECONDS));
    assertTrue(aborted.events.get(aborted.events.size() - 1).startsWith("finished"));
    for (String event : aborted.events) {
      assertTrue(event.startsWith("error") || event.startsWith("finished"), event);
    }
  }
}
