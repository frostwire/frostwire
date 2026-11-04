/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.frostwire.mcp.desktop.adapters.LibraryAdapter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SharingSettings;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The MCP library tools list, scan, describe, open and delete files under the torrent data folder.
 * The folder setting points at a temp folder for each test and is restored afterwards.
 */
class LibraryToolsTest {

  @TempDir Path dataDir;

  private File originalDataDir;

  @BeforeEach
  void pointTheDataFolderAtATempFolder() throws IOException {
    originalDataDir = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
    SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(dataDir.toFile());
    write("song.mp3", 3, 3_000);
    write("Movie.MKV", 10, 1_000);
    write("notes.txt", 5, 2_000);
    write("album/cover.jpg", 7, 4_000);
    write("album/ubuntu.torrent", 1, 5_000);
    write("album/README", 2, 6_000);
  }

  @AfterEach
  void restoreTheDataFolder() {
    SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(originalDataDir);
  }

  @Test
  void listReturnsEveryFileSortedByNameByDefault() {
    JsonObject out = new LibraryListTool().execute(new JsonObject());

    assertEquals(
        List.of("cover.jpg", "Movie.MKV", "notes.txt", "README", "song.mp3", "ubuntu.torrent"),
        names(out));
    assertEquals(6, out.get("total").getAsInt());
    assertEquals(0, out.get("offset").getAsInt());
    assertEquals(100, out.get("limit").getAsInt());
  }

  @Test
  void listFiltersByMediaTypeAndSortsBySizeOrDate() {
    assertEquals(List.of("song.mp3"), names(list("mediaType", "audio")));
    assertEquals(List.of("Movie.MKV"), names(list("mediaType", "video")));
    assertEquals(List.of("cover.jpg"), names(list("mediaType", "images")));
    assertEquals(List.of("notes.txt"), names(list("mediaType", "documents")));
    assertEquals(List.of("ubuntu.torrent"), names(list("mediaType", "torrents")));
    assertEquals(List.of("README"), names(list("mediaType", "other")));

    assertEquals(
        List.of("Movie.MKV", "cover.jpg", "notes.txt", "song.mp3", "README", "ubuntu.torrent"),
        names(list("sortBy", "size")),
        "largest first");
    assertEquals(
        List.of("README", "ubuntu.torrent", "cover.jpg", "song.mp3", "notes.txt", "Movie.MKV"),
        names(list("sortBy", "date")),
        "newest first");
  }

  @Test
  void listPagesAndClampsOffsetAndLimit() {
    JsonObject args = new JsonObject();
    args.addProperty("offset", 4);
    args.addProperty("limit", 10);
    JsonObject tail = new LibraryListTool().execute(args);
    assertEquals(List.of("song.mp3", "ubuntu.torrent"), names(tail));
    assertEquals(6, tail.get("total").getAsInt());

    args.addProperty("offset", -5);
    args.addProperty("limit", 0);
    JsonObject first = new LibraryListTool().execute(args);
    assertEquals(0, first.get("offset").getAsInt());
    assertEquals(1, first.get("limit").getAsInt());
    assertEquals(List.of("cover.jpg"), names(first));

    args.addProperty("limit", 50_000);
    assertEquals(1000, new LibraryListTool().execute(args).get("limit").getAsInt());
  }

  @Test
  void listDescribesEachFile() {
    JsonObject song = file(list("mediaType", "audio"), 0);

    assertEquals("song.mp3", song.get("name").getAsString());
    assertEquals(
        dataDir.resolve("song.mp3").toFile().getAbsolutePath(), song.get("path").getAsString());
    assertEquals(3, song.get("size").getAsLong());
    assertEquals(3_000_000, song.get("lastModified").getAsLong());
    assertEquals("mp3", song.get("extension").getAsString());
    assertEquals("audio", song.get("mediaType").getAsString());
    assertTrue(song.get("playable").getAsBoolean());

    JsonObject readme = file(list("mediaType", "other"), 0);
    assertEquals("", readme.get("extension").getAsString());
    assertFalse(readme.get("playable").getAsBoolean());
  }

