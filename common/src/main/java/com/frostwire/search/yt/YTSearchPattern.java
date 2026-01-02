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

package com.frostwire.search.yt;
import com.frostwire.search.CompositeFileSearchResult;

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.FileSearchResult;
import com.frostwire.search.SearchPattern;
import com.frostwire.search.StreamingCapability;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * V2 migration of YTSearchPerformer.
 * Searches YouTube and parses JSON video data.
 *
 * @author gubatron
 */
public class YTSearchPattern implements SearchPattern {
    private static final Logger LOG = Logger.getLogger(YTSearchPattern.class);
    private static final String DOMAIN = "www.youtube.com";
    private static final Pattern JSON_PATTERN = Pattern.compile("(\"videoRenderer\":.*?\"searchVideoResultEntityKey\")");

    // Static language maps - created once, reused for all instances
    private static final Map<String, Integer> ENGLISH_UNITS = createEnglishUnits();
    private static final Map<String, Integer> SPANISH_UNITS = createSpanishUnits();
    private static final Map<String, Integer> GERMAN_UNITS = createGermanUnits();
    private static final Map<String, Integer> ITALIAN_UNITS = createItalianUnits();
    private static final Map<String, Integer> FRENCH_UNITS = createFrenchUnits();

    public YTSearchPattern() {
        // Pattern is now static final, no initialization needed
    }

    @Override
    public String getSearchUrl(String encodedKeywords) {
        return "https://" + DOMAIN + "/results?app=desktop&search_query=" + encodedKeywords;
    }

    @Override
    public List<FileSearchResult> parseResults(String responseBody) {
        List<FileSearchResult> results = new ArrayList<>();

        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        try {
            String page = normalizeScriptTags(responseBody);
            int start = page.indexOf("videoRenderer") - 2;
            int end = page.indexOf("</script>", start);

            if (start < 0 || end < 0) {
                return results;
            }

            page = page.substring(start, end);
            Matcher jsonMatcher = JSON_PATTERN.matcher(page);
            GsonBuilder gsonBuilder = new GsonBuilder();
            Gson gson = gsonBuilder.create();

            while (jsonMatcher.find() && results.size() < 100) {
                try {
                    String json = jsonMatcher.group(1);
                    json = json.replace("\"videoRenderer\":", "") + ":\"\"}";
                    Video video = gson.fromJson(json, Video.class);

                    if (video.publishedTimeText == null || video.title == null || video.title.runs.isEmpty()) {
                        continue;
                    }

                    String title = video.title.runs.get(0).text;
                    if (title.length() > 150) {
                        title = title.substring(0, 150);
                    }

                    String videoAge = video.publishedTimeText.simpleText;
                    long creationTimeInMillis = parseCreationTimeInMillis(videoAge);
                    String thumbnailUrl = !video.thumbnail.thumbnails.isEmpty() ? video.thumbnail.thumbnails.get(0).url : "";
                    String detailsUrl = "https://" + DOMAIN + "/watch?v=" + video.videoId;

                    long viewCount = 1000;
                    if (video.viewCountText != null && video.viewCountText.simpleText != null) {
                        String viewCountStr = video.viewCountText.simpleText.toLowerCase();
                        if (!viewCountStr.contains("no views")) {
                            try {
                                // Extract numeric value more efficiently - remove all non-digits
                                String numericOnly = viewCountStr.replaceAll("[^0-9]", "");
                                if (!numericOnly.isEmpty()) {
                                    viewCount += Long.parseLong(numericOnly);
                                }
                            } catch (Exception e) {
                                LOG.warn("Failed to parse view count: " + viewCountStr);
                            }
                        }
                    }

                    int viewCountInt = viewCount > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) viewCount;
                    long estimatedSize = 0;

                    if (video.lengthText != null && video.lengthText.accessibility != null &&
                            video.lengthText.accessibility.accessibilityData != null) {
                        estimatedSize = estimatedFileSize(video.lengthText.accessibility.accessibilityData.label);
                    }

                    CompositeFileSearchResult searchResult = CompositeFileSearchResult.builder()
                            .displayName(title)
                            .filename(title + ".mp4")
                            .size(estimatedSize)
                            .detailsUrl(detailsUrl)
                            .source("YT")
                            .creationTime(creationTimeInMillis)
                            .thumbnailUrl(thumbnailUrl)
                            .viewCount(viewCountInt)
                            .streaming(new StreamingCapability(detailsUrl))
                            .preliminary(true)  // YouTube results need format/quality selection
                            .build();

                    results.add(searchResult);
                } catch (Exception e) {
                    LOG.warn("Error parsing YouTube result: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOG.error("Error parsing YouTube response: " + e.getMessage(), e);
        }

        return results;
    }

    /**
     * Converts durations expressed in strings into total seconds * 30 frames * ideal frame size (1980x1080)
     * examples of durations are:
     * "11 hours, 13 minutes, 17 seconds"
     * "24 minutes, 1 second"
     * "45 seconds"
     */
    private long estimatedFileSize(String durationLabel) {
        if (durationLabel == null || durationLabel.isEmpty()) {
            return 0;
        }

        // Normalize singular/plural forms with single pass
        String normalized = durationLabel
                .replace("days", "day")
                .replace("hours", "hour")
                .replace("minutes", "minute")
                .replace("seconds", "second");

        long seconds = 0;
        String[] parts = normalized.split(",");

        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            String[] time = part.split("\\s+", 2);
            if (time.length < 2) {
                continue;
            }

            try {
                int value = Integer.parseInt(time[0]);
                String unit = time[1].toLowerCase();

                // Check all language maps for unit
                Integer unitSeconds = lookupUnitSeconds(unit);
                if (unitSeconds == null) {
                    unitSeconds = 1;  // Default to 1 second for unknown units
                }

                seconds += (long) value * unitSeconds;
            } catch (NumberFormatException e) {
                // Skip malformed parts
            }
        }

        long totalFrames = seconds * 30;
        long bitDepth = 6;
        long frameSize = (1920 * 1080 * bitDepth) / (8 * 1024);
        return totalFrames * frameSize;
    }

    /**
     * Lookup unit in any language map.
     */
    private Integer lookupUnitSeconds(String unit) {
        Integer seconds = ENGLISH_UNITS.get(unit);
        if (seconds != null) return seconds;

        seconds = SPANISH_UNITS.get(unit);
        if (seconds != null) return seconds;

        seconds = GERMAN_UNITS.get(unit);
        if (seconds != null) return seconds;

        seconds = ITALIAN_UNITS.get(unit);
        if (seconds != null) return seconds;

        return FRENCH_UNITS.get(unit);
    }

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)\\s+([a-zñ]+)");

