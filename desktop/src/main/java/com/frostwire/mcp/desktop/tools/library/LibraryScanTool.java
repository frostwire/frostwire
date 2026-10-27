/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.library;

import com.frostwire.mcp.MCPTool;
import com.frostwire.util.BoundedFileWalker;
import com.google.gson.JsonObject;
import com.limegroup.gnutella.settings.SharingSettings;
import java.io.File;

public final class LibraryScanTool implements MCPTool {

  @Override
  public String name() {
    return "frostwire_library_scan";
  }

  @Override
  public String description() {
    return "Scan a directory for files, optionally filtering by media type. Returns the count of"
        + " files found.";
  }

  @Override
  public JsonObject inputSchema() {
    JsonObject schema = new JsonObject();
    schema.addProperty("type", "object");
    JsonObject props = new JsonObject();
    JsonObject pathProp = new JsonObject();
    pathProp.addProperty("type", "string");
    pathProp.addProperty(
        "description", "Directory path to scan (defaults to torrent data directory)");
    props.add("path", pathProp);
    JsonObject mediaTypeProp = new JsonObject();
    mediaTypeProp.addProperty("type", "string");
    mediaTypeProp.addProperty(
        "description", "Filter by media type: all, audio, video, images, documents, torrents");
    mediaTypeProp.addProperty("default", "all");
    props.add("mediaType", mediaTypeProp);
    schema.add("properties", props);
    return schema;
  }

  @Override
  public JsonObject execute(JsonObject arguments) {
    File scanDir;
    if (arguments != null && arguments.has("path") && !arguments.get("path").isJsonNull()) {
      String path = arguments.get("path").getAsString();
      scanDir = new File(path);
      if (!scanDir.isAbsolute()) {
        scanDir = new File(SharingSettings.TORRENT_DATA_DIR_SETTING.getValue(), path);
      }
    } else {
      scanDir = SharingSettings.TORRENT_DATA_DIR_SETTING.getValue();
    }
    if (!scanDir.exists() || !scanDir.isDirectory()) {
      JsonObject result = new JsonObject();
      result.addProperty("error", "Directory does not exist: " + scanDir.getAbsolutePath());
      return result;
    }
    String mediaType = "all";
    if (arguments != null
        && arguments.has("mediaType")
        && !arguments.get("mediaType").isJsonNull()) {
      mediaType = arguments.get("mediaType").getAsString();
    }
    int count = scanFiles(scanDir, mediaType);
    JsonObject result = new JsonObject();
    result.addProperty("scanned", true);
    result.addProperty("fileCount", count);
    result.addProperty("path", scanDir.getAbsolutePath());
    return result;
  }

  /**
   * Counts the files of {@code mediaType} under {@code root}. Bounded by {@link BoundedFileWalker},
   * so a symlink loop can't make the scan run forever.
   */
  static int scanFiles(File root, String mediaType) {
    int[] count = {0};
    BoundedFileWalker.walk(
        root,
        (path, attrs) -> {
          if (LibraryListTool.matchesMediaType(path.toFile(), mediaType)) {
            count[0]++;
          }
        });
    return count[0];
  }
}
