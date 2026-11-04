/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.mcp.MCPTool;
import com.frostwire.mcp.desktop.EngineContext;
import com.frostwire.mcp.desktop.adapters.TestTransfers;
import com.frostwire.mcp.desktop.adapters.TestTransfers.Download;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The MCP transfer tools find a transfer by info hash and act on it: list, detail, pause, resume,
 * remove, recheck, announce, sequential mode, speed limits and file priorities. The transfers are
 * real downloads over handles that record what was asked of them (see {@link TestTransfers}).
 */
class TransferToolsTest {

  private static final String UNKNOWN = "0123456789abcdef0123456789abcdef01234567";

  @TempDir Path dir;

  private Download ubuntu;
  private Download debian;

  @BeforeEach
  void twoTransfers() throws Exception {
    TorrentInfo ubuntuTorrent = TestTransfers.torrent(dir, "ubuntu", 40_000, 20_000);
    TorrentInfo debianTorrent = TestTransfers.torrent(dir, "debian", 10_000);
    ubuntu = TestTransfers.download(ubuntuTorrent, dir);
    debian = TestTransfers.download(debianTorrent, dir);
    TestTransfers.use(List.of(ubuntu.download(), debian.download()));
  }

  @AfterEach
  void forgetTransfers() {
    TestTransfers.reset();
  }

  @Test
  void everyToolNeedsADownloadIdAndReportsUnknownOnes() {
    for (MCPTool tool : idTools()) {
      assertEquals(
          "downloadId is required",
          tool.execute(new JsonObject()).get("error").getAsString(),
          tool.name());
      JsonObject args = validArguments(tool, UNKNOWN);
      assertEquals(
          "Download not found: " + UNKNOWN,
          tool.execute(args).get("error").getAsString(),
          tool.name());
      JsonArray required = tool.inputSchema().getAsJsonArray("required");
      assertEquals("downloadId", required.get(0).getAsString(), tool.name());
    }
  }

  @Test
  void everyToolReportsTransfersThatCannotBeRead() {
    TestTransfers.fail("table not ready");
    for (MCPTool tool : idTools()) {
      String error = tool.execute(validArguments(tool, ubuntu.id())).get("error").getAsString();
      assertTrue(error.startsWith("Failed to "), tool.name() + ": " + error);
      assertTrue(error.endsWith(": table not ready"), tool.name() + ": " + error);
    }
    assertEquals(
        "Failed to list transfers: table not ready",
        new TransfersListTool().execute(new JsonObject()).get("error").getAsString());
  }

  @Test
  void transfersAreFoundByInfoHashIgnoringCase() {
    JsonObject args = new JsonObject();
    args.addProperty("downloadId", debian.id().toUpperCase(Locale.ROOT));
    JsonObject paused = new TransferPauseTool().execute(args);
    assertEquals("paused", paused.get("state").getAsString());
    assertTrue(debian.handle().calls.contains("pause"));
    assertFalse(ubuntu.handle().calls.contains("pause"), "only the named transfer is paused");
  }

  @Test
  void pauseAndResumeDriveTheTorrent() {
    JsonObject paused = new TransferPauseTool().execute(id(ubuntu));
    assertEquals(ubuntu.id(), paused.get("downloadId").getAsString());
    assertEquals("paused", paused.get("state").getAsString());
    assertEquals(
        List.of("unset_flags", "pause"), ubuntu.handle().calls, "auto-management off first");

    ubuntu.handle().calls.clear();
    JsonObject resumed = new TransferResumeTool().execute(id(ubuntu));
    assertEquals(ubuntu.id(), resumed.get("downloadId").getAsString());
    assertEquals("STOPPED", resumed.get("state").getAsString(), "the engine isn't running");
    assertEquals(List.of("set_flags", "resume"), ubuntu.handle().calls);
  }

  @Test
  void recheckAndAnnounceReachTheTorrent() {
    JsonObject recheck = new TransferRecheckTool().execute(id(ubuntu));
    assertEquals("checking", recheck.get("state").getAsString());
    assertTrue(ubuntu.handle().calls.contains("force_recheck"));

    JsonObject announce = new TransferAnnounceTool().execute(id(ubuntu));
    assertTrue(announce.get("announced").getAsBoolean());
    assertTrue(ubuntu.handle().calls.contains("force_reannounce"));
  }

  @Test
  void removeTakesTheTransferOutOfTheSession() {
    EngineContext engine = EngineContext.install(dir.toFile());
    try {
      JsonObject args = id(ubuntu);
      args.addProperty("deleteData", true);
      JsonObject removed = new TransferRemoveTool().execute(args);
      assertEquals(ubuntu.id(), removed.get("downloadId").getAsString());
      assertTrue(removed.get("removed").getAsBoolean());
    } finally {
      engine.close();
    }
  }

