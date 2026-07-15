/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.frostwire.crypto.Bip39Mnemonic;
import com.frostwire.util.Hex;
import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdentityLifecycleTest {

    @TempDir
    File tmp;

    @Test
    void resolvePrefersLiveOverDisk() throws Exception {
        IdentityKeys live = IdentityKeys.generate(0);
        IdentityKeys other = IdentityKeys.generate(0);
        File file = new File(tmp, "identity.dat");
        IdentityKeys.save(other, file);
        assertArrayEquals(live.ed25519PubRaw(),
                IdentityLifecycle.resolve(live, file).ed25519PubRaw());
    }

    @Test
    void resolveLoadsFromDiskWhenLiveNull() throws Exception {
        IdentityKeys keys = IdentityKeys.generate(0);
        File file = new File(tmp, "identity.dat");
        IdentityKeys.save(keys, file);
        IdentityKeys loaded = IdentityLifecycle.resolve(null, file);
        assertNotNull(loaded);
        assertArrayEquals(keys.ed25519PubRaw(), loaded.ed25519PubRaw());
    }

    @Test
    void resolveReturnsNullWhenMissing() {
        assertNull(IdentityLifecycle.resolve(null, new File(tmp, "nope.dat")));
    }

    @Test
    void formatGroupedHexGroupsByFour() {
        assertEquals("aabb ccdd", IdentityLifecycle.formatGroupedHex("aabbccdd"));
        assertEquals("aabb", IdentityLifecycle.formatGroupedHex(new byte[] {(byte) 0xaa, (byte) 0xbb}));
    }

    @Test
    void generateAndInstallWritesFileAndBackup() throws Exception {
        File file = new File(tmp, RelayConstants.IDENTITY_FILE);
        IdentityKeys first = IdentityLifecycle.generateAndInstall(file, 0);
        assertTrue(file.isFile());
        IdentityKeys second = IdentityLifecycle.generateAndInstall(file, 0);
        File bak = new File(tmp, RelayConstants.IDENTITY_FILE + ".bak");
        assertTrue(bak.isFile());
        IdentityKeys fromBak = IdentityKeys.load(bak);
        assertArrayEquals(first.ed25519PubRaw(), fromBak.ed25519PubRaw());
        assertArrayEquals(second.ed25519PubRaw(), IdentityKeys.load(file).ed25519PubRaw());
    }

    @Test
    void seedPhraseRoundTrip() throws Exception {
        IdentityKeys keys = IdentityKeys.generate(0);
        File file = new File(tmp, RelayConstants.IDENTITY_FILE);
        IdentityKeys.save(keys, file);
        String phrase = IdentityLifecycle.seedPhrase(keys);
        assertEquals(24, Bip39Mnemonic.wordCount(phrase));
        File file2 = new File(tmp, "restored.dat");
        IdentityKeys restored = IdentityLifecycle.restoreFromSeedPhrase(phrase, file2);
        assertArrayEquals(keys.ed25519PubRaw(), restored.ed25519PubRaw());
        assertArrayEquals(keys.nodeId(), restored.nodeId());
    }

    @Test
    void seedFromPhraseRejectsWrongWordCount() {
        assertThrows(IllegalArgumentException.class,
                () -> IdentityLifecycle.seedFromPhrase("one two three"));
    }

    @Test
    void importExportRoundTrip() throws Exception {
        IdentityKeys keys = IdentityKeys.generate(0);
        File exported = new File(tmp, "export.dat");
        IdentityLifecycle.exportToFile(keys, exported);
        assertTrue(exported.length() >= 64, "export file must not be empty");
        byte[] encoded = IdentityLifecycle.exportBytes(keys);
        assertEquals(exported.length(), encoded.length);
        assertArrayEquals(Files.readAllBytes(exported.toPath()), encoded);
        File installed = new File(tmp, RelayConstants.IDENTITY_FILE);
        IdentityKeys imported = IdentityLifecycle.importFromFile(exported, installed);
        assertArrayEquals(keys.ed25519PubRaw(), imported.ed25519PubRaw());
        assertArrayEquals(keys.ed25519PubRaw(), IdentityKeys.load(installed).ed25519PubRaw());
    }

    @Test
    void encodeRoundTripMatchesSaveLoad() throws Exception {
        IdentityKeys keys = IdentityKeys.generate(0);
        byte[] encoded = IdentityKeys.encode(keys);
        assertTrue(encoded.length >= 64);
        File f = new File(tmp, "enc.dat");
        Files.write(f.toPath(), encoded);
        IdentityKeys loaded = IdentityKeys.load(f);
        assertArrayEquals(keys.ed25519PubRaw(), loaded.ed25519PubRaw());
        assertArrayEquals(keys.nodeId(), loaded.nodeId());
    }

    @Test
    void summaryTextContainsNodeId() throws Exception {
        IdentityKeys keys = IdentityKeys.generate(0);
        String summary = IdentityLifecycle.summaryText(keys, 3, 7);
        assertTrue(summary.contains(Hex.encode(keys.nodeId())));
        assertTrue(summary.contains("Karma: 3"));
        assertTrue(summary.contains("Shared torrents: 7"));
    }

    @Test
    void difficultyHelpers() throws Exception {
        IdentityKeys keys = IdentityKeys.generate(0);
        assertEquals(IdentityKeys.countLeadingZeroBits(keys.nodeId()),
                IdentityLifecycle.difficultyBits(keys));
    }
}
