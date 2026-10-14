/*
 *     Copyright (c) 2026, FrostWire(R). All rights reserved.
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.transfers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.frostwire.util.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HlsAudioDownloaderTest {
    private static final String BASE = "https://cf-hls-media.sndcdn.com/audio/";
    private static final String PLAYLIST_URL = BASE + "playlist.m3u8?token=fixture";
    private static final String PLAYLIST = "#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:10\n"
            + "#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-MAP:URI=\"init.mp4\"\n"
            + "#EXTINF:10.007800,\nseg1.m4s\n#EXTINF:10.007800,\nseg2.m4s\n#EXT-X-ENDLIST\n";

    @TempDir
    Path directory;

    @Test
    void assemblesFragmentBoxesInOrderAndCompletesOnlyOnceAfterClosingOutput() {
        FakeClient fake = fixture(PLAYLIST);
        File destination = directory.resolve("audio.m4a").toFile();
        fake.events.destination = destination;
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        byte[] expected = concat(fake.responses.get(BASE + "init.mp4"), fake.responses.get(BASE + "seg1.m4s"), fake.responses.get(BASE + "seg2.m4s"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> assertArrayEquals(expected, Files.readAllBytes(destination.toPath())));
        assertArrayEquals(expected, fake.events.data.toByteArray());
        assertEquals(Arrays.asList(PLAYLIST_URL, BASE + "init.mp4", BASE + "seg1.m4s", BASE + "seg2.m4s"), fake.requests);
        assertEquals(1, fake.events.complete);
        assertEquals(1, fake.events.headers);
        assertEquals(Long.toString(expected.length), fake.events.contentLength);
        assertEquals(expected.length, fake.events.lengthAtCompletion);
        assertEquals(0, fake.events.cancel);
        assertEquals(0, fake.events.errors);
        assertSame(fake.events, fake.listener);
        assertOnlyDestination(destination);
    }

    @Test
    void resolvesParentRootAndQueryUrisWithoutLeakingPlaylistQuery() {
        String text = PLAYLIST.replace("init.mp4", "../init.mp4?map=1")
                .replace("seg1.m4s", "/seg1.m4s?segment=1")
                .replace("seg2.m4s", "https://sndcdn.com/seg2.m4s");
        FakeClient fake = fixture(text);
        fake.responses.put("https://cf-hls-media.sndcdn.com/init.mp4?map=1", fake.responses.get(BASE + "init.mp4"));
        fake.responses.put("https://cf-hls-media.sndcdn.com/seg1.m4s?segment=1", fake.responses.get(BASE + "seg1.m4s"));
        fake.responses.put("https://sndcdn.com/seg2.m4s", fake.responses.get(BASE + "seg2.m4s"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, directory.resolve("audio.m4a").toFile()));
        assertEquals(1, fake.events.complete);
        assertEquals("https://cf-hls-media.sndcdn.com/init.mp4?map=1", fake.requests.get(1));
    }

    @Test
    void resolvesQueryOnlyReferencesAgainstPlaylistPath() {
        FakeClient fake = fixture(PLAYLIST.replace("seg2.m4s", "?segment=2"));
        String url = BASE + "playlist.m3u8?segment=2";
        fake.responses.put(url, fake.responses.get(BASE + "seg2.m4s"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, directory.resolve("audio.m4a").toFile()));
        assertEquals(url, fake.requests.get(3));
    }

    @Test
    void outputOpenFailureRestoresListenerAndPreservesExistingDirectory() {
        FakeClient fake = fixture(PLAYLIST);
        File destination = directory.resolve("existing").toFile();
        assertTrue(destination.mkdir());
        assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertTrue(destination.isDirectory());
        assertSame(fake.events, fake.listener);
        assertEquals(0, fake.events.complete);
        assertOnlyDestination(destination);
    }

    @Test
    void rejectsUnsupportedAndMalformedPlaylistsBeforeCreatingAudio() {
        List<String> invalid = Arrays.asList("not a playlist", PLAYLIST.replace("#EXT-X-ENDLIST", ""),
                PLAYLIST.replace("#EXT-X-ENDLIST", "#EXT-X-STREAM-INF:BANDWIDTH=100\nmaster.m3u8\n#EXT-X-ENDLIST"),
                PLAYLIST.replace("#EXT-X-VERSION:7", "#EXT-X-KEY:METHOD=AES-128,URI=\"key\""),
                PLAYLIST.replace("#EXT-X-VERSION:7", "#EXT-X-KEY:METHOD=SAMPLE-AES"),
                PLAYLIST.replace("#EXT-X-VERSION:7", "#EXT-X-BYTERANGE:12@0"),
                PLAYLIST.replace("#EXT-X-VERSION:7", "#EXT-X-DISCONTINUITY"),
                PLAYLIST.replace("#EXT-X-VERSION:7", "#EXT-X-GAP"),
                PLAYLIST.replace("#EXT-X-PLAYLIST-TYPE:VOD", "#EXT-X-PLAYLIST-TYPE:EVENT"),
                PLAYLIST.replace("URI=\"init.mp4\"", "URI=\"init.mp4\",BYTERANGE=\"12@0\""),
                PLAYLIST.replace("#EXT-X-ENDLIST", "#EXT-X-MAP:URI=\"other.mp4\"\n#EXT-X-ENDLIST"),
                PLAYLIST.replace("#EXTINF:10.007800,", "#EXTINF:NaN,"),
                PLAYLIST.replace("#EXTINF:10.007800,", ""),
                PLAYLIST.replace("#EXT-X-ENDLIST", "#EXTINF:1,\n#EXT-X-ENDLIST"));
        for (String text : invalid) {
            FakeClient fake = fixture(text);
            File destination = directory.resolve("audio.m4a").toFile();
            assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
            assertFailure(fake, destination);
            assertEquals(1, fake.requests.size());
        }
    }

    @Test
    void acceptsTheCurrentSoundcloudPlaybackCdn() {
        String base = "https://playback.media-streaming.soundcloud.cloud/audio/";
        String url = base + "playlist.m3u8";
        FakeClient fake = fixture(PLAYLIST);
        fake.responses.put(url, PLAYLIST.getBytes(StandardCharsets.UTF_8));
        fake.responses.put(base + "init.mp4", fake.responses.get(BASE + "init.mp4"));
        fake.responses.put(base + "seg1.m4s", fake.responses.get(BASE + "seg1.m4s"));
        fake.responses.put(base + "seg2.m4s", fake.responses.get(BASE + "seg2.m4s"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> HlsAudioDownloader.save(fake.client, url, directory.resolve("audio.m4a").toFile()));
        assertEquals(1, fake.events.complete);
    }

    @Test
    void rejectsUnsafePlaylistAndPartUris() {
        List<String> invalid = Arrays.asList("http://cf-hls-media.sndcdn.com/a", "https://sndcdn.com.evil.test/a",
                "https://evilsndcdn.com/a", "https://user@cf-hls-media.sndcdn.com/a", "https://cf-hls-media.sndcdn.com/a#fragment",
                "https://cf-hls-media.sndcdn.com:444/a", "//evil.test/a", "https://127.0.0.1/a", "bad uri",
                "https://playback.media-streaming.soundcloud.cloud.evil.test/a",
                "https://untrusted.soundcloud.cloud/a");
        for (String uri : invalid) {
            FakeClient fake = fixture(PLAYLIST);
            File destination = directory.resolve("audio.m4a").toFile();
            assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, uri, destination));
            assertFailure(fake, destination);
            FakeClient partFake = fixture(PLAYLIST.replace("seg2.m4s", uri));
            assertThrows(IOException.class, () -> HlsAudioDownloader.save(partFake.client, PLAYLIST_URL, destination));
            assertFailure(partFake, destination);
        }
    }

    @Test
    void cancelsDuringMediaAndDeletesAllPartialFiles() {
        FakeClient fake = fixture(PLAYLIST);
        fake.events.cancelAfterData = true;
        File destination = directory.resolve("audio.m4a").toFile();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertFalse(destination.exists());
        assertEquals(1, fake.events.cancel);
        assertEquals(0, fake.events.complete);
        assertEquals(0, fake.events.headers);
        assertEquals(0, fake.events.errors);
        assertSame(fake.events, fake.listener);
        assertDirectoryEmpty();
    }

    @Test
    void cancellationFromFinalHeadersDoesNotCompleteOrRetainOutput() {
        FakeClient fake = fixture(PLAYLIST);
        fake.events.cancelOnHeaders = true;
        File destination = directory.resolve("audio.m4a").toFile();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertFalse(destination.exists());
        assertEquals(1, fake.events.headers);
        assertEquals(1, fake.events.cancel);
        assertEquals(0, fake.events.complete);
        assertSame(fake.events, fake.listener);
        assertDirectoryEmpty();
    }

    @Test
    void honorsAlreadyCanceledClientWithoutFetching() {
        FakeClient fake = fixture(PLAYLIST);
        fake.canceled = true;
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, directory.resolve("audio.m4a").toFile()));
        assertTrue(fake.requests.isEmpty());
        assertEquals(1, fake.events.cancel);
        assertEquals(0, fake.events.complete);
        assertSame(fake.events, fake.listener);
        assertDirectoryEmpty();
    }

    @Test
    void playlistCancellationIsReportedWithoutAudioProgress() {
        FakeClient fake = fixture(PLAYLIST);
        fake.cancelUrl = PLAYLIST_URL;
        File destination = directory.resolve("audio.m4a").toFile();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertFalse(destination.exists());
        assertEquals(0, fake.events.data.size());
        assertEquals(1, fake.events.cancel);
        assertEquals(0, fake.events.complete);
        assertSame(fake.events, fake.listener);
        assertDirectoryEmpty();
    }

    @Test
    void failedSegmentDoesNotCompleteOrDoubleNotifyErrors() {
        FakeClient fake = fixture(PLAYLIST);
        fake.failUrl = BASE + "seg2.m4s";
        File destination = directory.resolve("audio.m4a").toFile();
        assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertFailure(fake, destination);
        assertEquals(4, fake.requests.size());
    }

    @Test
    void neverRetainsPlaylistResponsesAsAudio() {
        for (String url : Arrays.asList(BASE + "init.mp4", BASE + "seg1.m4s")) {
            FakeClient fake = fixture(PLAYLIST);
            fake.responses.put(url, PLAYLIST.getBytes(StandardCharsets.UTF_8));
            File destination = directory.resolve("audio.m4a").toFile();
            assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
            assertFailure(fake, destination);
        }
    }

    @Test
    void rejectsTruncatedBoxesAndFragmentsWithoutAudioPayload() {
        FakeClient valid = fixture(PLAYLIST);
        byte[] oversized = Arrays.copyOf(valid.responses.get(BASE + "seg1.m4s"), 8);
        ByteBuffer.wrap(oversized).putInt(10000);
        for (byte[] media : Arrays.asList(box("moof", new byte[0]),
                concat(movieFragment(), box("mdat", new byte[0])), movieFragment(), oversized,
                concat(movieFragment(), box("mdat", new byte[]{1}), new byte[]{0}))) {
            FakeClient fake = fixture(PLAYLIST);
            fake.responses.put(BASE + "seg1.m4s", media);
            File destination = directory.resolve("audio.m4a").toFile();
            assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
            assertFailure(fake, destination);
        }
        FakeClient fake = fixture(PLAYLIST);
        fake.responses.put(BASE + "init.mp4", concat(box("ftyp", new byte[8]), box("moov", new byte[0])));
        File destination = directory.resolve("audio.m4a").toFile();
        assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertFailure(fake, destination);
    }

    @Test
    void rejectsAdvertisedBoundsAndUnexpectedHttpRanges() {
        for (String url : Arrays.asList(PLAYLIST_URL, BASE + "init.mp4", BASE + "seg1.m4s")) {
            FakeClient fake = fixture(PLAYLIST);
            fake.responseHeaders.put(url, Collections.singletonMap("content-length", Collections.singletonList("67108865")));
            File destination = directory.resolve("audio.m4a").toFile();
            assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
            assertFailure(fake, destination);
            assertTrue(fake.canceled, "A failed bound must not reset a concurrent cancellation");
        }
        FakeClient fake = fixture(PLAYLIST);
        fake.responseHeaders.put(BASE + "seg1.m4s", Collections.singletonMap("Content-Range", Collections.singletonList("bytes 0-9/10")));
        File destination = directory.resolve("audio.m4a").toFile();
        assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertFailure(fake, destination);
    }

    @Test
    void boundsPlaylistEvenWithoutContentLength() {
        FakeClient fake = fixture(PLAYLIST);
        fake.responses.put(PLAYLIST_URL, new byte[1024 * 1024 + 1]);
        File destination = directory.resolve("audio.m4a").toFile();
        assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertFailure(fake, destination);
    }

    @Test
    void boundsPartCountBeforeAnyMediaFetch() {
        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n");
        for (int i = 0; i < 10000; i++) {
            text.append("#EXTINF:1,\nseg1.m4s\n");
        }
        text.append("#EXT-X-ENDLIST\n");
        FakeClient fake = fixture(text.toString());
        File destination = directory.resolve("audio.m4a").toFile();
        assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertFailure(fake, destination);
        assertEquals(1, fake.requests.size());
    }

    @Test
    void boundsUnknownLengthMediaPartsAndAggregateBytes() {
        FakeClient fake = fixture(PLAYLIST);
        fake.virtualMediaBytes = 64L * 1024 * 1024 + 1;
        fake.listener = null;
        File destination = directory.resolve("audio.m4a").toFile();
        assertThrows(IOException.class, () -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertFalse(destination.exists());
        assertSame(null, fake.listener);
        assertDirectoryEmpty();

        StringBuilder text = new StringBuilder("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n");
        for (int i = 0; i < 8; i++) {
            text.append("#EXTINF:1,\nseg1.m4s\n");
        }
        text.append("#EXT-X-ENDLIST\n");
        FakeClient aggregate = fixture(text.toString());
        aggregate.virtualMediaBytes = 64L * 1024 * 1024;
        aggregate.listener = null;
        assertThrows(IOException.class, () -> HlsAudioDownloader.save(aggregate.client, PLAYLIST_URL, destination));
        assertEquals(10, aggregate.requests.size());
        assertFalse(destination.exists());
        assertSame(null, aggregate.listener);
        assertDirectoryEmpty();
    }

    @Test
    void supportsNoListenerAndExplicitUnencryptedKey() {
        FakeClient fake = fixture(PLAYLIST.replace("#EXT-X-VERSION:7", "#EXT-X-KEY:METHOD=NONE"));
        fake.listener = null;
        File destination = directory.resolve("audio.m4a").toFile();
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> HlsAudioDownloader.save(fake.client, PLAYLIST_URL, destination));
        assertTrue(destination.length() > 0);
        assertSame(null, fake.listener);
        assertOnlyDestination(destination);
    }

    private void assertFailure(FakeClient fake, File destination) {
        assertFalse(destination.exists());
        assertEquals(0, fake.events.complete);
        assertEquals(0, fake.events.headers);
        assertEquals(0, fake.events.cancel);
        assertEquals(0, fake.events.errors);
        assertSame(fake.events, fake.listener);
        assertDirectoryEmpty();
    }

    private void assertDirectoryEmpty() {
        assertEquals(0, directory.toFile().list().length);
    }

    private void assertOnlyDestination(File destination) {
        assertArrayEquals(new String[]{destination.getName()}, directory.toFile().list());
    }

    private static FakeClient fixture(String playlist) {
        FakeClient fake = new FakeClient();
        fake.responses.put(PLAYLIST_URL, playlist.getBytes(StandardCharsets.UTF_8));
        // ISO BMFF box fixtures exercise byte-preserving assembly, not codec decoding.
        byte[] description = box("stsd", concat(new byte[4], new byte[]{0, 0, 0, 1},
                box("mp4a", concat(new byte[28], box("esds", new byte[4])))));
        byte[] track = box("trak", box("mdia", concat(box("mdhd", new byte[20]), box("hdlr", new byte[20]),
                box("minf", box("stbl", description)))));
        fake.responses.put(BASE + "init.mp4", concat(box("ftyp", concat("M4A ".getBytes(StandardCharsets.US_ASCII), new byte[4])), box("moov", track)));
        fake.responses.put(BASE + "seg1.m4s", concat(movieFragment(), box("mdat", new byte[]{5, 6})));
        fake.responses.put(BASE + "seg2.m4s", concat(movieFragment(), box("mdat", new byte[]{9, 10})));
        return fake;
    }

    private static byte[] movieFragment() {
        return box("moof", concat(box("mfhd", new byte[8]),
                box("traf", concat(box("tfhd", new byte[8]), box("trun", new byte[8])))));
    }

    private static byte[] box(String type, byte[] payload) {
        return ByteBuffer.allocate(8 + payload.length).putInt(8 + payload.length)
                .put(type.getBytes(StandardCharsets.US_ASCII)).put(payload).array();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    private static final class Events extends HttpClient.HttpClientListenerAdapter {
        private final ByteArrayOutputStream data = new ByteArrayOutputStream();
        private int complete;
        private int headers;
        private int cancel;
        private int errors;
        private String contentLength;
        private boolean cancelAfterData;
        private boolean cancelOnHeaders;
        private File destination;
        private long lengthAtCompletion;

        @Override
        public void onData(HttpClient client, byte[] buffer, int offset, int length) {
            data.write(buffer, offset, length);
            if (cancelAfterData) {
                client.cancel();
            }
        }

        @Override
        public void onComplete(HttpClient client) {
            complete++;
            if (destination != null) {
                lengthAtCompletion = destination.length();
            }
        }

        @Override
        public void onHeaders(HttpClient client, Map<String, List<String>> fields) {
            headers++;
            contentLength = fields.get("Content-Length").get(0);
            if (cancelOnHeaders) {
                client.cancel();
            }
        }

        @Override
        public void onCancel(HttpClient client) {
            cancel++;
        }

        @Override
        public void onError(HttpClient client, Throwable error) {
            errors++;
        }
    }

    private static final class FakeClient {
        private final Map<String, byte[]> responses = new HashMap<>();
        private final Map<String, Map<String, List<String>>> responseHeaders = new HashMap<>();
        private final List<String> requests = new ArrayList<>();
        private final Events events = new Events();
        private HttpClient.HttpClientListener listener = events;
        private boolean canceled;
        private String failUrl;
        private String cancelUrl;
        private long virtualMediaBytes;
        private final HttpClient client = (HttpClient) Proxy.newProxyInstance(HttpClient.class.getClassLoader(), new Class<?>[]{HttpClient.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getListener": return listener;
                        case "setListener": listener = (HttpClient.HttpClientListener) args[0]; return null;
                        case "isCanceled": return canceled;
                        case "cancel": canceled = true; return null;
                        case "resetCancellation": canceled = false; return null;
                        case "save":
                            saveResponse((HttpClient) proxy, (String) args[0], (File) args[1]);
                            return null;
                        default: throw new AssertionError("Unexpected client method: " + method.getName());
                    }
                });

        private void saveResponse(HttpClient source, String url, File file) throws IOException {
            requests.add(url);
            byte[] body = responses.get(url);
            if (body == null) {
                throw new IOException("Missing fixture response");
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                listener.onHeaders(source, responseHeaders.getOrDefault(url, Collections.emptyMap()));
                if (virtualMediaBytes > 0 && !url.equals(PLAYLIST_URL) && !url.endsWith("init.mp4")) {
                    // Simulate large streamed callbacks without writing a half-gigabyte fixture.
                    byte[] buffer = new byte[32768];
                    for (long remaining = virtualMediaBytes - body.length; remaining > 0 && !canceled; remaining -= buffer.length) {
                        listener.onData(source, buffer, 0, (int) Math.min(buffer.length, remaining));
                    }
                }
                int length = Math.min(32768, body.length);
                for (int offset = 0; offset < body.length && !canceled; offset += length) {
                    int n = Math.min(length, body.length - offset);
                    out.write(body, offset, n);
                    listener.onData(source, body, offset, n);
                    if (url.equals(cancelUrl)) {
                        canceled = true;
                    }
                    if (url.equals(failUrl)) {
                        listener.onError(source, new IOException("Fixture segment failure"));
                        throw new IOException("Fixture segment failure");
                    }
                }
            }
            if (canceled) {
                listener.onCancel(source);
            } else {
                listener.onComplete(source);
            }
        }
    }
}
