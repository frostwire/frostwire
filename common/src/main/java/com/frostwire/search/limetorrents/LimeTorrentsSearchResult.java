package com.frostwire.search.limetorrents;

import com.frostwire.search.SearchMatcher;
import com.frostwire.search.torrent.AbstractTorrentSearchResult;
import com.frostwire.util.HtmlManipulator;
import com.frostwire.util.StringUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by alejandroarturom on 26-08-16.
 */
public final class LimeTorrentsSearchResult extends AbstractTorrentSearchResult {
    private final String filename;
    private final String displayName;
    private final String detailsUrl;
    private final String torrentUrl;
    private final String infoHash;
    private final long size;
    private final long creationTime;
    private final int seeds;


    LimeTorrentsSearchResult(String domainName, String detailsUrl, SearchMatcher matcher) {
    this.detailsUrl = detailsUrl;
    this.infoHash = null;
    this.filename = parseFileName(matcher.group("filename"), FilenameUtils.getBaseName(detailsUrl));
    this.size = parseSize(matcher.group("filesize") + " " + matcher.group("unit"));
    this.creationTime = parseCreationTime(matcher.group("time"));
    this.seeds = parseSeeds(matcher.group("seeds"));
    this.torrentUrl = "http://itorrents.org/torrent/" + matcher.group("torrentid") + ".torrent";
    this.displayName = HtmlManipulator.replaceHtmlEntities(FilenameUtils.getBaseName(filename));
}
    @Override
    public long getSize() {
        return size;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getSource() {
        return "LimeTorrents";
    }

    @Override
    public String getHash() {
        return infoHash;
    }

    @Override
    public int getSeeds() {
        return seeds;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public String getTorrentUrl() {
        return torrentUrl;
    }

    private String parseFileName(String urlEncodedFileName, String fallbackName) {
        String decodedFileName = fallbackName;
        try {
            if (!StringUtils.isNullOrEmpty(urlEncodedFileName)) {
                decodedFileName = URLDecoder.decode(urlEncodedFileName, "UTF-8");
                decodedFileName.replace("&amp;", "and");
            }
        } catch (UnsupportedEncodingException e) {
        }
        return decodedFileName + ".torrent";
    }

    private int parseSeeds(String group) {
        try {
            return Integer.parseInt(group);
        } catch (Exception e) {
            return 0;
        }
    }

    private long parseCreationTime(String dateString) {
        long result = System.currentTimeMillis();
        try {
            SimpleDateFormat myFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            result = myFormat.parse(dateString).getTime();
        } catch (Throwable t) {
        }
        return result;
    }
}
