/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.andrew.apollo;

import com.frostwire.util.UrlUtils;

/**
 * Pure-Java URI helpers for the Apollo music player. No Android framework dependencies — safe to
 * test on pure JVM.
 */
public final class MusicUriUtils {

  private MusicUriUtils() {}

  /**
   * Resolves a raw path string into a well-formed URI string: - content://, http://, https://,
   * file:// URIs are returned as-is - bare file paths (including URL-encoded ones) get a "file://"
   * prefix after decoding percent-encoded characters via {@link UrlUtils#decode} - On any decoding
   * error the original path is prefixed with "file://"
   */
  public static String resolveUriString(String path) {
    if (path == null) {
      return null;
    }
    if (path.startsWith("content://")
        || path.startsWith("http://")
        || path.startsWith("https://")
        || path.startsWith("file://")) {
      return path;
    }
    try {
      return "file://" + escapeForUri(UrlUtils.decode(path));
    } catch (Throwable t) {
      return "file://" + escapeForUri(path);
    }
  }

  /**
   * Escapes what would end a file URI's path early or be read as an escape: '#' and '?' (a song
   * named "Track #1.mp3" opened "Track " and failed) and '%'. Uri.getPath() decodes them back.
   */
  static String escapeForUri(String filePath) {
    StringBuilder escaped = null;
    for (int i = 0; i < filePath.length(); i++) {
      final char c = filePath.charAt(i);
      final String replacement = c == '%' ? "%25" : c == '#' ? "%23" : c == '?' ? "%3F" : null;
      if (replacement != null && escaped == null) {
        escaped = new StringBuilder(filePath.length() + 8).append(filePath, 0, i);
      }
      if (escaped != null) {
        if (replacement != null) {
          escaped.append(replacement);
        } else {
          escaped.append(c);
        }
      }
    }
    return escaped == null ? filePath : escaped.toString();
  }
}
