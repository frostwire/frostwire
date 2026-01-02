/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

import com.frostwire.licenses.License;

import java.util.List;
import java.util.Optional;

/**
 * Simplified, flat search result class using composition instead of inheritance.
 * All result types (torrent, streaming, crawlable, etc.) are represented through
 * optional metadata fields.
 *
 * Results indicate via isPreliminary() whether they require a second step
 * (format selection, file selection, etc.) before the actual download can begin.
 *
 * @author gubatron
 */
public class CompositeFileSearchResult implements FileSearchResult {
    private final String displayName;
    private final String filename;
    private final long size;
    private final String detailsUrl;
    private final String source;
    private final long creationTime;
    private final License license;
    private final String thumbnailUrl;
    private final boolean preliminary;

    // Optional metadata composed into the result
    private final Optional<Integer> viewCount;
    private final Optional<TorrentMetadata> torrent;
    private final Optional<StreamingCapability> streaming;
    private final Optional<CrawlableCapability> crawlable;

    public CompositeFileSearchResult(
            String displayName,
            String filename,
            long size,
            String detailsUrl,
            String source,
            long creationTime,
            License license,
            String thumbnailUrl,
            boolean preliminary,
            Optional<Integer> viewCount,
            Optional<TorrentMetadata> torrent,
            Optional<StreamingCapability> streaming,
            Optional<CrawlableCapability> crawlable) {
        this.displayName = displayName;
        this.filename = filename;
        this.size = size;
        this.detailsUrl = detailsUrl;
        this.source = source;
        this.creationTime = creationTime;
        this.license = license;
        this.thumbnailUrl = thumbnailUrl;
        this.preliminary = preliminary;
        this.viewCount = viewCount;
        this.torrent = torrent;
        this.streaming = streaming;
        this.crawlable = crawlable;
    }

    // SearchResult interface implementation
    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String getSource() {
        return source;
    }

    @Override
    public License getLicense() {
        return license != null ? license : com.frostwire.licenses.Licenses.UNKNOWN;
    }

    @Override
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    @Override
    public boolean isPreliminary() {
        // Whether this result requires a secondary search/step is decided by the search performer/pattern
        // that created it, not inferred from capabilities
        return preliminary;
    }

    // File-specific accessors
    public String getFilename() {
        return filename;
    }

    public long getSize() {
        return size;
    }

    // Torrent-specific accessors (null-safe via Optional)
    public Optional<String> getTorrentUrl() {
        return torrent.map(TorrentMetadata::getUrl);
    }

    public Optional<String> getTorrentHash() {
        return torrent.map(TorrentMetadata::getHash);
    }

    public Optional<Integer> getSeeds() {
        return torrent.map(TorrentMetadata::getSeeds);
    }

    public Optional<String> getReferrerUrl() {
        return torrent.map(TorrentMetadata::getReferrerUrl);
    }

    public boolean isTorrent() {
        return torrent.isPresent();
    }

    // Streaming-specific accessors
    public Optional<String> getStreamUrl() {
        return streaming.map(StreamingCapability::getUrl);
    }

    public boolean isStreamable() {
        return streaming.isPresent();
    }

    // Crawlable-specific accessors
    public Optional<List<FileSearchResult>> getCrawledChildren() {
        return crawlable.map(c -> c.children);
    }

    public boolean isCrawlable() {
        return crawlable.isPresent() && !crawlable.get().isComplete;
    }

    public boolean isCrawlComplete() {
        return crawlable.isPresent() && crawlable.get().isComplete;
    }

    public void setCrawlableChildren(List<FileSearchResult> children) {
        if (crawlable.isPresent()) {
            crawlable.get().children = children;
            crawlable.get().isComplete = true;
        }
    }

    public Optional<Integer> getViewCount() {
        return viewCount;
    }

    // Builder pattern for easier construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String displayName;
        private String filename;
        private long size = -1;
        private String detailsUrl;
        private String source;
        private long creationTime = -1;
        private License license;
        private String thumbnailUrl;
        private boolean preliminary = false;  // Default: not preliminary
        private Integer viewCount;  // Optional view count
        private TorrentMetadata torrent;
        private StreamingCapability streaming;
        private CrawlableCapability crawlable;

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder size(long size) {
            this.size = size;
            return this;
        }

        public Builder detailsUrl(String detailsUrl) {
            this.detailsUrl = detailsUrl;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder creationTime(long creationTime) {
            this.creationTime = creationTime;
            return this;
        }

        public Builder license(License license) {
            this.license = license;
            return this;
        }

        public Builder thumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public Builder preliminary(boolean preliminary) {
            this.preliminary = preliminary;
            return this;
        }

        public Builder torrent(TorrentMetadata torrent) {
            this.torrent = torrent;
            return this;
        }

        public Builder torrent(String url, String hash, int seeds, String referrerUrl) {
            this.torrent = new TorrentMetadata(url, hash, seeds, referrerUrl);
            return this;
        }

        public Builder streaming(StreamingCapability streaming) {
            this.streaming = streaming;
            return this;
        }

        public Builder streaming(String url) {
            this.streaming = new StreamingCapability(url);
            return this;
        }

        public Builder crawlable(CrawlableCapability crawlable) {
            this.crawlable = crawlable;
            return this;
        }

        public Builder crawlable() {
            this.crawlable = new CrawlableCapability();
            return this;
        }

        public Builder viewCount(int viewCount) {
            this.viewCount = viewCount;
            return this;
        }

        public CompositeFileSearchResult build() {
            return new CompositeFileSearchResult(
                    displayName,
                    filename,
                    size,
                    detailsUrl,
                    source,
                    creationTime,
                    license,
                    thumbnailUrl,
                    preliminary,
                    Optional.ofNullable(viewCount),
                    Optional.ofNullable(torrent),
                    Optional.ofNullable(streaming),
                    Optional.ofNullable(crawlable)
            );
        }
    }

    @Override
    public String toString() {
        return getDetailsUrl();
    }
}
