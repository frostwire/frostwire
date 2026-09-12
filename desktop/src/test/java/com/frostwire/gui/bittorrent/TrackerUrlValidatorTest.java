/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.gui.bittorrent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackerUrlValidatorTest {

  @Test
  void acceptsWellFormedTrackerUrls() {
    assertTrue(TrackerUrlValidator.isValidTrackerUrl("http://tracker.example.com:6969/announce"));
    assertTrue(TrackerUrlValidator.isValidTrackerUrl("https://tracker.example.com/announce"));
    assertTrue(TrackerUrlValidator.isValidTrackerUrl("udp://tracker.example.com:1337/announce"));
    assertTrue(TrackerUrlValidator.isValidTrackerUrl("udp://tracker.example.com:1337"));
    assertTrue(TrackerUrlValidator.isValidTrackerUrl("UDP://tracker.example.com:1337"));
    assertTrue(
        TrackerUrlValidator.isValidTrackerUrl("  http://tracker.example.com:6969/announce  "));
  }

  @Test
  void rejectsMalformedTrackerUrls() {
    assertFalse(TrackerUrlValidator.isValidTrackerUrl(null));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl(""));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl("   "));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl("not a url"));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl("ftp://tracker.example.com/announce"));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl("http://tracker.example.com"));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl("http://:6969/announce"));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl("http://tracker.example.com:99999/announce"));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl("http://tracker.example.com:0/announce"));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl("http://tracker.example.com:abc/announce"));
    assertFalse(TrackerUrlValidator.isValidTrackerUrl("udp://:1337"));
  }
}
