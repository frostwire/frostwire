/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.search.apibay;

import com.frostwire.search.CompositeFileSearchResult;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.util.Logger;
import com.frostwire.util.UrlUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Search of The Pirate Bay's JSON API, {@code https://apibay.org/q.php?q=<query>&cat=0}. The API
 * answers with an array of objects whose numbers are strings: {@code id}, {@code name}, {@code
 * info_hash}, {@code seeders}, {@code leechers}, {@code size}, {@code num_files}, {@code added} and
 * {@code category}. A search without matches answers with one row named "No results returned" whose
 * info hash is all zeros.
 *
 * <p>Until 2026-11 this class was {@code MagnetDLSearchPattern}, which queried an apibay proxy that
 * disappeared (frostwire/frostwire#1533).
 *
 * @author gubatron
 */
public class ApiBaySearchPattern implements SearchPattern {
  private static final Logger LOG = Logger.getLogger(ApiBaySearchPattern.class);
  private static final String API_URL = "https://apibay.org/q.php?q=";
  private static final String DETAILS_URL = "https://thepiratebay.org/description.php?id=";
  private static final int MAX_NAME_LENGTH = 150;
  private static final int MAX_NUMBER_LENGTH = 40;
  private static final int INFO_HASH_LENGTH = 40;
  private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

  @Override
  public String getSearchUrl(String encodedKeywords) {
    return API_URL + encodedKeywords + "&cat=0";
  }

  @Override
  public List<FileSearchResult> parseResults(String responseBody) {
    List<FileSearchResult> results = new ArrayList<>();
    if (responseBody == null || responseBody.isBlank()) {
      return results;
    }

    JsonElement root;
    try {
      root = JsonParser.parseString(responseBody.trim());
    } catch (JsonParseException e) {
      LOG.warn("ApiBay: response is not JSON: " + e.getMessage());
      return results;
    }
    if (!root.isJsonArray()) {
      LOG.warn("ApiBay: expected a JSON array of results");
      return results;
    }

    JsonArray rows = root.getAsJsonArray();
    for (JsonElement row : rows) {
      FileSearchResult result = row.isJsonObject() ? toResult(row.getAsJsonObject()) : null;
      if (result != null) {
        results.add(result);
      }
    }
    LOG.debug("ApiBay: " + results.size() + " results out of " + rows.size() + " rows");
    return results;
  }

  /** Returns the result of one row, or null when the row holds no usable torrent. */
  private static FileSearchResult toResult(JsonObject row) {
    String name = string(row, "name");
    String infoHash = string(row, "info_hash");
    if (name == null || name.isBlank() || !isInfoHash(infoHash) || isAllZeros(infoHash)) {
      return null;
    }

    String displayName =
        name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    long id = number(row, "id");
    int seeders = (int) Math.min(Integer.MAX_VALUE, number(row, "seeders"));
    long size = number(row, "size");
    long added = number(row, "added");

    String magnetLink =
        UrlUtils.buildMagnetUrl(
            infoHash,
            name,
            "&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce"
                + "&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337"
                + "&tr=udp%3A%2F%2Fmovies.zsw.ca%3A6969%2Fannounce&tr=udp%3A%2F%2Ftracker.dler.org%3A6969%2Fannounce"
                + "&tr=udp%3A%2F%2Fopentracker.i2p.rocks%3A6969%2Fannounce&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce"
                + "&tr=udp%3A%2F%2Ftracker.0x.tf%3A6969%2Fannounce");

    return CompositeFileSearchResult.builder()
        .displayName(displayName)
        .filename(displayName + ".torrent")
        .size(size)
        .detailsUrl(DETAILS_URL + id)
        .source("apibay")
        .creationTime(added)
        .torrent(magnetLink, infoHash, seeders, magnetLink)
        .preliminary(false)
        .build();
  }

  private static boolean isInfoHash(String s) {
    if (s == null || s.length() != INFO_HASH_LENGTH) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      if (Character.digit(s.charAt(i), 16) < 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean isAllZeros(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) != '0') {
        return false;
      }
    }
    return true;
  }

  /** Returns the field as text when it is a JSON string or number, else null. */
  private static String string(JsonObject row, String field) {
    JsonElement e = row.get(field);
    if (e == null || !e.isJsonPrimitive()) {
      return null;
    }
    return e.getAsString();
  }

  /**
   * Returns the field as a non-negative long: 0 when absent, not a plain integer or negative, and
   * Long.MAX_VALUE when it is larger than a long.
   */
  private static long number(JsonObject row, String field) {
    String text = string(row, field);
    if (text == null) {
      return 0;
    }
    text = text.trim();
    if (text.isEmpty() || text.length() > MAX_NUMBER_LENGTH) {
      return 0;
    }
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) < '0' || text.charAt(i) > '9') {
        return 0;
      }
    }
    return new BigInteger(text).min(LONG_MAX).longValue();
  }
}
