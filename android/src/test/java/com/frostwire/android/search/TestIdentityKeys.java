/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.search;

import com.frostwire.search.relay.IdentityKeys;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/** Builds software Ed25519 identities for unit tests without loading the jlibtorrent native library. */
final class TestIdentityKeys {
    private TestIdentityKeys() {}

    static IdentityKeys fromSeedWithoutNative(byte[] rawSeed, byte[] rawPub) throws Exception {
        byte[] pkcs8Prefix = {
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
        };
        byte[] pkcs8 = new byte[48];
        System.arraycopy(pkcs8Prefix, 0, pkcs8, 0, 16);
        System.arraycopy(rawSeed, 0, pkcs8, 16, 32);
        byte[] x509Prefix = {
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        };
        byte[] x509 = new byte[44];
        System.arraycopy(x509Prefix, 0, x509, 0, 12);
        System.arraycopy(rawPub, 0, x509, 12, 32);
        KeyFactory kf = IdentityKeys.softwareKeyFactory("Ed25519");
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(x509));
        KeyPair edPair = new KeyPair(pub, priv);
        Method generator =
                IdentityKeys.class.getDeclaredMethod("generateX25519KeyPair");
        generator.setAccessible(true);
        KeyPair xPair = (KeyPair) generator.invoke(null);
        Constructor<IdentityKeys> ctor =
                IdentityKeys.class.getDeclaredConstructor(KeyPair.class, KeyPair.class);
        ctor.setAccessible(true);
        return ctor.newInstance(edPair, xPair);
    }
}
