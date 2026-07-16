/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.search.relay.icebridge.IceBridgeConstants;
import com.frostwire.util.Hex;

import java.security.KeyPair;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * Signed identity exchanged by FrostWire peers after DHT rendezvous and
 * optionally published as a BEP 46 mutable item by known public key + salt.
 *
 * <p>The canonical signature payload is the bencoded dictionary returned by
 * {@link #toCanonicalEntry()}, which is the full record without {@code sig}.
 *
 * <p>Wire schema versions:
 * <ul>
 *   <li>v1 — node_id, keys, utp_port, first/last_seen</li>
 *   <li>v2 — + rudp_port, role</li>
 *   <li>v3 — + caps</li>
 *   <li>v4 — + ib_ver (IceBridge software version string)</li>
 * </ul>
 */
public final class IdentityRecord {
    /**
     * Current write version (includes IceBridge software version).
     * Readers accept v1–v4 so already-running peers still verify.
     */
    public static final int VERSION = 4;
    public static final int NODE_ID_LENGTH = 20;
    public static final int ED25519_PUB_LENGTH = 32;
    public static final int X25519_PUB_LENGTH = 32;
    public static final int SIGNATURE_LENGTH = 64;
    public static final String BEP46_SALT = "frostwire-identity-v1";

    private static final byte[] ED25519_X509_PREFIX = new byte[]{
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private final byte[] nodeId;
    private final byte[] ed25519Pub;
    private final byte[] x25519Pub;
    private final int utpPort;
    private final int rudpPort;
    private final String role;
    private final long capabilities;
    /** IceBridge software version (empty when unknown / pre-v4 peer). */
    private final String icebridgeVersion;
    /** Wire schema version used for canonical signature domain. */
    private final int wireVersion;
    private final long firstSeen;
    private final long lastSeen;
    private final byte[] signature;

    private IdentityRecord(byte[] nodeId, byte[] ed25519Pub, byte[] x25519Pub,
                           int utpPort, int rudpPort, String role, long capabilities,
                           String icebridgeVersion, int wireVersion,
                           long firstSeen, long lastSeen, byte[] signature) {
        validate(nodeId, ed25519Pub, x25519Pub, signature, firstSeen, lastSeen);
        this.nodeId = nodeId.clone();
        this.ed25519Pub = ed25519Pub.clone();
        this.x25519Pub = x25519Pub.clone();
        this.utpPort = utpPort;
        this.rudpPort = rudpPort;
        this.role = role != null ? role : "BOTH";
        this.capabilities = capabilities;
        this.icebridgeVersion = icebridgeVersion != null ? icebridgeVersion : "";
        this.wireVersion = wireVersion;
        this.firstSeen = firstSeen;
        this.lastSeen = lastSeen;
        this.signature = signature.clone();
    }

    public byte[] nodeId() {
        return nodeId.clone();
    }

    public byte[] ed25519Pub() {
        return ed25519Pub.clone();
    }

    public byte[] x25519Pub() {
        return x25519Pub.clone();
    }

    public int utpPort() {
        return utpPort;
    }

    public int rudpPort() {
        return rudpPort;
    }

    public String role() {
        return role;
    }

    public long capabilities() {
        return capabilities;
    }

    /**
     * IceBridge software version advertised by this peer (e.g. {@code 1.1.0}).
     * Empty string when the peer is pre-v4 or did not announce one.
     */
    public String icebridgeVersion() {
        return icebridgeVersion;
    }

    public int version() {
        return wireVersion;
    }

    public long firstSeen() {
        return firstSeen;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public byte[] signature() {
        return signature.clone();
    }

    public Entry toEntry() {
        Map<String, Object> map = canonicalMap();
        map.put("sig", new Entry(b64(signature)));
        return Entry.fromMap(map);
    }

    public Entry toCanonicalEntry() {
        return Entry.fromMap(canonicalMap());
    }

    public byte[] canonicalBytes() {
        return toCanonicalEntry().bencode();
    }

    public boolean verifySignature() {
        try {
            Signature verifier = IdentityKeys.softwareSignature("Ed25519");
            verifier.initVerify(rawEd25519ToPublicKey(ed25519Pub));
            verifier.update(canonicalBytes());
            return verifier.verify(signature);
        } catch (Exception e) {
            return false;
        }
    }

    public IdentityRecord withUpdatedLastSeen(long ts, PrivateKey privateKey) {
        if (ts < firstSeen) {
            throw new IllegalArgumentException("last_seen must be >= first_seen");
        }
        return signed(nodeId, ed25519Pub, x25519Pub, utpPort, rudpPort, role, capabilities,
                icebridgeVersion, VERSION, firstSeen, ts, privateKey);
    }

    public static IdentityRecord createSigned(byte[] nodeId, KeyPair ed25519, byte[] x25519, int utpPort) {
        return createSigned(nodeId, ed25519, x25519, utpPort, 0, "BOTH");
    }

    public static IdentityRecord createSigned(byte[] nodeId, KeyPair ed25519, byte[] x25519,
                                              int utpPort, int rudpPort, String role) {
        return createSigned(nodeId, ed25519, x25519, utpPort, rudpPort, role,
                NodeCapabilities.fromRole(role));
    }

    public static IdentityRecord createSigned(byte[] nodeId, KeyPair ed25519, byte[] x25519,
                                              int utpPort, int rudpPort, String role, long capabilities) {
        long now = Instant.now().getEpochSecond();
        byte[] rawEd25519 = extractRawEd25519(ed25519.getPublic());
        return signed(nodeId, rawEd25519, x25519, utpPort, rudpPort, role, capabilities,
                IceBridgeConstants.SOFTWARE_VERSION, VERSION, now, now, ed25519.getPrivate());
    }

    public static IdentityRecord fromEntry(Entry entry) {
        Map<String, Entry> map = entry.dictionary();
        int version = (int) map.get("v").integer();
        if (version == 1) {
            IdentityRecord record = new IdentityRecord(
                    Hex.decode(map.get("node_id").string()),
                    b64decode(map.get("ed25519_pub").string()),
                    b64decode(map.get("x25519_pub").string()),
                    (int) map.get("utp_port").integer(),
                    0,
                    "BOTH",
                    NodeCapabilities.DEFAULT_BOTH,
                    "",
                    1,
                    map.get("first_seen").integer(),
                    map.get("last_seen").integer(),
                    b64decode(map.get("sig").string()));
            if (!record.verifySignature()) {
                throw new IllegalArgumentException("Invalid identity signature");
            }
            return record;
        }
        if (version == 2) {
            String role = map.containsKey("role") ? map.get("role").string() : "BOTH";
            IdentityRecord record = new IdentityRecord(
                    Hex.decode(map.get("node_id").string()),
                    b64decode(map.get("ed25519_pub").string()),
                    b64decode(map.get("x25519_pub").string()),
                    (int) map.get("utp_port").integer(),
                    map.containsKey("rudp_port") ? (int) map.get("rudp_port").integer() : 0,
                    role,
                    NodeCapabilities.fromRole(role),
                    "",
                    2,
                    map.get("first_seen").integer(),
                    map.get("last_seen").integer(),
                    b64decode(map.get("sig").string()));
            if (!record.verifySignature()) {
                throw new IllegalArgumentException("Invalid identity signature");
            }
            return record;
        }
        if (version == 3) {
            String role = map.containsKey("role") ? map.get("role").string() : "BOTH";
            long caps = map.containsKey("caps")
                    ? map.get("caps").integer()
                    : NodeCapabilities.fromRole(role);
            IdentityRecord record = new IdentityRecord(
                    Hex.decode(map.get("node_id").string()),
                    b64decode(map.get("ed25519_pub").string()),
                    b64decode(map.get("x25519_pub").string()),
                    (int) map.get("utp_port").integer(),
                    map.containsKey("rudp_port") ? (int) map.get("rudp_port").integer() : 0,
                    role,
                    caps,
                    "",
                    3,
                    map.get("first_seen").integer(),
                    map.get("last_seen").integer(),
                    b64decode(map.get("sig").string()));
            if (!record.verifySignature()) {
                throw new IllegalArgumentException("Invalid identity signature");
            }
            return record;
        }
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported identity version: " + version);
        }
        String role = map.containsKey("role") ? map.get("role").string() : "BOTH";
        long caps = map.containsKey("caps")
                ? map.get("caps").integer()
                : NodeCapabilities.fromRole(role);
        String ibVer = map.containsKey("ib_ver") ? map.get("ib_ver").string() : "";
        IdentityRecord record = new IdentityRecord(
                Hex.decode(map.get("node_id").string()),
                b64decode(map.get("ed25519_pub").string()),
                b64decode(map.get("x25519_pub").string()),
                (int) map.get("utp_port").integer(),
                map.containsKey("rudp_port") ? (int) map.get("rudp_port").integer() : 0,
                role,
                caps,
                ibVer != null ? ibVer : "",
                VERSION,
                map.get("first_seen").integer(),
                map.get("last_seen").integer(),
                b64decode(map.get("sig").string()));
        if (!record.verifySignature()) {
            throw new IllegalArgumentException("Invalid identity signature");
        }
        return record;
    }

    public static byte[] extractRawEd25519(PublicKey ed25519) {
        byte[] encoded = ed25519.getEncoded();
        if (encoded.length == ED25519_PUB_LENGTH) {
            return encoded;
        }
        if (encoded.length == ED25519_X509_PREFIX.length + ED25519_PUB_LENGTH) {
            for (int i = 0; i < ED25519_X509_PREFIX.length; i++) {
                if (encoded[i] != ED25519_X509_PREFIX[i]) {
                    throw new IllegalArgumentException("Unexpected Ed25519 key prefix");
                }
            }
            return Arrays.copyOfRange(encoded, ED25519_X509_PREFIX.length, encoded.length);
        }
        throw new IllegalArgumentException(
                "Unexpected Ed25519 key encoding: " + encoded.length + " bytes");
    }

    private static IdentityRecord signed(byte[] nodeId, byte[] ed25519Pub, byte[] x25519Pub,
                                         int utpPort, int rudpPort, String role, long capabilities,
                                         String icebridgeVersion, int wireVersion,
                                         long firstSeen, long lastSeen, PrivateKey privateKey) {
        validate(nodeId, ed25519Pub, x25519Pub, new byte[SIGNATURE_LENGTH], firstSeen, lastSeen);
        try {
            IdentityRecord unsigned = new IdentityRecord(nodeId, ed25519Pub, x25519Pub,
                    utpPort, rudpPort, role, capabilities, icebridgeVersion, wireVersion,
                    firstSeen, lastSeen, new byte[SIGNATURE_LENGTH]);
            Signature signer = IdentityKeys.softwareSignature("Ed25519");
            signer.initSign(privateKey);
            signer.update(unsigned.canonicalBytes());
            return new IdentityRecord(nodeId, ed25519Pub, x25519Pub, utpPort, rudpPort, role, capabilities,
                    icebridgeVersion, wireVersion, firstSeen, lastSeen, signer.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign identity record", e);
        }
    }

    private static PublicKey rawEd25519ToPublicKey(byte[] raw) throws Exception {
        byte[] encoded = new byte[ED25519_X509_PREFIX.length + raw.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, encoded, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(raw, 0, encoded, ED25519_X509_PREFIX.length, raw.length);
        return IdentityKeys.softwareKeyFactory("Ed25519")
                .generatePublic(new X509EncodedKeySpec(encoded));
    }

    private Map<String, Object> canonicalMap() {
        // Signature domain must match the wire version that was signed.
        // v1: no rudp/role/caps/ib_ver. v2: +rudp+role. v3: +caps. v4: +ib_ver.
        Map<String, Object> map = new TreeMap<>();
        map.put("ed25519_pub", new Entry(b64(ed25519Pub)));
        map.put("first_seen", new Entry(firstSeen));
        if (wireVersion >= 4) {
            map.put("ib_ver", new Entry(icebridgeVersion != null ? icebridgeVersion : ""));
        }
        map.put("last_seen", new Entry(lastSeen));
        map.put("node_id", new Entry(Hex.encode(nodeId)));
        if (wireVersion >= 2) {
            map.put("rudp_port", new Entry((long) rudpPort));
            map.put("role", new Entry(role));
        }
        if (wireVersion >= 3) {
            map.put("caps", new Entry(capabilities));
        }
        map.put("utp_port", new Entry((long) utpPort));
        map.put("v", new Entry((long) wireVersion));
        map.put("x25519_pub", new Entry(b64(x25519Pub)));
        return map;
    }

    private static void validate(byte[] nodeId, byte[] ed25519Pub, byte[] x25519Pub,
                                 byte[] signature, long firstSeen, long lastSeen) {
        if (nodeId == null || nodeId.length != NODE_ID_LENGTH) {
            throw new IllegalArgumentException("nodeId must be 20 bytes");
        }
        if (ed25519Pub == null || ed25519Pub.length != ED25519_PUB_LENGTH) {
            throw new IllegalArgumentException("ed25519 pub must be 32 bytes");
        }
        if (x25519Pub == null || x25519Pub.length != X25519_PUB_LENGTH) {
            throw new IllegalArgumentException("x25519 pub must be 32 bytes");
        }
        if (signature == null || signature.length != SIGNATURE_LENGTH) {
            throw new IllegalArgumentException("signature must be 64 bytes");
        }
        if (firstSeen <= 0) {
            throw new IllegalArgumentException("first_seen must be > 0");
        }
        if (lastSeen < firstSeen) {
            throw new IllegalArgumentException("last_seen must be >= first_seen");
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] b64decode(String value) {
        return Base64.getDecoder().decode(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdentityRecord)) return false;
        IdentityRecord that = (IdentityRecord) o;
        return utpPort == that.utpPort
                && rudpPort == that.rudpPort
                && capabilities == that.capabilities
                && firstSeen == that.firstSeen
                && lastSeen == that.lastSeen
                && role.equals(that.role)
                && icebridgeVersion.equals(that.icebridgeVersion)
                && wireVersion == that.wireVersion
                && Arrays.equals(nodeId, that.nodeId)
                && Arrays.equals(ed25519Pub, that.ed25519Pub)
                && Arrays.equals(x25519Pub, that.x25519Pub)
                && Arrays.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(nodeId);
        result = 31 * result + Arrays.hashCode(ed25519Pub);
        result = 31 * result + Arrays.hashCode(x25519Pub);
        result = 31 * result + utpPort;
        result = 31 * result + rudpPort;
        result = 31 * result + (int) (capabilities ^ (capabilities >>> 32));
        result = 31 * result + role.hashCode();
        result = 31 * result + icebridgeVersion.hashCode();
        result = 31 * result + wireVersion;
        result = 31 * result + (int) (firstSeen ^ (firstSeen >>> 32));
        result = 31 * result + (int) (lastSeen ^ (lastSeen >>> 32));
        result = 31 * result + Arrays.hashCode(signature);
        return result;
    }
}
