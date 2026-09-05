/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.telluride;

import com.frostwire.mp4.Mp4Muxer;
import com.frostwire.webm.WebMMuxer;

import java.io.File;
import java.io.IOException;

/**
 * Single authority for which DASH container pairs can be merged into a file
 * with sound, and the dispatcher that performs the merge.
 *
 * <p>Supported pairs (same-container only, codecs ride along verbatim):
 * <ul>
 *   <li>MP4 video + MP4/M4A audio → regular MP4 ({@link Mp4Muxer});</li>
 *   <li>WebM video + WebM audio (usually VP9 + Opus) → WebM ({@link WebMMuxer}).</li>
 * </ul>
 *
 * <p>Both the performer (sibling selection) and the download hooks consult
 * {@link #supportsPair} so an unsupported pair never triggers a useless
 * sibling fetch. Mux failures throw; callers keep the silent video.
 */
public final class DashMux {

    private DashMux() {
    }

    /** True when this video/audio extension pair can be merged. Null-safe. */
    public static boolean supportsPair(String videoExt, String audioExt) {
        if (videoExt == null || audioExt == null) {
            return false;
        }
        if (("mp4".equalsIgnoreCase(videoExt))
                && ("m4a".equalsIgnoreCase(audioExt) || "mp4".equalsIgnoreCase(audioExt))) {
            return true;
        }
        return "webm".equalsIgnoreCase(videoExt) && "webm".equalsIgnoreCase(audioExt);
    }

    /**
     * Merge when supported; returns false (no-op) otherwise. Callers treat
     * false and thrown IOException identically: keep the silent video.
     */
    public static boolean muxIfSupported(File videoFile, File audioFile,
                                         String videoExt, String audioExt, File outFile)
            throws IOException {
        if (videoFile == null || audioFile == null || outFile == null) {
            throw new IllegalArgumentException("files are required");
        }
        if (!supportsPair(videoExt, audioExt)) {
            return false;
        }
        if ("webm".equalsIgnoreCase(videoExt)) {
            WebMMuxer.mux(videoFile, audioFile, outFile);
        } else {
            Mp4Muxer.mux(videoFile, audioFile, outFile);
        }
        return true;
    }
}