  @Test
  void listAndScanReportAMissingDataFolder() {
    File missing = dataDir.resolve("gone").toFile();
    SharingSettings.TORRENT_DATA_DIR_SETTING.setValue(missing);

    assertEquals(
        "Torrent data directory does not exist: " + missing.getAbsolutePath(),
        new LibraryListTool().execute(new JsonObject()).get("error").getAsString());
    assertEquals(
        "Directory does not exist: " + missing.getAbsolutePath(),
        new LibraryScanTool().execute(null).get("error").getAsString());
  }

  @Test
  void scanCountsFilesInTheDataFolderOrAGivenFolder() {
    JsonObject all = new LibraryScanTool().execute(new JsonObject());
    assertTrue(all.get("scanned").getAsBoolean());
    assertEquals(6, all.get("fileCount").getAsInt());
    assertEquals(dataDir.toFile().getAbsolutePath(), all.get("path").getAsString());

    JsonObject relative = new JsonObject();
    relative.addProperty("path", "album");
    JsonObject album = new LibraryScanTool().execute(relative);
    assertEquals(3, album.get("fileCount").getAsInt(), "a relative path is inside the data folder");
    assertEquals(
        dataDir.resolve("album").toFile().getAbsolutePath(), album.get("path").getAsString());

    JsonObject absolute = new JsonObject();
    absolute.addProperty("path", dataDir.resolve("album").toString());
    absolute.addProperty("mediaType", "images");
    assertEquals(1, new LibraryScanTool().execute(absolute).get("fileCount").getAsInt());

    JsonObject notADirectory = new JsonObject();
    notADirectory.addProperty("path", "song.mp3");
    assertTrue(new LibraryScanTool().execute(notADirectory).has("error"));
  }

  @Test
  void fileDetailDescribesOneFileAndRejectsBadPaths() {
    JsonObject args = new JsonObject();
    args.addProperty("path", dataDir.resolve("notes.txt").toString());
    JsonObject notes = new LibraryFileDetailTool().execute(args);
    assertEquals("notes.txt", notes.get("name").getAsString());
    assertEquals("documents", notes.get("mediaType").getAsString());
    assertEquals(5, notes.get("size").getAsLong());

    assertError(
        new LibraryFileDetailTool().execute(new JsonObject()), "Missing required parameter: path");
    assertError(new LibraryFileDetailTool().execute(null), "Missing required parameter: path");
    assertError(new LibraryFileDetailTool().execute(path("")), "path parameter must not be empty");
    String missing = dataDir.resolve("missing.mp3").toString();
    assertError(
        new LibraryFileDetailTool().execute(path(missing)), "File does not exist: " + missing);
    String album = dataDir.resolve("album").toString();
    assertError(
        new LibraryFileDetailTool().execute(path(album)),
        "Path is a directory, not a file: " + album);
  }

  @Test
  void deleteRemovesExistingFilesAndCountsFailures() {
    JsonArray paths = new JsonArray();
    paths.add(dataDir.resolve("song.mp3").toString());
    paths.add(dataDir.resolve("notes.txt").toString());
    paths.add(dataDir.resolve("missing.mp3").toString());
    paths.add("");
    paths.add(dataDir.resolve("album").toString()); // not empty, so it can't be deleted
    JsonObject args = new JsonObject();
    args.add("paths", paths);

    JsonObject out = new LibraryDeleteTool().execute(args);

    assertEquals(2, out.get("deleted").getAsInt());
    assertEquals(3, out.get("failed").getAsInt());
    assertFalse(Files.exists(dataDir.resolve("song.mp3")));
    assertFalse(Files.exists(dataDir.resolve("notes.txt")));
    assertTrue(Files.exists(dataDir.resolve("album/cover.jpg")));
  }

