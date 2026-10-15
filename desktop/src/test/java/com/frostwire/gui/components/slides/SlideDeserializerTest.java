/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.gui.components.slides;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

class SlideDeserializerTest {

  // Trimmed live payload from https://update.frostwire.com/o2.php (one slide,
  // keeps the nulls and the unknown keys the server actually sends).
  private static final String REAL_SLIDE_JSON =
      "{\"slides\":[{"
          + "\"imageSrc\":\"https://static.frostwire.com/images/overlays/fw_desktop_android_ad_1.jpg\","
          + "\"clickURL\":\"http://www.frostwire.com/android/?from=fwplus201606overlay\","
          + "\"duration\":10000,"
          + "\"torrent\":null,"
          + "\"httpDownloadURL\":null,"
          + "\"language\":null,"
          + "\"os\":null,"
          + "\"title\":\"FrostWire Plus for Android\","
          + "\"author\":\"FrostWire\","
          + "\"size\":null,"
          + "\"method\":2,"
          + "\"md5\":null,"
          + "\"saveFileAs\":null,"
          + "\"executeParameters\":null,"
          + "\"includedVersions\":null,"
          + "\"audioURL\":null,"
          + "\"videoURL\":null,"
          + "\"facebook\":\"https://www.facebook.com/FrostwireOfficial\","
          + "\"twitter\":\"https://twitter.com/frostwire\","
          + "\"gplus\":null,"
          + "\"youtube\":\"http://www.youtube.com/user/FrostWireVideos\","
          + "\"instagram\":\"http://instagram.com/frostwire\","
          + "\"flags\":512,"
          + "\"url\":\"http://www.frostwire.com/android/\","
          + "\"httpUrl\":null}],\"randomStart\":false}";

  @Test
  void realPayload_parsesAllFields() {
    SlideList list = SlideDeserializer.parseSlideList(REAL_SLIDE_JSON);
    assertNotNull(list);
    assertEquals(1, list.slides.size());
    Slide s = list.slides.get(0);
    assertEquals(
        "https://static.frostwire.com/images/overlays/fw_desktop_android_ad_1.jpg", s.imageSrc);
    assertEquals("http://www.frostwire.com/android/?from=fwplus201606overlay", s.clickURL);
    assertEquals(10000L, s.duration);
    assertNull(s.torrent);
    assertNull(s.httpDownloadURL);
    assertNull(s.language);
    assertNull(s.os);
    assertEquals("FrostWire Plus for Android", s.title);
    assertEquals("FrostWire", s.author);
    assertEquals(0L, s.size);
    assertEquals(2, s.method);
    assertNull(s.md5);
    assertNull(s.saveFileAs);
    assertNull(s.includedVersions);
    assertNull(s.audioURL);
    assertNull(s.videoURL);
    assertEquals("https://www.facebook.com/FrostwireOfficial", s.facebook);
    assertEquals("https://twitter.com/frostwire", s.twitter);
    assertEquals("http://www.youtube.com/user/FrostWireVideos", s.youtube);
    assertEquals("http://instagram.com/frostwire", s.instagram);
    assertEquals(512, s.flags);
  }

  @Test
  void missingFields_defaultToNullAndZero() {
    SlideList list = SlideDeserializer.parseSlideList("{\"slides\":[{}]}");
    Slide s = list.slides.get(0);
    assertNull(s.imageSrc);
    assertNull(s.title);
    assertEquals(0L, s.duration);
    assertEquals(0L, s.size);
    assertEquals(0, s.method);
    assertEquals(0, s.flags);
  }

  @Test
  void emptySlidesArray_parsesToEmpty() {
    SlideList list = SlideDeserializer.parseSlideList("{\"slides\":[]}");
    assertNotNull(list.slides);
    assertTrue(list.slides.isEmpty());
  }

  @Test
  void nonNumericDuration_throwsSoCallerFallsBack() {
    assertThrows(
        JsonParseException.class,
        () -> SlideDeserializer.parseSlideList("{\"slides\":[{\"duration\":\"soon\"}]}"));
  }

  @Test
  void malformedJson_throwsSoCallerFallsBack() {
    assertThrows(JsonParseException.class, () -> SlideDeserializer.parseSlideList("not json"));
  }

  @Test
  void nonObjectSlide_throwsSoCallerFallsBack() {
    assertThrows(
        JsonParseException.class, () -> SlideDeserializer.parseSlideList("{\"slides\":[42]}"));
  }
}
