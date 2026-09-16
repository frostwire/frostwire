/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.crawl;

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.frostwire.util.http.HttpClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal read-only client for a relay's HTTP control API.
 *
 * <p>Two endpoints are used by the catalog crawler:
 *
 * <ul>
 *   <li>{@code GET /lookup?count=100} → candidate peer public keys
 *   <li>{@code GET /catalog?pub=<base64url>&timeoutMs=<n>} → shared-torrent rows for that peer
 * </ul>
 *
 * <p>Requests carry the optional {@code X-IceBridge-Token} header. Responses are hard-capped at
 * {@link #MAX_RESPONSE_BYTES} before parsing, malformed JSON yields an empty result, and the peer
 * host/IP present in {@code /lookup} responses is intentionally ignored: only the public key is
 * ever propagated to storage.
 */
public final class RelayControlClient {

  /** Maximum response body, in bytes, accepted before parsing. */
  static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

  private static final Logger LOG = Logger.getLogger(RelayControlClient.class);
  private static final String TOKEN_HEADER = "X-IceBridge-Token";
  private static final String USER_AGENT = "FrostWire-IceBridgeCrawler/1.0";
  private static final int LOOKUP_COUNT = 100;

  private final HttpClient http;
  private final String relayUrl;
  private final String token;

  public RelayControlClient(String relayUrl, String token) {
    this(HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC), relayUrl, token);
  }

  RelayControlClient(HttpClient http, String relayUrl, String token) {
    if (relayUrl == null || relayUrl.isBlank()) {
      throw new IllegalArgumentException("relayUrl is null or blank");
    }
    if (http == null) {
      throw new IllegalArgumentException("http is null");
    }
    this.http = http;
    this.relayUrl = trimTrailingSlash(relayUrl.trim());
    this.token = token == null ? "" : token.trim();
  }

  /** Candidate peer public keys (base64url) from {@code GET /lookup?count=100}. */
  public List<String> lookupPeerPubs(int timeoutSec) {
    String url = relayUrl + "/lookup?count=" + LOOKUP_COUNT;
    return parseLookupPubs(getBounded(url, timeoutMs(timeoutSec)));
  }

  /** Shared-torrent rows for one peer public key from {@code GET /catalog}. */
  public List<CatalogEntry> fetchCatalog(String pub, int catalogTimeoutSec) {
    if (pub == null || pub.isBlank()) {
      return List.of();
    }
    String url =
        relayUrl
            + "/catalog?pub="
            + URLEncoder.encode(pub, StandardCharsets.UTF_8)
            + "&timeoutMs="
            + timeoutMs(catalogTimeoutSec);
    return parseCatalog(getBounded(url, timeoutMs(catalogTimeoutSec)));
  }

  private String getBounded(String url, int timeoutMs) {
    Map<String, String> headers = token.isBlank() ? null : Map.of(TOKEN_HEADER, token);
    try {
      String body = http.get(url, timeoutMs, USER_AGENT, null, null, headers);
      if (body == null) {
        return null;
      }
      // The shared client has its own coarse cap; enforce the tighter crawler cap before parsing.
      if (body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
        LOG.warn("Relay response exceeds " + MAX_RESPONSE_BYTES + " bytes; skipping");
        return null;
      }
      return body;
    } catch (Throwable t) {
      LOG.warn("Relay request failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
      return null;
    }
  }

  /** Parse a {@code /lookup} payload, ignoring the peer address fields. */
  static List<String> parseLookupPubs(String json) {
    List<String> pubs = new ArrayList<>();
    JsonObject root = parseRoot(json);
    if (!isOk(root)) {
      return pubs;
    }
    JsonArray data = asArray(root.get("data"));
    if (data == null) {
      return pubs;
    }
    for (JsonElement element : data) {
      if (element == null || !element.isJsonObject()) {
        continue;
      }
      String pub = getString(element.getAsJsonObject(), "pub");
      if (pub != null && !pub.isBlank()) {
        pubs.add(pub);
      }
    }
    return pubs;
  }

  /** Parse a {@code /catalog} payload; rows without an infohash are skipped. */
  static List<CatalogEntry> parseCatalog(String json) {
    List<CatalogEntry> entries = new ArrayList<>();
    JsonObject root = parseRoot(json);
    if (!isOk(root)) {
      return entries;
    }
    JsonArray data = asArray(root.get("data"));
    if (data == null) {
      return entries;
    }
    for (JsonElement element : data) {
      if (element == null || !element.isJsonObject()) {
        continue;
      }
      JsonObject row = element.getAsJsonObject();
      String infohash = getString(row, "ih");
      if (infohash == null || infohash.isBlank()) {
        continue;
      }
      String name = getString(row, "name");
      long sizeBytes = getLong(row, "s");
      int files = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, getLong(row, "fc")));
      String publisher = getString(row, "pub");
      entries.add(
          new CatalogEntry(
              infohash,
              name == null ? "" : name,
              Math.max(0L, sizeBytes),
              files,
              publisher == null ? "" : publisher));
    }
    return entries;
  }

  private static JsonObject parseRoot(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      JsonElement parsed = JsonParser.parseString(json);
      return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static boolean isOk(JsonObject root) {
    if (root == null) {
      return false;
    }
    JsonElement ok = root.get("ok");
    return ok != null
        && ok.isJsonPrimitive()
        && ok.getAsJsonPrimitive().isBoolean()
        && ok.getAsBoolean();
  }

  private static JsonArray asArray(JsonElement element) {
    return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
  }

  private static String getString(JsonObject object, String key) {
    JsonElement element = object.get(key);
    if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
      return null;
    }
    try {
      return element.getAsString();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static long getLong(JsonObject object, String key) {
    JsonElement element = object.get(key);
    if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
      return 0L;
    }
    try {
      if (element.getAsJsonPrimitive().isNumber()) {
        return element.getAsLong();
      }
      if (element.getAsJsonPrimitive().isString()) {
        return Long.parseLong(element.getAsString().trim());
      }
    } catch (RuntimeException e) {
      return 0L;
    }
    return 0L;
  }

  private static int timeoutMs(int timeoutSec) {
    long millis = Math.max(1L, (long) timeoutSec) * 1000L;
    return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
  }

  private static String trimTrailingSlash(String url) {
    int end = url.length();
    while (end > 0 && url.charAt(end - 1) == '/') {
      end--;
    }
    return url.substring(0, end);
  }

  /** One shared-torrent row as advertised by a relay. */
  public record CatalogEntry(
      String infohash, String name, long sizeBytes, int files, String publisherPeerId) {}
}