  @Test
  void deleteRejectsMissingOrMalformedPaths() {
    assertError(new LibraryDeleteTool().execute(null), "Missing required parameter: paths");
    assertError(
        new LibraryDeleteTool().execute(new JsonObject()), "Missing required parameter: paths");
    JsonObject notArray = new JsonObject();
    notArray.addProperty("paths", dataDir.resolve("song.mp3").toString());
    assertError(new LibraryDeleteTool().execute(notArray), "paths parameter must be an array");
    JsonObject empty = new JsonObject();
    empty.add("paths", new JsonArray());
    assertError(new LibraryDeleteTool().execute(empty), "paths array must not be empty");
    assertTrue(Files.exists(dataDir.resolve("song.mp3")));
  }

  @Test
  void openValidatesThePathBeforeOpeningIt() {
    assertError(new LibraryOpenTool().execute(null), "Missing required parameter: path");
    assertError(
        new LibraryOpenTool().execute(new JsonObject()), "Missing required parameter: path");
    assertError(new LibraryOpenTool().execute(path("")), "path parameter must not be empty");
    String missing = dataDir.resolve("missing.mp3").toString();
    assertError(new LibraryOpenTool().execute(path(missing)), "File does not exist: " + missing);
  }

  @Test
  void openReportsWhenTheDesktopCannotOpenFiles() {
    // Without a display there is no desktop to open the file with; never open one for real.
    assumeTrue(GraphicsEnvironment.isHeadless(), "only runs headless");
    JsonObject out = new LibraryOpenTool().execute(path(dataDir.resolve("song.mp3").toString()));
    assertTrue(out.get("error").getAsString().startsWith("Failed to open file: "), out.toString());
    assertFalse(out.has("opened"));
  }

  @Test
  void mediaTypesFollowTheExtensionIgnoringCase() {
    assertEquals("audio", LibraryAdapter.getMediaType("FLAC"));
    assertEquals("video", LibraryAdapter.getMediaType("webm"));
    assertEquals("images", LibraryAdapter.getMediaType("PNG"));
    assertEquals("documents", LibraryAdapter.getMediaType("epub"));
    assertEquals("torrents", LibraryAdapter.getMediaType("torrent"));
    assertEquals("other", LibraryAdapter.getMediaType("exe"));
    assertEquals("other", LibraryAdapter.getMediaType(""));
    assertEquals("other", LibraryAdapter.getMediaType(null));
  }

  @Test
  void toolsDeclareTheirRequiredArguments() {
    assertEquals("path", required(new LibraryFileDetailTool().inputSchema()));
    assertEquals("path", required(new LibraryOpenTool().inputSchema()));
    assertEquals("paths", required(new LibraryDeleteTool().inputSchema()));
    assertFalse(new LibraryListTool().inputSchema().has("required"));
    assertFalse(new LibraryScanTool().inputSchema().has("required"));
  }

  private static String required(JsonObject schema) {
    JsonArray required = schema.getAsJsonArray("required");
    assertEquals(1, required.size());
    String name = required.get(0).getAsString();
    assertTrue(schema.getAsJsonObject("properties").has(name));
    return name;
  }

  private void write(String relative, int size, long lastModifiedSeconds) throws IOException {
    Path file = dataDir.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.write(file, new byte[size]);
    assertTrue(file.toFile().setLastModified(lastModifiedSeconds * 1000));
  }

  private static JsonObject list(String key, String value) {
    JsonObject args = new JsonObject();
    args.addProperty(key, value);
    return new LibraryListTool().execute(args);
  }

  private static JsonObject path(String path) {
    JsonObject args = new JsonObject();
    args.addProperty("path", path);
    return args;
  }

  private static JsonObject file(JsonObject listing, int index) {
    return listing.getAsJsonArray("files").get(index).getAsJsonObject();
  }

  private static List<String> names(JsonObject listing) {
    List<String> names = new ArrayList<>();
    for (var element : listing.getAsJsonArray("files")) {
      names.add(element.getAsJsonObject().get("name").getAsString());
    }
    return names;
  }

  private static void assertError(JsonObject out, String message) {
    assertEquals(message, out.get("error").getAsString(), out.toString());
  }
}
