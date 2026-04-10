package com.frostwire.mcp.desktop.adapters;

import com.frostwire.util.PlaybackUtil;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.Set;

public final class LibraryAdapter {

    private static final Set<String> AUDIO_EXT = Set.of("mp3", "ogg", "flac", "wav", "aac", "m4a", "wma", "opus");
    private static final Set<String> VIDEO_EXT = Set.of("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v");
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "bmp", "svg", "webp");
    private static final Set<String> DOC_EXT = Set.of("pdf", "doc", "docx", "txt", "odt", "rtf", "epub");
    private static final Set<String> TORRENT_EXT = Set.of("torrent");

    private LibraryAdapter() {
    }

    public static String getMediaType(String extension) {
        if (extension == null || extension.isEmpty()) {
            return "other";
        }
        String ext = extension.toLowerCase();
        if (AUDIO_EXT.contains(ext)) return "audio";
        if (VIDEO_EXT.contains(ext)) return "video";
        if (IMAGE_EXT.contains(ext)) return "images";
        if (DOC_EXT.contains(ext)) return "documents";
        if (TORRENT_EXT.contains(ext)) return "torrents";
        return "other";
    }

    public static JsonObject toFileJson(File file) {
        JsonObject json = new JsonObject();
        json.addProperty("name", file.getName());
        json.addProperty("path", file.getAbsolutePath());
        json.addProperty("size", file.length());
        json.addProperty("lastModified", file.lastModified());
        String ext = "";
        String name = file.getName();
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx >= 0 && dotIdx < name.length() - 1) {
            ext = name.substring(dotIdx + 1);
        }
        json.addProperty("extension", ext);
        json.addProperty("mediaType", getMediaType(ext));
        json.addProperty("playable", PlaybackUtil.isPlayableFile(name));
        return json;
    }
}
