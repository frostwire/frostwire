/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.search.telluride.TellurideListener;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SharingSettings;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cloud tools ask Telluride for a web page's media formats and download its media. Telluride is
 * a fake that records how it was started and answers through the listener at once, the way the real
 * one reports metadata, errors and its exit code.
 */
class CloudToolsTest {

  private static final String PAGE = "https://video.example/watch?v=1";

  @TempDir Path dir;

  private FakeTelluride telluride;

  @BeforeEach
  void installAFakeTelluride() throws IOException {
    telluride = new FakeTelluride(Files.createFile(dir.resolve("telluride")).toFile());
  }

  @Test
  void searchReturnsTheMediaFormats() {
    telluride.answer =
        listener ->
            listener.onMeta(
                "{\"id\":\"v1\",\"title\":\"A video\",\"thumbnail\":\"https://img/1.jpg\","
                    + "\"formats\":["
                    + "{\"format_id\":\"18\",\"ext\":\"mp4\",\"resolution\":\"640x360\","
                    + "\"filesize\":1000,\"vcodec\":\"avc1\",\"acodec\":\"mp4a\"},"
                    + "{\"format_id\":\"140\",\"ext\":\"m4a\",\"filesize\":null},"
                    + "\"junk\"]}");

    JsonObject out = new CloudSearchTool(telluride).execute(url(PAGE));

    assertEquals(PAGE, telluride.url);
    assertTrue(telluride.metaOnly, "a search only reads metadata");
    assertNull(telluride.saveDir);
    assertEquals("v1", out.get("id").getAsString());
    assertEquals("A video", out.get("title").getAsString());
    assertEquals("https://img/1.jpg", out.get("thumbnail").getAsString());
    JsonArray formats = out.getAsJsonArray("formats");
    assertEquals(2, formats.size(), "entries that aren't objects are skipped");
    JsonObject video = formats.get(0).getAsJsonObject();
    assertEquals("18", video.get("formatId").getAsString());
    assertEquals("mp4", video.get("ext").getAsString());
    assertEquals("640x360", video.get("resolution").getAsString());
    assertEquals(1000, video.get("fileSize").getAsLong());
    assertEquals("avc1", video.get("vcodec").getAsString());
    assertEquals("mp4a", video.get("acodec").getAsString());
    JsonObject audio = formats.get(1).getAsJsonObject();
    assertFalse(audio.has("fileSize"), "an unknown size is left out");
    assertFalse(audio.has("resolution"));
  }

  @Test
  void searchReportsWhatWentWrong() {
    telluride.answer = listener -> listener.onError("Unsupported URL");
    assertEquals("Unsupported URL", search().get("error").getAsString());

    telluride.answer = listener -> listener.onFinished(0);
    assertEquals("No metadata received from cloud source", search().get("error").getAsString());

    telluride.answer = listener -> listener.onMeta("[1,2]");
    assertEquals("Invalid metadata format received", search().get("error").getAsString());

    telluride.answer = listener -> listener.onMeta("{\"title\":");
    assertTrue(search().get("error").getAsString().startsWith("Failed to parse metadata: "));

    telluride.answer = listener -> listener.onMeta("{\"formats\":{}}");
    JsonObject noFormats = search();
    assertFalse(noFormats.has("error"));
    assertFalse(noFormats.has("formats"), "formats that aren't a list are left out");
  }

  @Test
  void downloadCompletesIntoTheGivenFolder() {
    telluride.answer =
        listener -> {
          listener.onDestination("/videos/A video.mp4");
          listener.onFinished(0);
        };
    JsonObject args = url(PAGE);
    args.addProperty("saveDir", dir.toString());
    args.addProperty("audioOnly", true);

    JsonObject out = new CloudDownloadTool(telluride).execute(args);

    assertEquals("completed", out.get("state").getAsString());
    assertEquals("/videos/A video.mp4", out.get("destination").getAsString());
    assertTrue(out.get("downloadId").getAsString().matches("[0-9a-f-]{36}"));
    assertEquals(dir.toFile(), telluride.saveDir);
    assertTrue(telluride.audioOnly);
    assertFalse(telluride.metaOnly);
  }

  @Test
  void downloadDefaultsToTheTorrentDataFolder() {
    telluride.answer = listener -> listener.onFinished(0);

    JsonObject out = new CloudDownloadTool(telluride).execute(url(PAGE));

    assertEquals("completed", out.get("state").getAsString());
    assertFalse(out.has("destination"));
    assertEquals(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), telluride.saveDir);
    assertFalse(telluride.audioOnly);
  }

  @Test
  void downloadReportsFailures() {
    telluride.answer = listener -> listener.onFinished(2);
    JsonObject exit = new CloudDownloadTool(telluride).execute(url(PAGE));
    assertEquals("failed", exit.get("state").getAsString());
    assertEquals(2, exit.get("exitCode").getAsInt());

    telluride.answer = listener -> listener.onError("HTTP Error 403");
    JsonObject error = new CloudDownloadTool(telluride).execute(url(PAGE));
    assertEquals("failed", error.get("state").getAsString());
    assertEquals("HTTP Error 403", error.get("error").getAsString());
  }

  @Test
  void bothToolsNeedTelluride() {
    telluride.executable = new File(dir.toFile(), "missing");
    assertEquals(
        "Telluride executable not found",
        new CloudSearchTool(telluride).execute(url(PAGE)).get("error").getAsString());
    assertEquals(
        "Telluride executable not found",
        new CloudDownloadTool(telluride).execute(url(PAGE)).get("error").getAsString());

    telluride.executable = null;
    assertEquals(
        "Telluride executable not found",
        new CloudSearchTool(telluride).execute(url(PAGE)).get("error").getAsString());
    assertNull(telluride.url, "Telluride was never started");
  }

  @Test
  void bothToolsDeclareTheUrlArgument() {
    for (JsonObject schema :
        new JsonObject[] {
          new CloudSearchTool().inputSchema(), new CloudDownloadTool().inputSchema()
        }) {
      assertEquals("url", schema.getAsJsonArray("required").get(0).getAsString());
      assertTrue(schema.getAsJsonObject("properties").has("url"));
    }
  }

  private JsonObject search() {
    return new CloudSearchTool(telluride).execute(url(PAGE));
  }

  private static JsonObject url(String url) {
    JsonObject args = new JsonObject();
    args.addProperty("url", url);
    return args;
  }

  /** Records how it was started and answers through the listener before returning. */
  private static final class FakeTelluride implements TellurideRunner {
    File executable;
    Consumer<TellurideListener> answer = listener -> listener.onFinished(0);
    String url;
    File saveDir;
    boolean audioOnly;
    boolean metaOnly;

    FakeTelluride(File executable) {
      this.executable = executable;
    }

    @Override
    public File executable() {
      return executable;
    }

    @Override
    public void launch(
        File executable,
        String url,
        File saveDir,
        boolean audioOnly,
        boolean metaOnly,
        TellurideListener listener) {
      this.url = url;
      this.saveDir = saveDir;
      this.audioOnly = audioOnly;
      this.metaOnly = metaOnly;
      answer.accept(listener);
    }
  }
}
