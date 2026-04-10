package com.frostwire.mcp.desktop.adapters;

import com.frostwire.search.HttpSearchResult;
import com.frostwire.search.SearchResult;
import com.frostwire.search.torrent.TorrentSearchResult;
import com.google.gson.JsonObject;

public class SearchAdapter {
    private SearchAdapter() {
    }

    public static JsonObject toMCPJson(SearchResult result) {
        JsonObject json = new JsonObject();
        json.addProperty("displayName", result.getDisplayName());
        json.addProperty("source", result.getSource());
        json.addProperty("creationTime", result.getCreationTime());
        json.addProperty("detailsUrl", result.getDetailsUrl() != null ? result.getDetailsUrl() : "");
        json.addProperty("thumbnailUrl", result.getThumbnailUrl() != null ? result.getThumbnailUrl() : "");
        json.addProperty("license", result.getLicense() != null ? result.getLicense().getName() : "");

        if (result instanceof TorrentSearchResult) {
            TorrentSearchResult tsr = (TorrentSearchResult) result;
            json.addProperty("filename", tsr.getFilename());
            json.addProperty("size", tsr.getSize());
            json.addProperty("seeds", tsr.getSeeds());
            json.addProperty("hash", tsr.getHash() != null ? tsr.getHash() : "");
            json.addProperty("torrentUrl", tsr.getTorrentUrl() != null ? tsr.getTorrentUrl() : "");
            json.addProperty("fileType", "torrent");
        } else if (result instanceof HttpSearchResult) {
            HttpSearchResult hsr = (HttpSearchResult) result;
            json.addProperty("filename", hsr.getFilename());
            json.addProperty("size", hsr.getSize());
            json.addProperty("downloadUrl", hsr.getDownloadUrl() != null ? hsr.getDownloadUrl() : "");
            json.addProperty("fileType", "cloud");
        }

        return json;
    }
}
