/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mcp.desktop.tools.relay2;

import com.frostwire.search.relay.LocalSharedTorrent;
import com.frostwire.util.Hex;
import com.google.gson.JsonObject;
import java.util.Base64;

/**
 * Shared argument-parsing and JSON helpers for the IceBridge peer-directory / local-index MCP
 * tools.
 */
final class RelayToolSupport {

  private RelayToolSupport() {}

  static int clampInt(JsonObject args, String key, int defaultValue, int min, int max) {
    int value = defaultValue;
    if (args != null && args.has(key) && !args.get(key).isJsonNull()) {
      try {
        value = args.get(key).getAsInt();
      } catch (RuntimeException ignored) {
        value = defaultValue;
      }
    }
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
  }

  static boolean boolArg(JsonObject args, String key, boolean defaultValue) {
    if (args != null && args.has(key) && !args.get(key).isJsonNull()) {
      try {
        return args.get(key).getAsBoolean();
      } catch (RuntimeException ignored) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  static String stringArg(JsonObject args, String key) {
    if (args == null || !args.has(key) || args.get(key).isJsonNull()) {
      return null;
    }
    try {
      return args.get(key).getAsString();
    } catch (RuntimeException e) {
      return null;
    }
  }

  /** Decodes a 64-char hex string into a 32-byte pubkey, or throws IllegalArgumentException. */
  static byte[] parsePub32(String hex) {
    if (hex == null || hex.length() != 64) {
      throw new IllegalArgumentException("pub must be a 64-character hex string");
    }
    byte[] bytes = Hex.decode(hex);
    if (bytes.length != 32) {
      throw new IllegalArgumentException("pub must decode to 32 bytes");
    }
    return bytes;
  }

  /**
   * Decodes a peer public key supplied either as a 64-character hex string or as a base64url
   * encoding (padded or unpadded) of the raw 32 bytes. Throws IllegalArgumentException when the
   * input is missing or does not decode to exactly 32 bytes.
   */
  static byte[] parsePub(String raw) {
    if (raw == null || raw.isEmpty()) {
      throw new IllegalArgumentException("Missing required parameter: peer");
    }
    byte[] bytes = null;
    if (raw.length() == 64) {
      try {
        bytes = Hex.decode(raw);
      } catch (IllegalArgumentException ignored) {
        bytes = null;
      }
    }
    if (bytes == null) {
      try {
        bytes = Base64.getUrlDecoder().decode(raw);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("peer must be 64-char hex or base64url of 32 bytes");
      }
    }
    if (bytes.length != 32) {
      throw new IllegalArgumentException("peer must decode to 32 bytes");
    }
    return bytes;
  }

  static JsonObject torrentToJson(LocalSharedTorrent t) {
    JsonObject tj = new JsonObject();
    tj.addProperty("ih", t.infoHashHex());
    tj.addProperty("name", t.name());
    tj.addProperty("size", t.sizeBytes());
    tj.addProperty("files", t.fileCount());
    tj.addProperty("publisher_pub", Hex.encode(t.publisherEd25519Pub()));
    tj.addProperty("publisher_node", Hex.encode(t.publisherNodeId()));
    tj.addProperty("matched_file", t.matchedFile());
    return tj;
  }
}
