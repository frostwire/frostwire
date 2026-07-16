/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

/**
 * Constants shared by the IceBridge protocol implementation.
 */
public final class IceBridgeConstants {

    private IceBridgeConstants() {
        // utility class
    }

    /** Protocol version for inter-servent rUDP framing. */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * Monotonic software release code advertised by this IceBridge build.
     * Crawlers / stats jobs can histogram this for network penetration.
     * Bump on every IceBridge-facing release that changes wire behavior or
     * identity/registry fields.
     */
    public static final int SOFTWARE_VERSION_CODE = 1;

    /**
     * Human-readable IceBridge software version (semver). Announced in
     * {@code IdentityRecord} ({@code ib_ver}) and control-plane register/lookup.
     */
    public static final String SOFTWARE_VERSION = "1.1.0";

    /** Default Ed25519 public-key length in bytes. */
    public static final int ED25519_PUB_LENGTH = 32;

    /** Default Ed25519 signature length in bytes. */
    public static final int ED25519_SIG_LENGTH = 64;

    /** KDF label for encrypted relay sessions (reserved for future E2E). */
    public static final String RELAY_SESSION_KDF_LABEL = "frostwire-icebridge-session-v1";

    /** Thread name prefix for IceBridge executor pools. */
    public static final String THREAD_PREFIX = "icebridge-";
}