    private long parseCreationTimeInMillis(String creationString) {
        if (creationString == null || creationString.isEmpty()) {
            return System.currentTimeMillis();
        }

        try {
            // to lower case and remove any prefix string before the time value
            creationString = creationString.toLowerCase().replaceFirst("^\\D*", "");
            LOG.info("YTSearchPattern parseCreationTimeInMillis: " + creationString);

            Matcher matcher = DURATION_PATTERN.matcher(creationString);

            if (matcher.find()) {
                int time = Integer.parseInt(matcher.group(1));
                String unit = matcher.group(2);

                // Find the language map that contains the time unit
                Integer secondsPerUnit = lookupUnitSeconds(unit);

                if (secondsPerUnit != null) {
                    return System.currentTimeMillis() - (1000L * time * secondsPerUnit);
                }
            }
        } catch (Exception e) {
            LOG.warn("Error parsing creation time: " + e.getMessage());
        }

        return System.currentTimeMillis();
    }

    private String normalizeScriptTags(String html) {
        return html.replace("&quot;", "\"");
    }

    /**
     * Static initializers for language-specific time unit maps.
     * These are created once at class load time, not per instance.
     */
    private static Map<String, Integer> createEnglishUnits() {
        Map<String, Integer> map = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        map.put("second", 1);
        map.put("minute", minute);
        map.put("hour", hour);
        map.put("day", day);
        map.put("week", week);
        map.put("month", month);
        map.put("year", year);
        return map;
    }

    private static Map<String, Integer> createSpanishUnits() {
        Map<String, Integer> map = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        map.put("segundo", 1);
        map.put("minuto", minute);
        map.put("hora", hour);
        map.put("día", day);
        map.put("semana", week);
        map.put("mes", month);
        map.put("año", year);
        return map;
    }

    private static Map<String, Integer> createGermanUnits() {
        Map<String, Integer> map = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        map.put("sekunde", 1);
        map.put("minute", minute);
        map.put("stunde", hour);
        map.put("tag", day);
        map.put("woche", week);
        map.put("monat", month);
        map.put("jahr", year);
        return map;
    }

    private static Map<String, Integer> createItalianUnits() {
        Map<String, Integer> map = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        map.put("secondo", 1);
        map.put("minuto", minute);
        map.put("ora", hour);
        map.put("giorno", day);
        map.put("settimana", week);
        map.put("mese", month);
        map.put("anno", year);
        return map;
    }

    private static Map<String, Integer> createFrenchUnits() {
        Map<String, Integer> map = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        map.put("seconde", 1);
        map.put("minute", minute);
        map.put("heure", hour);
        map.put("jour", day);
        map.put("semaine", week);
        map.put("mois", month);
        map.put("année", year);
        return map;
    }

    /**
     * GSON model classes for parsing YouTube JSON response
     */
    public static class Video {
        public String videoId;
        public Title title;
        public PublishedTimeText publishedTimeText;
        public Thumbnail thumbnail;
        public ViewCountText viewCountText;
        public LengthText lengthText;
    }

    public static class Title {
        public List<Runs> runs;
    }

    public static class Runs {
        public String text;
    }

    public static class PublishedTimeText {
        public String simpleText;
    }

    public static class Thumbnail {
        public List<ThumbnailDetails> thumbnails;
    }

    public static class ThumbnailDetails {
        public String url;
        public int width;
        public int height;
    }

    public static class ViewCountText {
        public String simpleText;
    }

    public static class LengthText {
        public AccessibilityData accessibility;
    }

    public static class AccessibilityData {
        public AccessibilityDataLabel accessibilityData;
    }

    public static class AccessibilityDataLabel {
        public String label;
    }
}
