/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

final class MimeDetectorTest {

  @Test
  void commonExtensionsMapToTheirMimeType() {
    assertEquals("application/x-bittorrent", MimeDetector.getMimeType("torrent"));
    assertEquals("audio/mpeg", MimeDetector.getMimeType("mp3"));
    assertEquals("video/mp4", MimeDetector.getMimeType("mp4"));
    assertEquals("image/jpeg", MimeDetector.getMimeType("jpg"));
    assertEquals("image/png", MimeDetector.getMimeType("png"));
    assertEquals("image/gif", MimeDetector.getMimeType("gif"));
    assertEquals("application/pdf", MimeDetector.getMimeType("pdf"));
    assertEquals("application/zip", MimeDetector.getMimeType("zip"));
    assertEquals("text/html", MimeDetector.getMimeType("html"));
    assertEquals("text/css", MimeDetector.getMimeType("css"));
    assertEquals("text/plain", MimeDetector.getMimeType("txt"));
    assertEquals("audio/ogg", MimeDetector.getMimeType("ogg"));
    assertEquals("audio/flac", MimeDetector.getMimeType("flac"));
    assertEquals("video/webm", MimeDetector.getMimeType("webm"));
    assertEquals("video/x-matroska", MimeDetector.getMimeType("mkv"));
    assertEquals("application/vnd.android.package-archive", MimeDetector.getMimeType("apk"));
  }

  @Test
  void unknownOrWrongCaseExtensionsFallBackToOctetStream() {
    assertEquals(MimeDetector.UNKNOWN, MimeDetector.getMimeType("definitely-not-an-extension"));
    assertEquals(MimeDetector.UNKNOWN, MimeDetector.getMimeType(""));
    assertEquals("application/octet-stream", MimeDetector.UNKNOWN);
    // lookups are case-sensitive: callers must lower-case the extension
    assertNotEquals(MimeDetector.UNKNOWN, MimeDetector.getMimeType("mp3"));
    assertEquals(MimeDetector.UNKNOWN, MimeDetector.getMimeType("MP3"));
  }
}
