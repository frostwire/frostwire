/*
 *     Copyright (c) 2026, FrostWire(R). All rights reserved.
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.transfers;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.frostwire.util.http.HttpClient;

/** Saves finite, unencrypted SoundCloud fMP4 audio without transcoding. */
public final class HlsAudioDownloader {
    private static final long MAX_PLAYLIST_BYTES = 1024 * 1024;
    private static final long MAX_PART_BYTES = 64L * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 512L * 1024 * 1024;
    private static final int MAX_PARTS = 10000;
    private static final int BUFFER_SIZE = 32768;
    private static final int MAX_BOXES = 10000;
    private static final int MAX_BOX_DEPTH = 12;
    private static final Pattern MAP = Pattern.compile("#EXT-X-MAP:URI=\"([^\"]+)\"");

    private HlsAudioDownloader() {
    }

    /**
     * Blocking I/O: callers must run off the UI thread and exclusively borrow the client.
     * Restores its listener; cancellation removes partial output and reports cancel once.
     * IOException is propagated without error callbacks so the caller owns error reporting.
     */
    public static void save(HttpClient client, String playlistUrl, File destination) throws IOException {
        HttpClient.HttpClientListener original = client.getListener();
        Guard guard = new Guard(client, original);
        boolean outputStarted = false;
        boolean success = false;
        try {
            client.setListener(guard);
            if (client.isCanceled()) {
                return;
            }
            URI playlist = cdnUri(null, playlistUrl);
            List<URI> parts;
            File scratch = File.createTempFile("fw-hls-playlist-", ".tmp", destination.getAbsoluteFile().getParentFile());
            try {
                fetch(client, guard, playlist, scratch, MAX_PLAYLIST_BYTES, false);
                if (client.isCanceled()) {
                    return;
                }
                parts = parse(playlist, readPlaylist(scratch));
            } finally {
                deleteTemporary(scratch);
            }
            long total = 0;
            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(destination))) {
                outputStarted = true;
                byte[] buffer = new byte[BUFFER_SIZE];
                for (int index = 0; index < parts.size(); index++) {
                    URI part = parts.get(index);
                    if (client.isCanceled()) {
                        return;
                    }
                    scratch = File.createTempFile("fw-hls-part-", ".tmp", destination.getAbsoluteFile().getParentFile());
                    try {
                        fetch(client, guard, part, scratch, Math.min(MAX_PART_BYTES, MAX_AUDIO_BYTES - total), true);
                        if (client.isCanceled()) {
                            return;
                        }
                        checkFragment(scratch, index == 0);
                        try (FileInputStream in = new FileInputStream(scratch)) {
                            int n;
                            while ((n = in.read(buffer)) != -1) {
                                if (client.isCanceled()) {
                                    return;
                                }
                                out.write(buffer, 0, n);
                                total += n;
                            }
                        }
                    } finally {
                        deleteTemporary(scratch);
                    }
                }
            }
            if (client.isCanceled()) {
                return;
            }
            client.setListener(original);
            if (original != null) {
                original.onHeaders(client, Collections.singletonMap("Content-Length", Collections.singletonList(Long.toString(total))));
                if (client.isCanceled()) {
                    return;
                }
                success = true;
                original.onComplete(client);
            } else {
                success = true;
            }
        } finally {
            try {
                if (!success && outputStarted) {
                    deleteTemporary(destination);
                }
            } finally {
                client.setListener(original);
                if (guard.failure == null && (client.isCanceled() || guard.canceled) && original != null) {
                    original.onCancel(client);
                }
            }
        }
    }

    private static String readPlaylist(File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if ((long) out.size() + count > MAX_PLAYLIST_BYTES) {
                    throw new IOException("Oversized HLS playlist");
                }
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void deleteTemporary(File file) throws IOException {
        if (file.exists() && !file.delete() && file.exists()) {
            throw new IOException("Unable to delete HLS temporary file");
        }
    }

    private static void fetch(HttpClient client, Guard guard, URI uri, File scratch, long limit, boolean media) throws IOException {
        guard.limit = limit;
        guard.bytes = 0;
        guard.media = media;
        try {
            client.save(uri.toString(), scratch, false);
        } catch (LimitExceeded e) {
            throw guard.failure;
        } catch (IOException e) {
            if (guard.failure != null) {
                throw guard.failure;
            }
            if (!client.isCanceled()) {
                throw e;
            }
        }
        if (guard.failure != null) {
            throw guard.failure;
        }
        if (!client.isCanceled() && (scratch.length() == 0 || scratch.length() > limit)) {
            throw new IOException("Empty or oversized HLS response");
        }
    }

    private static List<URI> parse(URI playlist, String text) throws IOException {
        String[] lines = text.split("\\r?\\n");
        if (lines.length == 0 || !"#EXTM3U".equals(lines[0].trim())) {
            throw new IOException("Invalid HLS playlist");
        }
        List<URI> parts = new ArrayList<>();
        boolean end = false;
        boolean segment = false;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (end) {
                throw new IOException("Data after HLS end marker");
            }
            if (line.startsWith("#EXT-X-MAP:")) {
                Matcher matcher = MAP.matcher(line);
                if (!parts.isEmpty() || !matcher.matches()) {
                    throw new IOException("Unsupported HLS initialization map");
                }
                parts.add(cdnUri(playlist, matcher.group(1)));
            } else if (line.startsWith("#EXTINF:")) {
                if (parts.isEmpty() || segment) {
                    throw new IOException("Invalid HLS segment");
                }
                try {
                    String duration = line.substring(8).split(",", -1)[0];
                    double value = Double.parseDouble(duration);
                    if (!Double.isFinite(value) || value <= 0 || !line.contains(",")) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    throw new IOException("Invalid HLS duration", e);
                }
                segment = true;
            } else if (line.equals("#EXT-X-ENDLIST")) {
                end = true;
            } else if (line.equals("#EXT-X-KEY:METHOD=NONE")) {
                // Explicitly unencrypted.
            } else if (line.startsWith("#EXT-X-VERSION:") || line.startsWith("#EXT-X-TARGETDURATION:")
                    || line.startsWith("#EXT-X-MEDIA-SEQUENCE:") || line.equals("#EXT-X-PLAYLIST-TYPE:VOD")
                    || line.equals("#EXT-X-INDEPENDENT-SEGMENTS")) {
                // Metadata does not change ordered whole-part assembly.
            } else if (line.startsWith("#EXT")) {
                throw new IOException("Unsupported HLS tag");
            } else if (!line.startsWith("#")) {
                if (!segment || parts.size() >= MAX_PARTS) {
                    throw new IOException("Invalid or excessive HLS segments");
                }
                parts.add(cdnUri(playlist, line));
                segment = false;
            }
        }
        if (!end || segment || parts.size() < 2) {
            throw new IOException("Incomplete or non-finite HLS playlist");
        }
        return parts;
    }

    private static void checkFragment(File file, boolean initialization) throws IOException {
        Set<String> all = new HashSet<>();
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            Set<String> roots = inspectBoxes(input, 0, input.length(), 0, new int[1], all);
            boolean valid = initialization
                    ? roots.contains("ftyp") && roots.contains("moov") && all.contains("trak")
                        && all.contains("mdhd") && all.contains("hdlr") && all.contains("stsd")
                        && all.contains("mp4a") && all.contains("esds")
                    : roots.contains("moof") && roots.contains("mdat") && all.contains("mfhd")
                        && all.contains("traf") && all.contains("tfhd") && all.contains("trun");
            if (!valid) {
                throw new IOException("HLS response is not complete fragmented MP4 audio");
            }
        }
    }

    // Validate bounded box structure, not codec contents; never allocate from declared box sizes.
    private static Set<String> inspectBoxes(RandomAccessFile input, long start, long end, int depth,
                                            int[] count, Set<String> all) throws IOException {
        if (depth > MAX_BOX_DEPTH) {
            throw new IOException("Excessive MP4 box depth");
        }
        Set<String> types = new HashSet<>();
        for (long position = start; position < end;) {
            if (end - position < 8 || ++count[0] > MAX_BOXES) {
                throw new IOException("Truncated or excessive MP4 boxes");
            }
            input.seek(position);
            long size = input.readInt() & 0xffffffffL;
            byte[] typeBytes = new byte[4];
            input.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.US_ASCII);
            int header = 8;
            if (size == 1) {
                if (end - position < 16) {
                    throw new IOException("Truncated extended MP4 box");
                }
                size = input.readLong();
                header = 16;
            } else if (size == 0) {
                size = end - position;
            }
            if (size < header || size > end - position) {
                throw new IOException("Invalid MP4 box length");
            }
            long payload = position + header;
            long boxEnd = position + size;
            types.add(type);
            all.add(type);
            switch (type) {
                case "moov": case "trak": case "mdia": case "minf": case "stbl": case "moof": case "traf":
                    inspectBoxes(input, payload, boxEnd, depth + 1, count, all);
                    break;
                case "stsd":
                    if (size - header < 8) {
                        throw new IOException("Truncated MP4 sample description");
                    }
                    input.seek(payload + 4);
                    long entries = input.readInt() & 0xffffffffL;
                    if (entries == 0 || entries > MAX_BOXES) {
                        throw new IOException("Invalid MP4 sample description count");
                    }
                    inspectBoxes(input, payload + 8, boxEnd, depth + 1, count, all);
                    break;
                case "mp4a":
                    if (size - header < 28) {
                        throw new IOException("Truncated AAC sample entry");
                    }
                    inspectBoxes(input, payload + 28, boxEnd, depth + 1, count, all);
                    break;
                case "ftyp": case "mfhd": case "tfhd": case "trun":
                    if (size - header < 8) {
                        throw new IOException("Truncated MP4 metadata box");
                    }
                    break;
                case "mdat": case "esds":
                    if (size == header) {
                        throw new IOException("Empty MP4 audio box");
                    }
                    break;
                default:
                    break;
            }
            position = boxEnd;
        }
        return types;
    }

    private static URI cdnUri(URI base, String value) throws IOException {
        try {
            URI uri = new URI(value);
            if (base != null) {
                // URI.resolve treats a query-only reference as a directory on some JVMs.
                if (value.startsWith("?")) {
                    uri = new URI(base.getScheme() + "://" + base.getRawAuthority() + base.getRawPath() + value);
                } else {
                    uri = base.resolve(uri);
                }
            }
            String host = uri.getHost();
            host = host == null ? "" : host.toLowerCase(Locale.ROOT);
            boolean soundcloudCdn = host.equals("sndcdn.com") || host.endsWith(".sndcdn.com")
                    || host.equals("playback.media-streaming.soundcloud.cloud");
            if (value.isEmpty() || !"https".equalsIgnoreCase(uri.getScheme()) || !soundcloudCdn
                    || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443) || value.indexOf('\\') >= 0) {
                throw new IOException("Unsafe HLS CDN URI");
            }
            return uri;
        } catch (URISyntaxException | NullPointerException e) {
            throw new IOException("Invalid HLS CDN URI", e);
        }
    }

    private static final class LimitExceeded extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class Guard extends HttpClient.HttpClientListenerAdapter {
        private final HttpClient client;
        private final HttpClient.HttpClientListener original;
        private long limit;
        private long bytes;
        private long audioBytes;
        private boolean media;
        private boolean canceled;
        private IOException failure;

        private Guard(HttpClient client, HttpClient.HttpClientListener original) {
            this.client = client;
            this.original = original;
        }

        @Override
        public void onHeaders(HttpClient source, Map<String, List<String>> headers) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if ("Content-Range".equalsIgnoreCase(entry.getKey())) {
                    fail("Unexpected partial HLS response");
                } else if ("Content-Length".equalsIgnoreCase(entry.getKey())) {
                    for (String value : entry.getValue()) {
                        try {
                            long length = Long.parseLong(value);
                            if (length < 0 || length > limit) {
                                fail("Oversized HLS response");
                            }
                        } catch (NumberFormatException e) {
                            fail("Invalid HLS response length");
                        }
                    }
                }
            }
        }

        @Override
        public void onData(HttpClient source, byte[] buffer, int offset, int length) {
            bytes += length;
            if (media) {
                audioBytes += length;
            }
            if (bytes > limit || audioBytes > MAX_AUDIO_BYTES || failure != null) {
                fail("Oversized HLS response");
                throw new LimitExceeded();
            }
            if (media && original != null) {
                original.onData(client, buffer, offset, length);
            }
        }

        @Override
        public void onCancel(HttpClient source) {
            canceled = true;
        }

        @Override
        public void onError(HttpClient source, Throwable error) {
            failure = new IOException("HLS fetch failed", error);
        }

        private void fail(String message) {
            if (failure == null) {
                failure = new IOException(message);
            }
            client.cancel();
        }
    }
}
