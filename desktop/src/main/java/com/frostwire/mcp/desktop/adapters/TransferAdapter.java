/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.adapters;

import com.frostwire.bittorrent.BTDownload;
import com.frostwire.gui.bittorrent.BTDownloadMediator;
import com.frostwire.gui.bittorrent.BittorrentDownload;
import com.frostwire.transfers.TransferItem;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.SwingUtilities;

public final class TransferAdapter {

  private TransferAdapter() {}

  public static JsonObject toSummaryJson(BTDownload dl) {
    JsonObject json = new JsonObject();
    json.addProperty("id", dl.getInfoHash());
    json.addProperty("name", dl.getName());
    json.addProperty("displayName", dl.getDisplayName());
    json.addProperty("state", dl.getState().name());
    json.addProperty("progress", dl.getProgress());
    json.addProperty("size", dl.getSize());
    json.addProperty("downloaded", dl.getBytesReceived());
    json.addProperty("uploaded", dl.getBytesSent());
    json.addProperty("downloadSpeed", dl.getDownloadSpeed());
    json.addProperty("uploadSpeed", dl.getUploadSpeed());
    json.addProperty("eta", dl.getETA());

    JsonObject seeds = new JsonObject();
    seeds.addProperty("connected", dl.getConnectedSeeds());
    seeds.addProperty("total", dl.getTotalSeeds());
    json.add("seeds", seeds);

    JsonObject peers = new JsonObject();
    peers.addProperty("connected", dl.getConnectedPeers());
    peers.addProperty("total", dl.getTotalPeers());
    json.add("peers", peers);

    json.addProperty("infoHash", dl.getInfoHash());
    json.addProperty(
        "savePath", dl.getSavePath() != null ? dl.getSavePath().getAbsolutePath() : "");
    json.addProperty("magnetUri", dl.magnetUri() != null ? dl.magnetUri() : "");
    json.addProperty("sequential", dl.isSequentialDownload());

    return json;
  }

  public static JsonObject toItemJson(TransferItem item) {
    JsonObject json = new JsonObject();
    json.addProperty("name", item.getName());
    json.addProperty("size", item.getSize());
    json.addProperty("downloaded", item.getDownloaded());
    json.addProperty("progress", item.getProgress());
    json.addProperty("complete", item.isComplete());
    json.addProperty("skipped", item.isSkipped());
    json.addProperty("path", item.getFile() != null ? item.getFile().getAbsolutePath() : "");
    return json;
  }

  public static final int MAX_TRANSFER_FILE_ROWS = 200;

  public static JsonArray toItemsJson(List<TransferItem> items) {
    return toItemsJson(items, items == null ? 0 : items.size());
  }

  public static JsonArray toItemsJson(List<TransferItem> items, int limit) {
    JsonArray arr = new JsonArray();
    if (items == null || limit <= 0) {
      return arr;
    }
    int end = Math.min(items.size(), limit);
    for (int i = 0; i < end; i++) {
      arr.add(toItemJson(items.get(i)));
    }
    return arr;
  }

  public static JsonObject transferFilesEntry(
      String downloadId, String name, List<TransferItem> items, int remaining) {
    int total = items == null ? 0 : items.size();
    int take = remaining > 0 && total > 0 ? Math.min(total, remaining) : 0;
    JsonObject entry = new JsonObject();
    entry.addProperty("downloadId", downloadId);
    entry.addProperty("name", name);
    entry.addProperty("fileCount", total);
    entry.addProperty("truncated", take < total);
    entry.add("files", toItemsJson(items, take));
    return entry;
  }

  public static JsonObject omittedTransferFilesEntry(String downloadId, String name) {
    JsonObject entry = new JsonObject();
    entry.addProperty("downloadId", downloadId);
    entry.addProperty("name", name);
    entry.addProperty("truncated", true);
    entry.add("files", new JsonArray());
    return entry;
  }

  public static JsonArray boundTransferFiles(
      int count,
      int maxRows,
      java.util.function.IntFunction<NamedTransfer> names,
      java.util.function.IntFunction<List<TransferItem>> items) {
    JsonArray allFiles = new JsonArray();
    int remaining = maxRows;
    for (int i = 0; i < count; i++) {
      NamedTransfer named = names.apply(i);
      if (remaining <= 0) {
        allFiles.add(omittedTransferFilesEntry(named.id(), named.name()));
        continue;
      }
      List<TransferItem> files = items.apply(i);
      JsonObject entry = transferFilesEntry(named.id(), named.name(), files, remaining);
      allFiles.add(entry);
      remaining -= entry.getAsJsonArray("files").size();
    }
    return allFiles;
  }

  public record NamedTransfer(String id, String name) {}

  public static BTDownload findDownload(String downloadId) {
    for (BTDownload dl : getAllDownloads()) {
      if (dl.getInfoHash() != null && dl.getInfoHash().equalsIgnoreCase(downloadId)) {
        return dl;
      }
    }
    return null;
  }

  public static List<BTDownload> getAllDownloads() {
    List<com.frostwire.gui.bittorrent.BTDownload> guiDownloads = snapshotGuiDownloads();
    List<BTDownload> result = new ArrayList<>(guiDownloads.size());
    for (com.frostwire.gui.bittorrent.BTDownload btDownload : guiDownloads) {
      if (btDownload instanceof BittorrentDownload) {
        BTDownload dl = ((BittorrentDownload) btDownload).getDl();
        if (dl != null) {
          result.add(dl);
        }
      }
    }
    return result;
  }

  private static List<com.frostwire.gui.bittorrent.BTDownload> snapshotGuiDownloads() {
    if (SwingUtilities.isEventDispatchThread()) {
      return BTDownloadMediator.instance().getDataModel().snapshotAllDownloads();
    }
    FutureTask<List<com.frostwire.gui.bittorrent.BTDownload>> snapshot =
        new FutureTask<>(() -> BTDownloadMediator.instance().getDataModel().snapshotAllDownloads());
    SwingUtilities.invokeLater(snapshot);
    try {
      return snapshot.get(2, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      snapshot.cancel(false);
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while reading transfers", e);
    } catch (ExecutionException | TimeoutException e) {
      snapshot.cancel(false);
      throw new IllegalStateException("Could not snapshot transfers from the UI", e);
    }
  }

  public static JsonObject errorJson(String message) {
    JsonObject json = new JsonObject();
    json.addProperty("error", message);
    return json;
  }
}
