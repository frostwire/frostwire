/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.util.Logger;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Verifies that a {@link RemoteSearchResponse} was signed by the expected peer,
 * carries the correct nonce, and is within the timestamp skew window.
 *
 * <p>Shared by {@link OutgoingRelayClient} (direct TCP path) and
 * {@link DistributedSearchPerformer} (IceBridge path) so both transports apply
 * identical verification logic.
 */
public final class SearchResponseVerifier {

    private static final Logger LOG = Logger.getLogger(SearchResponseVerifier.class);

    /**
     * The 12-byte X.509 prefix for a raw Ed25519 public key:
     * {@code SEQUENCE { SEQUENCE { OID Ed25519 }, BIT STRING <pub> }}.
     */
    private static final byte[] ED25519_X509_PREFIX = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    };

    private SearchResponseVerifier() {
    }

    /**
     * Verify a response against the original request and the expected
     * responder's raw Ed25519 public key.
     *
     * <p>Three checks are performed (all must pass):
     * <ol>
     *   <li>Nonce match — the response nonce must equal the request nonce.</li>
     *   <li>Timestamp skew — within {@link RemoteSearchRequest#MAX_TIMESTAMP_SKEW_SEC}.</li>
     *   <li>Ed25519 signature — valid under the expected responder's key.</li>
     * </ol>
     *
     * @return {@code true} if all checks pass, {@code false} otherwise
     */
    public static boolean verify(RemoteSearchResponse response,
                                  RemoteSearchRequest request,
                                  byte[] expectedResponderPub) {
        if (response == null || request == null || expectedResponderPub == null
                || expectedResponderPub.length != 32) {
            return false;
        }
        try {
            if (!Arrays.equals(response.nonce(), request.nonce())) {
                LOG.debug("Response verification failed: nonce mismatch");
                return false;
            }
            long nowSec = System.currentTimeMillis() / 1000L;
            long diff = nowSec - response.timestamp();
            long skew = diff >= 0 ? diff : -diff;
            if (skew > RemoteSearchRequest.MAX_TIMESTAMP_SKEW_SEC) {
                LOG.debug("Response verification failed: timestamp skew " + skew + "s");
                return false;
            }
            PublicKey pub = rawEd25519ToPublicKey(expectedResponderPub);
            Signature verifier = IdentityKeys.softwareSignature("Ed25519");
            verifier.initVerify(pub);
            verifier.update(response.canonicalBytes());
            if (!verifier.verify(response.signature())) {
                LOG.debug("Response verification failed: bad signature");
                return false;
            }
            return true;
        } catch (GeneralSecurityException e) {
            LOG.debug("Response verification threw", e);
            return false;
        }
    }

    /**
     * Reconstruct a JDK {@link PublicKey} from a raw 32-byte Ed25519 public key
     * by prepending the fixed X.509 SubjectPublicKeyInfo prefix.
     */
    public static PublicKey rawEd25519ToPublicKey(byte[] raw) throws GeneralSecurityException {
        byte[] encoded = new byte[ED25519_X509_PREFIX.length + raw.length];
        System.arraycopy(ED25519_X509_PREFIX, 0, encoded, 0, ED25519_X509_PREFIX.length);
        System.arraycopy(raw, 0, encoded, ED25519_X509_PREFIX.length, raw.length);
        return IdentityKeys.softwareKeyFactory("Ed25519")
                .generatePublic(new X509EncodedKeySpec(encoded));
    }
}
