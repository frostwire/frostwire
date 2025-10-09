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

package com.frostwire.search;

import com.frostwire.jlibtorrent.FileStorage;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.regex.Pattern;
import com.frostwire.search.torrent.TorrentCrawlableSearchResult;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.util.Logger;
import com.frostwire.util.StringUtils;

import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public final class PerformersHelper {
    private static final Logger LOG = Logger.getLogger(PerformersHelper.class);
    private static final Pattern MAGNET_HASH_PATTERN = Pattern.compile("magnet\\:\\?xt\\=urn\\:btih\\:([a-fA-F0-9]{40})");

    public static Set<String> stopwords = Set.of(
            // English
            "and", "the", "a", "on", "in", "of", "for", "to", "is", "it", "at", "by", "an", "or", "as", "be", "with",
            "this", "that", "these", "those", "from", "but", "about", "which", "some", "so", "out", "then", "than", "too",

            // Spanish
            "y", "el", "la", "los", "las", "un", "una", "unos", "unas", "en", "de", "para", "por", "con", "como", "sobre",
            "al", "lo", "es", "del", "más", "ya", "o", "sin", "sus", "le", "se", "me", "te", "tu", "mi", "esto",
            "eso", "estos", "esos", "aquel", "aquella", "aquellos", "aquellas",

            // German
            "und", "der", "die", "ein", "eine", "einer", "einem", "einen", "im", "auf", "am", "zu",
            "mit", "von", "über", "für", "ist", "war", "sein", "sie", "er", "wir", "ihr", "denn", "doch", "nicht",
            "weil", "als", "aber", "wenn", "dann", "dies", "diese", "dieser", "dieses", "das", "jenes", "solche"
    );

    private PerformersHelper() {
    }

    public static List<? extends SearchResult> searchPageHelper(RegexSearchPerformer<?> performer, String page, int regexMaxResults) {
        List<SearchResult> result = new LinkedList<>();
        if (page == null) {
            LOG.warn(performer.getClass().getSimpleName() + " returning null page. Issue fetching page or issue getting page prefix/suffix offsets. Notify developers at contact@frostwire.com");
            return result;
        }

        Pattern primaryPattern = performer.getPattern();
        Pattern altPattern = performer.getAltPattern();

        result.addAll(performSearch(performer, primaryPattern, page, regexMaxResults));

        if (result.isEmpty() && altPattern != null) {
            result.addAll(performSearch(performer, altPattern, page, regexMaxResults));
        }

        return result;
    }

    private static List<? extends SearchResult> performSearch(RegexSearchPerformer<?> performer, Pattern pattern, String page, int regexMaxResults) {
        List<SearchResult> result = new LinkedList<>();
        SearchMatcher matcher = SearchMatcher.from(pattern.matcher(page));
        int i = 0;
        boolean matcherFound;

        do {
            try {
                matcherFound = matcher.find();
            } catch (Throwable t) {
                matcherFound = false;
                LOG.error("performSearch(...): " + pattern + " has failed.\n" + t.getMessage(), t);
            }

            if (matcherFound) {
                SearchResult sr = performer.fromMatcher(matcher);
                if (sr != null) {
                    if (sr instanceof WebSearchPerformer) {
                        List<String> keywords = ((WebSearchPerformer) sr).getKeywords();
                        if (oneKeywordMatchedOrFuzzyMatchedFilter(keywords, sr)) {
                            result.add(sr);
                            i++;
                        }
                    } else {
                        result.add(sr);
                        i++;
                    }

                }
            }
        } while (matcherFound && i < regexMaxResults && !performer.isStopped());

        return result;
    }

    /**
     * This method is only public allow reuse inside the package search, consider it a private API
     */
    public static List<? extends SearchResult> crawlTorrentInfo(SearchPerformer performer, TorrentCrawlableSearchResult sr, byte[] data, boolean detectAlbums) {
        List<TorrentCrawledSearchResult> list = new LinkedList<>();
        if (data == null) {
            return list;
        }
        TorrentInfo ti;
        try {
            ti = TorrentInfo.bdecode(data);
        } catch (Throwable t) {
            LOG.error("Can't bdecode:\n" + new String(data) + "\n\n");
            throw t;
        }
        int numFiles = ti.numFiles();
        FileStorage fs = ti.files();
        for (int i = 0; !performer.isStopped() && i < numFiles; i++) {
            // TODO: Check for the hidden attribute
            if (fs.padFileAt(i)) {
                continue;
            }
            list.add(new TorrentCrawledSearchResult(sr, ti, i, fs.filePath(i), fs.fileSize(i)));
        }
        if (detectAlbums) {
            List<SearchResult> temp = new LinkedList<>();
            temp.addAll(list);
            temp.addAll(new AlbumCluster().detect(sr, list));
            return temp;
        } else {
            return list;
        }
    }

    public static List<? extends SearchResult> crawlTorrentInfo(SearchPerformer performer, TorrentCrawlableSearchResult sr, byte[] data) {
        return crawlTorrentInfo(performer, sr, data, false);
    }

    public static String parseInfoHash(String url) {
        String result = null;
        final SearchMatcher matcher = SearchMatcher.from(MAGNET_HASH_PATTERN.matcher(url));
        try {
            if (matcher.find()) {
                result = matcher.group(1).toLowerCase();
            }
        } catch (Throwable t) {
            LOG.error("Could not parse magnet out of " + url, t);
        }
        return result;
    }

    public static String reduceHtml(String html, int prefixOffset, int suffixOffset) {
        if (prefixOffset == -1 || suffixOffset == -1) {
            html = null;
        } else if ((prefixOffset > 0 || suffixOffset < html.length())) {
            if (prefixOffset > suffixOffset) {
                LOG.warn("PerformersHelper.reduceHtml() Check your logic: prefixOffset:" + prefixOffset + " > suffixOffset:" + suffixOffset);
                LOG.info(html);
                return null;
            }
            html = new String(html.substring(prefixOffset, suffixOffset).toCharArray());
        }
        return html;
    }

    public static List<String> tokenizeSearchKeywords(@Nullable String keywords) {
        if (keywords == null || keywords.trim().isEmpty()) {
            return new ArrayList<>(0);
        }
        keywords = sanitize(keywords);
        Set<String> tokens = new HashSet<>(Arrays.asList(keywords.toLowerCase(Locale.US).split(" ")));
        return new ArrayList<>(normalizeTokens(tokens));
    }

    public static String searchResultAsNormalizedString(SearchResult sr) {
        StringBuilder sb = new StringBuilder();

        sb.append(sr.getDisplayName());
        if (sr instanceof CrawledSearchResult) {
            sb.append(((CrawledSearchResult) sr).getParent().getDisplayName());
        }

        if (sr instanceof FileSearchResult) {
            sb.append(((FileSearchResult) sr).getFilename());
        }

        String str = sanitize(sb.toString());
        return normalize(str);
    }

    private static String sanitize(String str) {
        str = StringUtils.fromHtml(str);
        
        // Replace specific patterns first (more efficient than regex)
        // Note: These replacements must match the original regex behavior exactly
        str = str.replace(".torrent", " ");
        
        // Handle www. pattern (must be followed by a dot)
        str = str.replaceAll("www\\.", " ");
        
        str = str.replace(".com", " ");
        str = str.replace(".net", " ");
        
        // Replace problem characters efficiently without regex
        char[] chars = str.toCharArray();
        StringBuilder sb = new StringBuilder(chars.length);
        
        for (char c : chars) {
            switch (c) {
                case '\\':
                case '/':
                case '%':
                case '_':
                case ';':
                case '-':
                case '.':
                case '(':
                case ')':
                case '[':
                case ']':
                case '\n':
                case '\r':
                case '&':
                case '~':
                case '{':
                case '}':
                case '*':
                case '@':
                case '^':
                case '\'':
                case '=':
                case '!':
                case ',':
                case '|':
                case '#':
                case '\u00D0': // Ð
                case '\u00A1': // ¡
                case '\u00C0': // À
                case '\u00C1': // Á
                    sb.append(' ');
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        
        str = sb.toString();
        str = StringUtils.removeDoubleSpaces(str);
        return str.trim();
    }

    private static String normalize(String token) {
        String norm = Normalizer.normalize(token, Normalizer.Form.NFKD);
        norm = norm.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        norm = norm.toLowerCase(Locale.US);

        return norm;
    }

    private static Set<String> normalizeTokens(Set<String> tokens) {
        Set<String> normalizedTokens = new HashSet<>(0);

        for (String token : tokens) {
            String norm = normalize(token);
            normalizedTokens.add(norm);
        }

        return normalizedTokens;
    }

    /**
     * Similar to someSearchTokensMatchSearchResult but using fuzzy matching
     */
    public static boolean oneKeywordMatchedOrFuzzyMatchedFilter(List<String> keywords, SearchResult sr) {
        String normalizedSearchResultAsLowerCaseString = searchResultAsNormalizedString(sr).toLowerCase();

        for (String keyword : keywords) {
            String lowerKeyword = keyword.toLowerCase();
            if (normalizedSearchResultAsLowerCaseString.contains(lowerKeyword) || isFuzzyMatch(normalizedSearchResultAsLowerCaseString, lowerKeyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFuzzyMatch(String str, String keyword) {
        int distance = levenshteinDistance(str, keyword);
        int threshold = Math.max(str.length(), keyword.length()) / 2; // Adjust threshold as needed
        return distance <= threshold;
    }

    /**
     * ThreadLocal cache for working arrays to avoid repeated allocations.
     * Each thread gets its own pair of arrays that grow as needed.
     */
    private static final ThreadLocal<int[][]> LEVENSHTEIN_ARRAYS = ThreadLocal.withInitial(() -> new int[2][64]);

    /**
     * Optimized Levenshtein distance using two rolling arrays instead of a full matrix.
     * This reduces space complexity from O(n*m) to O(min(n,m)).
     * 
     * Memory optimization: Uses ThreadLocal cached arrays to avoid repeated allocations
     * for similar-length strings across multiple calls.
     * 
     * Performance: Reduces allocation from ~60-160KB per call (for typical 80-200 char strings)
     * to O(min(n,m)) integers that are reused across calls via ThreadLocal caching.
     * 
     * @param a first string
     * @param b second string
     * @return the Levenshtein distance between the two strings
     */
    public static int levenshteinDistance(String a, String b) {
        int n = a.length();
        int m = b.length();
        
        // Handle empty string cases
        if (n == 0) return m;
        if (m == 0) return n;
        
        // Swap strings so that n <= m to minimize space usage
        if (n > m) {
            String tmp = a;
            a = b;
            b = tmp;
            int tmpLen = n;
            n = m;
            m = tmpLen;
        }
        
        // Get cached arrays from ThreadLocal, growing them if needed
        int[][] arrays = LEVENSHTEIN_ARRAYS.get();
        if (arrays[0].length < n + 1) {
            // Grow arrays to next power of 2 for efficiency
            int newSize = Integer.highestOneBit(n) << 1;
            arrays[0] = new int[newSize];
            arrays[1] = new int[newSize];
        }
        
        int[] prevRow = arrays[0];
        int[] currRow = arrays[1];
        
        // Initialize first row
        for (int i = 0; i <= n; i++) {
            prevRow[i] = i;
        }
        
        // Compute distance using rolling arrays
        for (int j = 1; j <= m; j++) {
            currRow[0] = j;
            char bChar = b.charAt(j - 1);
            
            for (int i = 1; i <= n; i++) {
                int cost = (a.charAt(i - 1) == bChar) ? 0 : 1;
                currRow[i] = Math.min(
                    Math.min(currRow[i - 1] + 1,      // insertion
                             prevRow[i] + 1),          // deletion
                    prevRow[i - 1] + cost              // substitution
                );
            }
            
            // Swap rows for next iteration
            int[] temp = prevRow;
            prevRow = currRow;
            currRow = temp;
        }
        
        return prevRow[n];
    }

    private static int costOfSubstitution(char a, char b) {
        return a == b ? 0 : 1;
    }

    public static int countMatchedTokens(String normalizedResult, List<String> searchTokens) {
        int count = 0;
        if (normalizedResult != null && !normalizedResult.isEmpty()) {
            for (String token : searchTokens) {
                if (normalizedResult.contains(token)) {
                    count++;
                }
            }
        }
        return count;
    }
}
