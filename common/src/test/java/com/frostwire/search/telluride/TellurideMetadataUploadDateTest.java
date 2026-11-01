/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How the yt-dlp metadata readers date a result from {@code upload_date} (YYYYMMDD). They used a
 * shared {@code Calendar} that only the {@code TellurideSearchPerformer} constructor created, so in
 * a process that never built a performer (Android calls the static readers directly) a video or
 * playlist entry without {@code upload_date} threw a NullPointerException, and an {@code
 * upload_date} cut short or not made of digits threw StringIndexOutOfBounds or NumberFormat. A
 * dated item also moved that shared calendar, so the next undated item got the previous item's
 * date. Found by {@code TellurideSearchPerformerFuzzTest}. An undated or unreadable date now means
 * "now"; a well-formed one decodes to that day.
 */
class TellurideMetadataUploadDateTest {

  private static final Gson GSON = new GsonBuilder().create();

  private static String video(String uploadDateField) {
    return "{\"id\":\"abc\",\"title\":\"Muxed\",\"extractor\":\"generic\","
        + "\"webpage_url\":\"https://ex.invalid/m\""
        + uploadDateField
        + ",\"formats\":[{\"url\":\"https://ex.invalid/18.mp4\",\"ext\":\"mp4\","
        + "\"acodec\":\"mp4a.40.2\",\"vcodec\":\"avc1.42001E\",\"filesize\":4494476,"
        + "\"height\":360,\"width\":640}]}";
  }

  private static String playlist(String... entries) {
    return "{\"_type\":\"playlist\",\"title\":\"P\",\"extractor\":\"youtube:tab\",\"entries\":["
        + String.join(",", entries)
        + "]}";
  }

  private static String entry(String id, String uploadDateField) {
    return "{\"id\":\""
        + id
        + "\",\"title\":\"T "
        + id
        + "\","
        + "\"url\":\"https://www.youtube.com/watch?v="
        + id
        + "\""
        + uploadDateField
        + "}";
  }

  private static LocalDate day(long millis) {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
  }

  private static void assertNow(long before, long creationTime) {
    assertTrue(
        creationTime >= before && creationTime <= System.currentTimeMillis(),
        "expected the current time, got " + Instant.ofEpochMilli(creationTime));
  }

  /**
   * Calls a reader as the first thing in a fresh copy of the classes, the state Android is in: it
   * never constructs a TellurideSearchPerformer.
   */
  private static int resultCountInFreshProcess(String method, String json) throws Exception {
    List<URL> urls = new ArrayList<>();
    for (String path : System.getProperty("java.class.path").split(File.pathSeparator)) {
      urls.add(new File(path).toURI().toURL());
    }
    try (URLClassLoader fresh =
        new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader())) {
      Class<?> gsonClass = fresh.loadClass(Gson.class.getName());
      Class<?> listenerClass = fresh.loadClass(TellurideSearchPerformerListener.class.getName());
      Method reader =
          fresh
              .loadClass(TellurideSearchPerformer.class.getName())
              .getMethod(method, String.class, gsonClass, listenerClass, long.class, String.class);
      Object gson = gsonClass.getConstructor().newInstance();
      try {
        return ((List<?>) reader.invoke(null, json, gson, null, -1L, "test")).size();
      } catch (InvocationTargetException e) {
        if (e.getCause() instanceof RuntimeException) {
          throw (RuntimeException) e.getCause();
        }
        throw e;
      }
    }
  }

  @Test
  void videoWithoutUploadDateInAProcessThatNeverBuiltAPerformer() throws Exception {
    assertEquals(2, resultCountInFreshProcess("getValidResults", video("")));
  }

  @Test
  void playlistEntryWithoutUploadDateInAProcessThatNeverBuiltAPerformer() throws Exception {
    assertEquals(1, resultCountInFreshProcess("getValidPlaylistResults", playlist(entry("a", ""))));
  }

  @Test
  void uploadDateCutShortOrNotDigitsMeansNow() {
    for (String bad : new String[] {"", "2024", "202401", "2024-01-01", "abcdefgh", "2024010x"}) {
      long before = System.currentTimeMillis();
      List<TellurideSearchResult> videos =
          TellurideSearchPerformer.getValidResults(
              video(",\"upload_date\":\"" + bad + "\""), GSON, null, -1, "test");
      assertEquals(2, videos.size(), bad);
      assertNow(before, videos.get(0).getCreationTime());

      List<TellurideSearchResult> entries =
          TellurideSearchPerformer.getValidPlaylistResults(
              playlist(entry("a", ",\"upload_date\":\"" + bad + "\"")), GSON, null, -1, "test");
      assertEquals(1, entries.size(), bad);
      assertNow(before, entries.get(0).getCreationTime());
    }
  }

  @Test
  void wellFormedUploadDateDecodesToThatDay() {
    List<TellurideSearchResult> videos =
        TellurideSearchPerformer.getValidResults(
            video(",\"upload_date\":\"20240101\""), GSON, null, -1, "test");
    assertEquals(2, videos.size());
    assertEquals(LocalDate.of(2024, 1, 1), day(videos.get(0).getCreationTime()));
    assertEquals(LocalDate.of(2024, 1, 1), day(videos.get(1).getCreationTime()));

    List<TellurideSearchResult> entries =
        TellurideSearchPerformer.getValidPlaylistResults(
            playlist(
                entry("a", ",\"upload_date\":\"19991231\""),
                entry("b", ",\"upload_date\":\"20200229\"")),
            GSON,
            null,
            -1,
            "test");
    assertEquals(LocalDate.of(1999, 12, 31), day(entries.get(0).getCreationTime()));
    assertEquals(LocalDate.of(2020, 2, 29), day(entries.get(1).getCreationTime()));
  }

  @Test
  void undatedEntryAfterADatedOneIsNotGivenTheEarlierDate() {
    long before = System.currentTimeMillis();
    List<TellurideSearchResult> entries =
        TellurideSearchPerformer.getValidPlaylistResults(
            playlist(entry("a", ",\"upload_date\":\"20000101\""), entry("b", "")),
            GSON,
            null,
            -1,
            "test");
    assertEquals(2, entries.size());
    assertEquals(LocalDate.of(2000, 1, 1), day(entries.get(0).getCreationTime()));
    assertNow(before, entries.get(1).getCreationTime());
  }
}