  @Test
  void sequentialModeIsSetAndCleared() {
    JsonObject missing = new TransferSequentialTool().execute(id(ubuntu));
    assertEquals("sequential is required", missing.get("error").getAsString());

    JsonObject on = id(ubuntu);
    on.addProperty("sequential", true);
    JsonObject enabled = new TransferSequentialTool().execute(on);
    assertTrue(enabled.get("sequential").getAsBoolean());
    assertEquals(List.of("set_flags"), ubuntu.handle().calls);

    on.addProperty("sequential", false);
    assertFalse(new TransferSequentialTool().execute(on).get("sequential").getAsBoolean());
    assertEquals(List.of("set_flags", "unset_flags"), ubuntu.handle().calls);
  }

  @Test
  void speedLimitsAreSetOneOrBothAtATime() {
    assertEquals(
        "At least one of downloadLimit or uploadLimit is required",
        new TransferSpeedLimitTool().execute(id(ubuntu)).get("error").getAsString());

    JsonObject both = id(ubuntu);
    both.addProperty("downloadLimit", 100_000);
    both.addProperty("uploadLimit", 20_000);
    JsonObject set = new TransferSpeedLimitTool().execute(both);
    assertEquals(100_000, set.get("downloadLimit").getAsInt());
    assertEquals(20_000, set.get("uploadLimit").getAsInt());
    assertEquals(100_000, ubuntu.handle().downloadLimit);
    assertEquals(20_000, ubuntu.handle().uploadLimit);

    JsonObject uploadOnly = id(ubuntu);
    uploadOnly.addProperty("uploadLimit", 0);
    JsonObject changed = new TransferSpeedLimitTool().execute(uploadOnly);
    assertEquals(100_000, changed.get("downloadLimit").getAsInt(), "the other limit is reported");
    assertEquals(0, changed.get("uploadLimit").getAsInt());
    assertEquals(100_000, ubuntu.handle().downloadLimit);
  }

  @Test
  void filePrioritiesAreSetForTheFilesThatExist() {
    JsonObject args = priority(ubuntu, "high", 0, 7, -1);
    JsonObject out = new TransferFilePriorityTool().execute(args);
    assertEquals(1, out.get("updatedFiles").getAsInt(), "indices outside the torrent are skipped");
    assertEquals(Priority.SEVEN.swig(), ubuntu.handle().priorities[0]);
    assertEquals(Priority.FOUR.swig(), ubuntu.handle().priorities[1]);

    new TransferFilePriorityTool().execute(priority(ubuntu, "SKIP", 1));
    assertEquals(Priority.IGNORE.swig(), ubuntu.handle().priorities[1]);
    new TransferFilePriorityTool().execute(priority(ubuntu, "download", 0));
    assertEquals(Priority.NORMAL.swig(), ubuntu.handle().priorities[0]);
    new TransferFilePriorityTool().execute(priority(ubuntu, "normal", 1));
    assertEquals(Priority.NORMAL.swig(), ubuntu.handle().priorities[1]);

    ubuntu.handle().calls.clear();
    JsonObject none = new TransferFilePriorityTool().execute(priority(ubuntu, "high", 9));
    assertEquals(0, none.get("updatedFiles").getAsInt());
    assertFalse(ubuntu.handle().calls.contains("prioritize_files"), "nothing to change");
  }

  @Test
  void filePriorityValidatesItsArguments() {
    TransferFilePriorityTool tool = new TransferFilePriorityTool();
    assertEquals("priority is required", tool.execute(id(ubuntu)).get("error").getAsString());

    JsonObject noIndices = id(ubuntu);
    noIndices.addProperty("priority", "high");
    assertEquals(
        "fileIndices array is required", tool.execute(noIndices).get("error").getAsString());
    noIndices.addProperty("fileIndices", 0);
    assertEquals(
        "fileIndices array is required", tool.execute(noIndices).get("error").getAsString());

    assertEquals(
        "Invalid priority: urgent. Use download, skip, normal, or high.",
        tool.execute(priority(ubuntu, "urgent", 0)).get("error").getAsString());

    ubuntu.handle().valid = false;
    assertEquals(
        "Torrent handle is not valid for: " + ubuntu.id(),
        tool.execute(priority(ubuntu, "high", 0)).get("error").getAsString());
  }

