/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.Arrays;
import java.util.Base64;

/**
 * Immutable metadata for a single torrent that this FrostWire node is
 * sharing or has seen, held in the local {@code shared_torrents} table
 * and republished to the DHT as an {@code IndexAnnouncement}.
 */
public final class LocalSharedTorrent {
    public static final int INFO_HASH_V1_LENGTH = 20;
    public static final int INFO_HASH_V2_LENGTH = 32;
    public static final int ED25519_PUB_LENGTH = 32;

    private final byte[] infoHash;
    private final String name;
    private final long sizeBytes;
    private final int fileCount;
    private final String filesJson;
    private final String tags;
    private final byte[] publisherNodeId;
    private final byte[] publisherEd25519Pub;
    private final int publisherUtpPort;
    private final long addedAt;
    private final long lastSeenAt;
    private final Long lastPublishedAt;

    private LocalSharedTorrent(Builder b) {
        this.infoHash = b.infoHash.clone();
        this.name = b.name;
        this.sizeBytes = b.sizeBytes;
        this.fileCount = b.fileCount;
        this.filesJson = b.filesJson == null ? "[]" : b.filesJson;
        this.tags = b.tags == null ? "" : b.tags;
        this.publisherNodeId = b.publisherNodeId.clone();
        this.publisherEd25519Pub = b.publisherEd25519Pub.clone();
        this.publisherUtpPort = b.publisherUtpPort;
        this.addedAt = b.addedAt;
        this.lastSeenAt = b.lastSeenAt;
        this.lastPublishedAt = b.lastPublishedAt;
    }

    public byte[] infoHash() {
        return infoHash.clone();
    }

    public String infoHashHex() {
        return com.frostwire.util.Hex.encode(infoHash);
    }

    public String name() {
        return name;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public int fileCount() {
        return fileCount;
    }

    public String filesJson() {
        return filesJson;
    }

    public String tags() {
        return tags;
    }

    public byte[] publisherNodeId() {
        return publisherNodeId.clone();
    }

    public byte[] publisherEd25519Pub() {
        return publisherEd25519Pub.clone();
    }

    public String publisherEd25519PubB64() {
        return Base64.getEncoder().withoutPadding().encodeToString(publisherEd25519Pub);
    }

    public int publisherUtpPort() {
        return publisherUtpPort;
    }

    public long addedAt() {
        return addedAt;
    }

    public long lastSeenAt() {
        return lastSeenAt;
    }

    public Long lastPublishedAt() {
        return lastPublishedAt;
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.infoHash = this.infoHash;
        b.name = this.name;
        b.sizeBytes = this.sizeBytes;
        b.fileCount = this.fileCount;
        b.filesJson = this.filesJson;
        b.tags = this.tags;
        b.publisherNodeId = this.publisherNodeId;
        b.publisherEd25519Pub = this.publisherEd25519Pub;
        b.publisherUtpPort = this.publisherUtpPort;
        b.addedAt = this.addedAt;
        b.lastSeenAt = this.lastSeenAt;
        b.lastPublishedAt = this.lastPublishedAt;
        return b;
    }

    public static String shortNodeId(byte[] nodeId) {
        String hex = com.frostwire.util.Hex.encode(nodeId);
        return hex.length() <= 8 ? hex : hex.substring(0, 8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalSharedTorrent)) return false;
        LocalSharedTorrent that = (LocalSharedTorrent) o;
        return Arrays.equals(infoHash, that.infoHash);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(infoHash);
    }

    @Override
    public String toString() {
        return "LocalSharedTorrent{infoHash=" + infoHashHex() +
                ", name='" + name + '\'' +
                ", sizeBytes=" + sizeBytes +
                ", fileCount=" + fileCount +
                ", lastSeenAt=" + lastSeenAt +
                ", lastPublishedAt=" + lastPublishedAt + '}';
    }

    public static final class Builder {
        private byte[] infoHash;
        private String name;
        private long sizeBytes;
        private int fileCount;
        private String filesJson = "[]";
        private String tags = "";
        private byte[] publisherNodeId;
        private byte[] publisherEd25519Pub;
        private int publisherUtpPort;
        private long addedAt;
        private long lastSeenAt;
        private Long lastPublishedAt;

        public Builder infoHash(byte[] v) {
            this.infoHash = v;
            return this;
        }

        public Builder name(String v) {
            this.name = v;
            return this;
        }

        public Builder sizeBytes(long v) {
            this.sizeBytes = v;
            return this;
        }

        public Builder fileCount(int v) {
            this.fileCount = v;
            return this;
        }

        public Builder filesJson(String v) {
            this.filesJson = v;
            return this;
        }

        public Builder tags(String v) {
            this.tags = v;
            return this;
        }

        public Builder publisherNodeId(byte[] v) {
            this.publisherNodeId = v;
            return this;
        }

        public Builder publisherEd25519Pub(byte[] v) {
            this.publisherEd25519Pub = v;
            return this;
        }

        public Builder publisherUtpPort(int v) {
            this.publisherUtpPort = v;
            return this;
        }

        public Builder addedAt(long v) {
            this.addedAt = v;
            return this;
        }

        public Builder lastSeenAt(long v) {
            this.lastSeenAt = v;
            return this;
        }

        public Builder lastPublishedAt(Long v) {
            this.lastPublishedAt = v;
            return this;
        }

        public LocalSharedTorrent build() {
            if (infoHash == null || (infoHash.length != INFO_HASH_V1_LENGTH && infoHash.length != INFO_HASH_V2_LENGTH)) {
                throw new IllegalArgumentException("infoHash must be 20 (v1) or 32 (v2) bytes");
            }
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("name must be non-empty");
            }
            if (sizeBytes < 0) {
                throw new IllegalArgumentException("sizeBytes must be >= 0");
            }
            if (fileCount <= 0) {
                throw new IllegalArgumentException("fileCount must be > 0");
            }
            if (publisherNodeId == null || publisherNodeId.length != 20) {
                throw new IllegalArgumentException("publisherNodeId must be 20 bytes");
            }
            if (publisherEd25519Pub == null || publisherEd25519Pub.length != ED25519_PUB_LENGTH) {
                throw new IllegalArgumentException("publisherEd25519Pub must be 32 bytes");
            }
            if (addedAt <= 0) {
                throw new IllegalArgumentException("addedAt must be > 0");
            }
            if (lastSeenAt < addedAt) {
                throw new IllegalArgumentException("lastSeenAt must be >= addedAt");
            }
            return new LocalSharedTorrent(this);
        }
    }
}
