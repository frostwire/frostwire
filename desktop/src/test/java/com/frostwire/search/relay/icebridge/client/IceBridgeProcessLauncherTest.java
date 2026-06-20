/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.client;

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

        launcher = new IceBridgeProcessLauncher(jar, identityFile, 0, 0, "BOTH");
        launcher.start();

        assertTrue(launcher.isAlive());
        assertTrue(launcher.controlPort() > 0);
        assertTrue(launcher.rudpPort() > 0);

        // Give the daemon time to boot (JVM + Netty + identity load) and then
        // poll health repeatedly.
        IceBridgeClient client = launcher.client();
        boolean healthy = false;
        for (int i = 0; i < 600; i++) {
            if (!launcher.isAlive()) {
                fail("IceBridge subprocess exited before becoming healthy");
            }
            if (client.health()) {
                healthy = true;
                break;
            }
            Thread.sleep(100);
        }
        assertTrue(healthy, "IceBridge daemon did not become healthy in time (60s)");
    }
}