  @Test
  void detailDescribesTheTransferAndItsFiles() {
    ubuntu.handle().status().setTotal_done(15_000);
    ubuntu.handle().status().setTotal_upload(3_000);
    ubuntu.handle().status().setNum_seeds(4);
    ubuntu.handle().status().setList_seeds(9);
    ubuntu.handle().status().setNum_peers(6);
    ubuntu.handle().status().setList_peers(30);
    ubuntu.handle().status().setProgress(0.25f);
    ubuntu.handle().progress = new long[] {10_000, 5_000};
    ubuntu.download().refreshStatusCache();

    JsonObject detail = new TransferDetailTool().execute(id(ubuntu));

    assertEquals(ubuntu.id(), detail.get("id").getAsString());
    assertEquals(ubuntu.id(), detail.get("infoHash").getAsString());
    assertEquals("ubuntu", detail.get("name").getAsString());
    assertEquals("ubuntu", detail.get("displayName").getAsString(), "a folder of two files");
    assertEquals("STOPPED", detail.get("state").getAsString());
    assertEquals(25, detail.get("progress").getAsInt());
    assertEquals(60_000, detail.get("size").getAsLong());
    assertEquals(15_000, detail.get("downloaded").getAsLong());
    assertEquals(3_000, detail.get("uploaded").getAsLong());
    assertEquals(0, detail.get("downloadSpeed").getAsLong(), "a stopped transfer moves nothing");
    assertEquals(4, detail.getAsJsonObject("seeds").get("connected").getAsInt());
    assertEquals(9, detail.getAsJsonObject("seeds").get("total").getAsInt());
    assertEquals(6, detail.getAsJsonObject("peers").get("connected").getAsInt());
    assertEquals(30, detail.getAsJsonObject("peers").get("total").getAsInt());
    assertEquals(dir.toFile().getAbsolutePath(), detail.get("savePath").getAsString());
    assertFalse(detail.get("sequential").getAsBoolean());
    assertTrue(detail.has("magnetUri"));
    assertEquals(-1, detail.get("eta").getAsLong(), "data left and no download rate");

    JsonArray items = detail.getAsJsonArray("items");
    assertEquals(2, items.size());
    JsonObject first = items.get(0).getAsJsonObject();
    assertEquals("file0.bin", first.get("name").getAsString());
    assertEquals(40_000, first.get("size").getAsLong());
    assertEquals(10_000, first.get("downloaded").getAsLong());
    assertEquals(25, first.get("progress").getAsInt());
    assertFalse(first.get("complete").getAsBoolean());
    assertFalse(first.get("skipped").getAsBoolean());
    assertTrue(first.get("path").getAsString().endsWith("file0.bin"));
    JsonObject second = items.get(1).getAsJsonObject();
    assertEquals(5_000, second.get("downloaded").getAsLong());
  }

  @Test
  void listPagesAndFiltersTransfersByState() {
    JsonObject all = new TransfersListTool().execute(new JsonObject());
    assertEquals(2, all.get("total").getAsInt());
    assertEquals(0, all.get("offset").getAsInt());
    assertEquals(50, all.get("limit").getAsInt());
    JsonArray transfers = all.getAsJsonArray("transfers");
    assertEquals(ubuntu.id(), transfers.get(0).getAsJsonObject().get("id").getAsString());
    assertEquals(debian.id(), transfers.get(1).getAsJsonObject().get("id").getAsString());

    JsonObject page = new JsonObject();
    page.addProperty("offset", 1);
    page.addProperty("limit", 5);
    JsonObject second = new TransfersListTool().execute(page);
    assertEquals(1, second.getAsJsonArray("transfers").size());
    assertEquals(
        debian.id(),
        second.getAsJsonArray("transfers").get(0).getAsJsonObject().get("id").getAsString());

    page.addProperty("offset", 7);
    assertEquals(0, new TransfersListTool().execute(page).getAsJsonArray("transfers").size());

    // The engine isn't running, so both transfers are STOPPED: no state filter matches them.
    for (String filter : List.of("downloading", "seeding", "completed", "paused", "error")) {
      assertEquals(0, list(filter).get("total").getAsInt(), filter);
    }
    assertEquals(2, list("ALL").get("total").getAsInt());
    assertEquals(2, list("whatever").get("total").getAsInt(), "an unknown filter keeps everything");
  }

  private static JsonObject list(String filter) {
    JsonObject args = new JsonObject();
    args.addProperty("filter", filter);
    return new TransfersListTool().execute(args);
  }

  private static JsonObject id(Download download) {
    JsonObject args = new JsonObject();
    args.addProperty("downloadId", download.id());
    return args;
  }

  private static JsonObject priority(Download download, String priority, int... indices) {
    JsonObject args = id(download);
    args.addProperty("priority", priority);
    JsonArray array = new JsonArray();
    for (int index : indices) {
      array.add(index);
    }
    args.add("fileIndices", array);
    return args;
  }

  /** Tools that act on one transfer named by its info hash. */
  private static List<MCPTool> idTools() {
    return List.of(
        new TransferDetailTool(),
        new TransferPauseTool(),
        new TransferResumeTool(),
        new TransferRemoveTool(),
        new TransferRecheckTool(),
        new TransferAnnounceTool(),
        new TransferSequentialTool(),
        new TransferSpeedLimitTool(),
        new TransferFilePriorityTool());
  }

  /** Arguments that pass a tool's own checks, so only the download lookup can fail. */
  private static JsonObject validArguments(MCPTool tool, String downloadId) {
    JsonObject args = new JsonObject();
    args.addProperty("downloadId", downloadId);
    args.addProperty("sequential", true);
    args.addProperty("downloadLimit", 1);
    args.addProperty("priority", "high");
    args.add("fileIndices", new JsonArray());
    return args;
  }
}
