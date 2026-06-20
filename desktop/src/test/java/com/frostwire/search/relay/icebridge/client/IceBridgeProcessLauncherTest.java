/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

import com.frostwire.search.relay.IdentityKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IceBridgeProcessLauncherTest {

    private IceBridgeProcessLauncher launcher;

    @AfterEach
    void stop() {
        if (launcher != null) {
            launcher.close();
        }
    }

    @Test
    void startsAndHealthChecksIceBridgeProcess() throws Exception {
        File jar = new File(System.getProperty("user.dir"), "build/libs/icebridge.jar");
        assertTrue(jar.isFile(), "icebridge.jar must be built first (run icebridgeJar)");

        Path tmp = Files.createTempDirectory("icebridge-test");
        File identityFile = new File(tmp.toFile(), "identity.dat");

        // Pre-generate identity with no PoW so the daemon doesn't spend
        // minutes mining 20-bit difficulty (JDK Ed25519 KeyPairGenerator
        // is ~100x slower than native on some platforms).
        IdentityKeys keys = IdentityKeys.generate(0);
        IdentityKeys.save(keys, identityFile);

        launcher = new IceBridgeProcessLauncher(jar, identityFile, 0, 0, "BOTH");
        launcher.start();

        assertTrue(launcher.isAlive());
        assertTrue(launcher.controlPort() > 0);
        assertTrue(launcher.rudpPort() > 0);

        IceBridgeClient client = launcher.client();
        boolean healthy = false;
        for (int i = 0; i < 300; i++) {
            if (!launcher.isAlive()) {
                fail("IceBridge subprocess exited before becoming healthy");
            }
            if (client.health()) {
                healthy = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(healthy, "IceBridge daemon did not become healthy in time (30s)");
    }
}
