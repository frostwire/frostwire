/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

import com.frostwire.regex.Matcher;
import com.frostwire.regex.Pattern;
import com.frostwire.search.PagedWebSearchPerformer;
import com.frostwire.search.SearchResult;
import com.frostwire.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * YT Search performer
 */
public class YTSearchPerformer extends PagedWebSearchPerformer {

    private static Pattern jsonPattern;

    // Maps for different languages
    private final Map<String, Integer> englishUnitsToSeconds = getEnglishUnitsToSeconds();
    private final Map<String, Integer> spanishUnitsToSeconds = getSpanishUnitsToSeconds();
    private final Map<String, Integer> germanUnitsToSeconds = getGermanUnitsToSeconds();
    private final Map<String, Integer> italianUnitsToSeconds = getItalianUnitsToSeconds();
    private final Map<String, Integer> frenchUnitsToSeconds = getFrenchUnitsToSeconds();

    private final static Logger LOG = Logger.getLogger(YTSearchPerformer.class);

    private String htmlOutput;


    public YTSearchPerformer(long token, String keywords, int timeout, int pages) {
        super("www.youtube.com", token, keywords, timeout, pages);
        if (jsonPattern == null) {
            jsonPattern = Pattern.compile("(\"videoRenderer\":.*?\"searchVideoResultEntityKey\")");
        }
    }

    @Override
    protected String getSearchUrl(int page, String encodedKeywords) {
        return "https://" + getDomainName() + "/results?app=desktop&search_query=" + encodedKeywords;
    }

