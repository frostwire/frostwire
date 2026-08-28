/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TellurideHttpHeadersTest {

    @Test
    void mediaRequestsStartWithAFullByteRange() {
        Map<String, String> original = Map.of("User-Agent", "test");
        Map<String, String> headers = TellurideSearchPerformer.withFullRange(original);

        assertEquals("bytes=0-", headers.get("Range"));
        assertEquals("test", headers.get("User-Agent"));
        assertEquals(1, original.size(), "caller headers must not be mutated");
    }

    @Test
    void withFullRangeDropsHopByHopAndAuthorityHeaders() {
        Map<String, String> original = new HashMap<>();
        original.put("User-Agent", "yt-dlp");
        original.put("Referer", "https://www.youtube.com/");
        original.put("Cookie", "sid=1");
        original.put("Host", "googlevideo.com");
        original.put("Content-Length", "12");
        original.put("Connection", "keep-alive");
        original.put("Transfer-Encoding", "chunked");

        Map<String, String> headers = TellurideSearchPerformer.withFullRange(original);

        assertEquals("yt-dlp", headers.get("User-Agent"));
        assertEquals("https://www.youtube.com/", headers.get("Referer"));
        assertEquals("sid=1", headers.get("Cookie"));
        assertEquals("bytes=0-", headers.get("Range"));
        assertFalse(headers.containsKey("Host"));
        assertFalse(headers.containsKey("Content-Length"));
        assertFalse(headers.containsKey("Connection"));
        assertFalse(headers.containsKey("Transfer-Encoding"));
    }

    @Test
    void searchResultOwnsAnUnmodifiableHeaderCopy() {
        Map<String, String> original = new HashMap<>();
        original.put("User-Agent", "yt-dlp");
        TellurideSearchResult result =
                new TellurideSearchResult(
                        "id",
                        "title",
                        "file.mp4",
                        "Cloud:youtube",
                        "https://youtube.com/watch?v=id",
                        "https://googlevideo.com/video",
                        null,
                        1L,
                        0L,
                        original);

        original.put("Host", "evil.example");
        assertFalse(result.getHttpHeaders().containsKey("Host"));
        assertThrows(UnsupportedOperationException.class, () -> result.getHttpHeaders().put("Range", "bytes=1-"));
        assertNull(
                new TellurideSearchResult("id", "title", "Cloud:youtube", "https://youtube.com", null, 0L)
                        .getHttpHeaders());
    }
}
