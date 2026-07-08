/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Constants shared by distributed search, relayd, and desktop UI glue.
 */
public final class RelayConstants {
    public static final int RELAY_REGISTRY_TTL_SEC = 30 * 60;
    public static final int IDENTITY_REPUBLISH_INTERVAL_SEC = 5 * 60;
    public static final int RELAY_REPUBLISH_INTERVAL_SEC = 5 * 60;
    public static final int KARMA_COMMIT_INTERVAL_SEC = 30 * 60;
    public static final int KARMA_PUBLISH_INTERVAL_SEC = 30 * 60;
    public static final int DEFAULT_MAX_QPS = 5;
    public static final int WOT_MAX_DEPTH = 3;

    public static final int CHALLENGE_NONCE_BYTES = 32;
    public static final int TRANSPORT_NONCE_BYTES = 12;
    public static final int PAYLOAD_NONCE_BYTES = 12;

    public static final String TOPIC_PEERS = "frostwire-peers-v1";
    public static final String TOPIC_RELAYS = "frostwire-relays-v1";
    public static final String TOPIC_BOOTSTRAP = "frostwire-bootstrap-v1";

    public static final String BEP46_SALT_IDENTITY = "frostwire-identity-v1";
    public static final String BEP46_SALT_INDEX = "frostwire-index-v1";

    public static final String TRANSPORT_KDF_LABEL = "frostwire-relay-transport-v1";
    public static final String PAYLOAD_KDF_LABEL = "frostwire-search-v1";

    public static final int LOCAL_INDEX_MAX_RESULTS = 50;
    public static final int LOCAL_INDEX_PEER_LRU_PER_PEER = 10_000;
    public static final int PEER_INDEX_SWEEP_INTERVAL_SEC = 600;
    public static final int EVENT_LOG_MAX_ROWS = 10_000;
    public static final String EVENT_LOG_PATH = "distributed_search_log.jsonl";
    public static final String IDENTITY_FILE = "identity.dat";
    /**
     * Subdirectory under the platform user settings dir for identity, local
     * index DB, and related relay state (desktop: FrostWire5 settings;
     * Android: app files dir when callers pass that root).
     */
    public static final String RELAY_HOME_DIR = "libtorrent";
    public static final String BITCOIN_HEADER_CACHE_DIR = "bitcoin-headers";

    /**
     * Default TCP port for the relay search server. Separate from
     * the BitTorrent listen port range to avoid conflicts.
     */
    public static final int RELAY_LISTEN_PORT = 6888;

    private RelayConstants() {
    }

    /**
     * {@code userSettingsDir/libtorrent} — shared home for identity + index.
     *
     * @param userSettingsDir platform settings root (must not be null)
     */
    public static File relayHomeDir(File userSettingsDir) {
        if (userSettingsDir == null) {
            throw new IllegalArgumentException("userSettingsDir is null");
        }
        return new File(userSettingsDir, RELAY_HOME_DIR);
    }

    /**
     * Canonical identity path used by desktop Initializer, Identity settings,
     * and IceBridge child launch: {@code userSettingsDir/libtorrent/identity.dat}.
     */
    public static File identityFile(File userSettingsDir) {
        return new File(relayHomeDir(userSettingsDir), IDENTITY_FILE);
    }

    public static byte[] topicHash(String topic) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return sha1.digest(topic.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}
