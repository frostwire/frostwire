/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

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
    public static final String BITCOIN_HEADER_CACHE_DIR = "bitcoin-headers";

    private RelayConstants() {
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
