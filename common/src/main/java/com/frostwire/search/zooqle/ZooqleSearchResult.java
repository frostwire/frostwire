package com.frostwire.search.zooqle;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by gubatron on 4/17/17.
 */
public class ZooqleSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;

    ZooqleSearchResult(String detailsUrl, String urlPrefix, SearchMatcher matcher) {
        this.detailsUrl = detailsUrl;
        filename = matcher.group("filename") + ".torrent";
        displayName = matcher.group("filename");
        seeds = Integer.valueOf(matcher.group("seeds").trim());
        torrentUrl = urlPrefix + "/download/" + matcher.group("torrent") + ".torrent";
        infoHash = matcher.group("infohash");
        size = calculateSize(matcher.group("size"), matcher.group("sizeUnit"));
        creationTime = parseCreationTime(matcher.group("year") + " " + matcher.group("month") + " " + matcher.group("day"));
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getSource() {
        return "Zooqle";
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public String getTorrentUrl() {
        return torrentUrl;
    }

    @Override
    public int getSeeds() {
        return seeds;
    }

    @Override
    public String getHash() {
        return infoHash;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            SimpleDateFormat myFormat = new SimpleDateFormat("yyyy MMM d", Locale.US);
            result = myFormat.parse(dateString).getTime();
        } catch (Throwable ignored) {
        }
        return result;
    }
}