    @Override
    protected List<? extends SearchResult> searchPage(String page) {
        page = normalizeScriptTags(page);
        int start = page.indexOf("videoRenderer") - 2;
        int end = page.indexOf("</script>", start);

        page = page.substring(start, end);
        htmlOutput = page;
        Matcher jsonMatcher = jsonPattern.matcher(page);
        List<YTSearchResult> results = new ArrayList<>();
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();
        while (jsonMatcher.find()) {
            try {
                String json = jsonMatcher.group(1);
                json = json.replace("\"videoRenderer\":", "") + ":\"\"}";
                Video video = gson.fromJson(json, Video.class);
                if (video.publishedTimeText == null) {
                    continue;
                }
                String title = video.title.runs.get(0).text.length() > 150 ? video.title.runs.get(0).text.substring(0, 150) : video.title.runs.get(0).text;
                String videoAge = video.publishedTimeText.simpleText;
                long creationTimeInMillis = parseCreationTimeInMillis(videoAge);
                String thumbnailUrl = video.thumbnail.thumbnails.get(0).url;
                String detailsUrl = "https://" + getDomainName() + "/watch?v=" + video.videoId;
                long viewCount = 1000 + ((video.viewCountText.simpleText.toLowerCase().contains("no views")) ? 0 : Long.parseLong(video.viewCountText.simpleText.replace(",", "").replace(".", "").replace(" ", "").replaceAll("[a-zA-Z]+", "")));
                int viewCountInt = viewCount > (long) Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) viewCount;
                YTSearchResult searchResult = new YTSearchResult(title, detailsUrl, creationTimeInMillis, thumbnailUrl, viewCountInt, estimatedFileSize(video.lengthText.accessibility.accessibilityData.label));
                //LOG.info("YTSearchPerformer() searchPage() searchResult: " + searchResult);
                results.add(searchResult);
            } catch (Throwable e) {
                LOG.error("YTSearchPerformer() searchPage() error: " + e.getMessage(), e);
                LOG.error("YTSearchPerformer() searchPage() json: " + jsonMatcher.group(1));
            }
        }
        return results;
    }

    /**
     * Converts durations expressed in strings into total seconds * 30 frames * ideal frame size (1980x1080
     * examples of durations are:
     * "11 hours, 13 minutes, 17 seconds"
     * "24 minutes, 1 second"
     * "45 seconds"
     */
    private long estimatedFileSize(String durationLabel) {
        String[] parts = durationLabel.
                replace("days", "day").
                replace("hours", "hour").
                replace("minutes", "minute").
                replace("seconds", "second").
                split(",");
        long seconds = 0;
        for (String part : parts) {
            part = part.trim();
            String[] time = part.split(" ");
            int value = Integer.parseInt(time[0]);
            String unit = time[1];

            Integer mappedSeconds = englishUnitsToSeconds.getOrDefault(unit, null);
            if (mappedSeconds == null) {
                mappedSeconds = spanishUnitsToSeconds.getOrDefault(unit, null);
            }
            if (mappedSeconds == null) {
                mappedSeconds = germanUnitsToSeconds.getOrDefault(unit, null);
            }
            if (mappedSeconds == null) {
                mappedSeconds = italianUnitsToSeconds.getOrDefault(unit, null);
            }
            if (mappedSeconds == null) {
                mappedSeconds = frenchUnitsToSeconds.getOrDefault(unit, null);
            }
            if (mappedSeconds == null) {
                mappedSeconds = 1;
            }

            seconds += (long) value * mappedSeconds;
        }
        long totalFrames = seconds * 30;
        long bitDepth = 6;
        long frameSize = (1920 * 1080 * bitDepth) / (8 * 1024);
        return totalFrames * frameSize;
    }

    /**
     * private long parseCreationTimeInMillis(String creationString) {
     * LOG.info("YTSearchPerformer() parseCreationTimeInMillis() creationString: " + creationString);
     * // hace 50 minutos
     * // Emitido hace 13 horas
     * creationString = creationString.toLowerCase().replace("streamed ", "").replaceAll("s", "").replace("ago", "");
     * String[] parts = creationString.split(" ");
     * int time = Integer.parseInt(parts[0]);
     * String unit = parts[1];
     * return System.currentTimeMillis() - (1000L * time * unitsToSeconds.get(unit));
     * }
     */

    private long parseCreationTimeInMillis(String creationString) {
        // to lower case and remove any prefix string before the time value
        creationString = creationString.toLowerCase().replaceFirst("^\\D*", "");
        LOG.info("YTSearchPerformer() parseCreationTimeInMillis() creationString: " + creationString);

        // Regular expression to match the time value and unit
        Pattern pattern = Pattern.compile("(\\d+)\\s+([a-zñ]+)");
        Matcher matcher = pattern.matcher(creationString);

        if (matcher.find()) {
            int time = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            // Find the language map that contains the time unit
            Integer secondsPerUnit = englishUnitsToSeconds.getOrDefault(unit, null);
            if (secondsPerUnit == null)
                secondsPerUnit = spanishUnitsToSeconds.getOrDefault(unit, null);
            if (secondsPerUnit == null)
                secondsPerUnit = germanUnitsToSeconds.getOrDefault(unit, null);
            if (secondsPerUnit == null)
                secondsPerUnit = italianUnitsToSeconds.getOrDefault(unit, null);
            if (secondsPerUnit == null)
                secondsPerUnit = frenchUnitsToSeconds.getOrDefault(unit, null);

            if (secondsPerUnit != null) {
                return System.currentTimeMillis() - (1000L * time * secondsPerUnit);
            }
        }

        // Unable to parse the input string
        return System.currentTimeMillis();
    }

    private Map<String, Integer> getEnglishUnitsToSeconds() {
        Map<String, Integer> unitsToSeconds = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        unitsToSeconds.put("second", 1);
        unitsToSeconds.put("seconds", 1);
        unitsToSeconds.put("minute", minute);
        unitsToSeconds.put("minutes", minute);
        unitsToSeconds.put("hour", hour);
        unitsToSeconds.put("hours", hour);
        unitsToSeconds.put("day", day);
        unitsToSeconds.put("days", day);
        unitsToSeconds.put("week", week);
        unitsToSeconds.put("weeks", week);
        unitsToSeconds.put("month", month);
        unitsToSeconds.put("months", month);
        unitsToSeconds.put("year", year);
        unitsToSeconds.put("years", year);
        return unitsToSeconds;
    }

    private Map<String, Integer> getSpanishUnitsToSeconds() {
        Map<String, Integer> unitsToSeconds = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        unitsToSeconds.put("segundo", 1);
        unitsToSeconds.put("segundos", 1);
        unitsToSeconds.put("minuto", minute);
        unitsToSeconds.put("minutos", minute);
        unitsToSeconds.put("hora", hour);
        unitsToSeconds.put("horas", hour);
        unitsToSeconds.put("día", day);
        unitsToSeconds.put("días", day);
        unitsToSeconds.put("semana", week);
        unitsToSeconds.put("semanas", week);
        unitsToSeconds.put("mes", month);
        unitsToSeconds.put("meses", month);
        unitsToSeconds.put("año", year);
        unitsToSeconds.put("años", year);
        return unitsToSeconds;
    }

    private Map<String, Integer> getGermanUnitsToSeconds() {
        Map<String, Integer> unitsToSeconds = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        unitsToSeconds.put("sekunde", 1);
        unitsToSeconds.put("sekunden", 1);
        unitsToSeconds.put("minute", minute);
        unitsToSeconds.put("minuten", minute);
        unitsToSeconds.put("stunde", hour);
        unitsToSeconds.put("stunden", hour);
        unitsToSeconds.put("tag", day);
        unitsToSeconds.put("tage", day);
        unitsToSeconds.put("woche", week);
        unitsToSeconds.put("wochen", week);
        unitsToSeconds.put("monat", month);
        unitsToSeconds.put("monate", month);
        unitsToSeconds.put("jahr", year);
        unitsToSeconds.put("jahre", year);
        return unitsToSeconds;
    }

    private Map<String, Integer> getItalianUnitsToSeconds() {
        Map<String, Integer> unitsToSeconds = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        unitsToSeconds.put("secondo", 1);
        unitsToSeconds.put("secondi", 1);
        unitsToSeconds.put("minuto", minute);
        unitsToSeconds.put("minuti", minute);
        unitsToSeconds.put("ora", hour);
        unitsToSeconds.put("ore", hour);
        unitsToSeconds.put("giorno", day);
        unitsToSeconds.put("giorni", day);
        unitsToSeconds.put("settimana", week);
        unitsToSeconds.put("settimane", week);
        unitsToSeconds.put("mese", month);
        unitsToSeconds.put("mesi", month);
        unitsToSeconds.put("anno", year);
        unitsToSeconds.put("anni", year);
        return unitsToSeconds;
    }

    private Map<String, Integer> getFrenchUnitsToSeconds() {
        Map<String, Integer> unitsToSeconds = new HashMap<>();
        int minute = 60;
        int hour = 60 * minute;
        int day = 24 * hour;
        int week = 7 * day;
        int month = 30 * day;
        int year = 365 * day;
        unitsToSeconds.put("seconde", 1);
        unitsToSeconds.put("secondes", 1);
        unitsToSeconds.put("minute", minute);
        unitsToSeconds.put("minutes", minute);
        unitsToSeconds.put("heure", hour);
        unitsToSeconds.put("heures", hour);
        unitsToSeconds.put("jour", day);
        unitsToSeconds.put("jours", day);
        unitsToSeconds.put("semaine", week);
        unitsToSeconds.put("semaines", week);
        unitsToSeconds.put("mois", month);
        unitsToSeconds.put("année", year);
        unitsToSeconds.put("années", year);
        return unitsToSeconds;
    }

    @Override
    public boolean isCrawler() {
        return false;
    }

    public String getHtmlOutput() {
        return htmlOutput;
    }

    private static boolean isHexEncoded(String input) {
        // Check for the pattern \x followed by two hex digits
        Pattern hexPattern = Pattern.compile("\\\\x[0-9A-Fa-f]{2}");
        Matcher matcher = hexPattern.matcher(input);
        return matcher.find();
    }

    private static String decodeHexString(String hex) {
        StringBuilder output = new StringBuilder();

        for (int i = 0; i < hex.length(); i++) {
            if (hex.charAt(i) == '\\' && hex.charAt(i + 1) == 'x') {
                String str = hex.substring(i + 2, i + 4);
                output.append((char) Integer.parseInt(str, 16));
                i += 3; // Move past the hex sequence
            } else {
                output.append(hex.charAt(i));
            }
        }

        return output.toString();
    }

    public static String normalizeString(String input) {
        if (isHexEncoded(input)) {
            return decodeHexString(input);
        } else {
            return input;
        }
    }

    public static String normalizeScriptTags(String html) {
        java.util.regex.Pattern scriptPattern = java.util.regex.Pattern.compile("<script[^>]*>(.*?)</script>", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = scriptPattern.matcher(html);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String scriptContent = matcher.group(1);
            String normalizedScriptContent = normalizeString(scriptContent);
            matcher.appendReplacement(sb, "<script>" + java.util.regex.Matcher.quoteReplacement(normalizedScriptContent) + "</script>");
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public static class Video {
        public String videoId;
        public Thumbnail thumbnail;
        public Title title;
        public PublishedTimeText publishedTimeText;
        public LengthText lengthText;
        public ViewCountText viewCountText;
    }

    public static class Thumbnail {
        public List<ThumbnailDetails> thumbnails;
    }

    public static class ThumbnailDetails {
        public String url;
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

    public static class LengthText {
        public Accessibility accessibility;
    }

    public static class Accessibility {
        public AccessibilityData accessibilityData;
    }

    public static class AccessibilityData {
        public String label;
    }

    public static class ViewCountText {
        public String simpleText;
    }
}
