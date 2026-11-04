/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.btengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.bittorrent.DefaultTrackers;
import com.frostwire.jlibtorrent.AnnounceEntry;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.WebSeedEntry;
import com.frostwire.mcp.desktop.EngineContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.ConnectionSettings;
import com.limegroup.gnutella.settings.SharingSettings;
import com.limegroup.gnutella.util.FrostWireUtils;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The engine tools: status, pause and resume of the whole engine, and creating a .torrent from
 * files. The engine has startup's context but no session; created torrents go to a temp torrents
 * folder, and the setting is put back afterwards.
 */
class BTEngineToolsTest {

  @TempDir Path dir;

  private EngineContext context;
  private File torrentsDir;

  @BeforeEach
  void setUp() {
    context = EngineContext.install(dir.resolve("engine").toFile());
    torrentsDir = SharingSettings.TORRENTS_DIR_SETTING.getValue();
    SharingSettings.TORRENTS_DIR_SETTING.setValue(dir.resolve("torrents").toFile());
  }

  @AfterEach
  void tearDown() {
    BTEngine.getInstance().resume();
    SharingSettings.TORRENTS_DIR_SETTING.setValue(torrentsDir);
    context.close();
  }

  @Test
  void pauseAndResumeTheEngineAndReportItInTheStatus() {
    JsonObject paused = new BTEnginePauseTool().execute(new JsonObject());
    assertTrue(paused.get("paused").getAsBoolean());
    JsonObject whilePaused = new BTEngineStatusTool().execute(new JsonObject());
    assertTrue(whilePaused.get("paused").getAsBoolean());

    JsonObject resumed = new BTEngineResumeTool().execute(new JsonObject());
    assertFalse(resumed.get("paused").getAsBoolean());

    JsonObject status = new BTEngineStatusTool().execute(new JsonObject());
    assertFalse(status.get("paused").getAsBoolean());
    assertFalse(status.get("started").getAsBoolean(), "no session was started");
    assertEquals(0, status.get("totalDownload").getAsLong());
    assertEquals(0, status.get("totalUpload").getAsLong());
    assertEquals(0, status.get("dhtNodes").getAsLong());
    assertEquals(0, status.get("downloadRate").getAsLong());
    assertEquals(0, status.get("uploadRate").getAsLong());
    assertEquals(
        SharingSettings.ENABLE_DISTRIBUTED_HASH_TABLE.getValue(),
        status.get("dhtEnabled").getAsBoolean());
    assertEquals(
        ConnectionSettings.I2P_ENABLED.getValue(), status.get("i2pEnabled").getAsBoolean());
    assertEquals(
        ConnectionSettings.VPN_DROP_PROTECTION.getValue(),
        status.get("vpnDropProtection").getAsBoolean());
  }

  @Test
  void createTorrentWritesAHybridTorrentWithTheDefaultTrackers() throws Exception {
    Path movie = write("movie.mp4", 50_000);

    JsonObject out = new CreateTorrentTool().execute(path(movie));

    File torrentFile = new File(out.get("torrentPath").getAsString());
    assertEquals(dir.resolve("torrents").resolve("movie.mp4.torrent").toFile(), torrentFile);
    TorrentInfo torrent = new TorrentInfo(torrentFile);
    assertEquals("movie.mp4", torrent.name());
    assertEquals(50_000, torrent.totalSize());
    assertTrue(torrent.infoHashType().has_v1() && torrent.infoHashType().has_v2(), "hybrid");
    assertEquals(torrent.infoHashV1().toString(), out.get("infoHash").getAsString());
    assertTrue(out.get("magnetUri").getAsString().startsWith("magnet:?"));
    assertEquals("FrostWire " + FrostWireUtils.getFrostWireVersion(), torrent.creator());
    assertFalse(torrent.isPrivate());
    assertEquals(new HashSet<>(DefaultTrackers.ANNOUNCE_URLS), new HashSet<>(trackers(torrent)));
  }

  @Test
  void createTorrentUsesTheGivenTypeTrackersWebSeedsAndPieceSize() throws Exception {
    Path folder = Files.createDirectories(dir.resolve("album"));
    Files.write(folder.resolve("a.flac"), new byte[70_000]);
    Files.write(folder.resolve("b.flac"), new byte[30_000]);
    JsonObject args = path(folder);
    args.addProperty("torrentType", "v1");
    args.addProperty("pieceSize", 32_768);
    JsonArray trackers = new JsonArray();
    trackers.add("udp://tracker.example:6969/announce");
    trackers.add("");
    args.add("trackers", trackers);
    JsonArray seeds = new JsonArray();
    seeds.add("https://mirror.example/album/");
    seeds.add("");
    args.add("webSeeds", seeds);

    JsonObject out = new CreateTorrentTool().execute(args);

    TorrentInfo torrent = new TorrentInfo(new File(out.get("torrentPath").getAsString()));
    assertTrue(torrent.infoHashType().has_v1());
    assertFalse(torrent.infoHashType().has_v2(), "v1 only");
    assertEquals(32_768, torrent.pieceLength());
    assertEquals(2, torrent.numFiles());
    assertEquals(List.of("udp://tracker.example:6969/announce"), trackers(torrent));
    List<String> webSeeds = new ArrayList<>();
    for (WebSeedEntry seed : torrent.webSeeds()) {
      webSeeds.add(seed.url());
    }
    assertEquals(List.of("https://mirror.example/album/"), webSeeds);
  }

  @Test
  void createTorrentCanMakeAV2OnlyTorrent() throws Exception {
    JsonObject args = path(write("doc.pdf", 20_000));
    args.addProperty("torrentType", "v2");

    JsonObject out = new CreateTorrentTool().execute(args);

    TorrentInfo torrent = new TorrentInfo(new File(out.get("torrentPath").getAsString()));
    assertFalse(torrent.infoHashType().has_v1());
    assertTrue(torrent.infoHashType().has_v2());
    assertTrue(out.has("infoHash"));
  }

  @Test
  void createTorrentRejectsMissingOrBadPaths() throws Exception {
    CreateTorrentTool tool = new CreateTorrentTool();
    assertEquals("Missing required parameter: path", tool.execute(null).get("error").getAsString());
    assertEquals(
        "Missing required parameter: path",
        tool.execute(new JsonObject()).get("error").getAsString());
    JsonObject empty = new JsonObject();
    empty.addProperty("path", "");
    assertEquals(
        "path parameter must not be empty", tool.execute(empty).get("error").getAsString());
    Path missing = dir.resolve("missing.bin");
    assertEquals(
        "Path does not exist: " + missing, tool.execute(path(missing)).get("error").getAsString());

    Path emptyFolder = Files.createDirectories(dir.resolve("empty"));
    assertTrue(
        tool.execute(path(emptyFolder))
            .get("error")
            .getAsString()
            .startsWith("Failed to create torrent: "),
        "a torrent needs at least one file");
    assertEquals("path", tool.inputSchema().getAsJsonArray("required").get(0).getAsString());
  }

  private Path write(String name, int size) throws Exception {
    Path file = dir.resolve(name);
    Files.write(file, new byte[size]);
    return file;
  }

  private static JsonObject path(Path path) {
    JsonObject args = new JsonObject();
    args.addProperty("path", path.toString());
    return args;
  }

  private static List<String> trackers(TorrentInfo torrent) {
    List<String> urls = new ArrayList<>();
    for (AnnounceEntry tracker : torrent.trackers()) {
      urls.add(tracker.url());
    }
    return urls;
  }
}
