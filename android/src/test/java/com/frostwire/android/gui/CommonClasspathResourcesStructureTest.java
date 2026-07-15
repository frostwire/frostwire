/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.frostwire.crypto.Bip39Mnemonic;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Guards Android packaging of common/ classpath resources (BIP39 wordlist).
 * Without {@code sourceSets.main.resources.srcDir ../common/src/main/resources},
 * seed-phrase UI fails on device with "BIP39 wordlist resource not found".
 */
public final class CommonClasspathResourcesStructureTest {

    private static final String WORDLIST = "/com/frostwire/crypto/bip39_english.txt";

    @Test
    public void bip39WordlistOnClasspath() throws Exception {
        try (InputStream in = Bip39Mnemonic.class.getResourceAsStream(WORDLIST)) {
            assertNotNull("BIP39 wordlist missing from classpath: " + WORDLIST, in);
            int lines = 0;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                while (r.readLine() != null) {
                    lines++;
                }
            }
            assertTrue("expected 2048 BIP39 words, got " + lines, lines == 2048);
        }
    }

    @Test
    public void bip39WordlistUsableViaBip39Mnemonic() {
        // All-zero 32-byte entropy → known 24-word phrase (also proves resource load).
        String mnemonic = Bip39Mnemonic.entropyToMnemonic(new byte[32]);
        assertNotNull(mnemonic);
        assertTrue(Bip39Mnemonic.wordCount(mnemonic) == 24);
    }

    @Test
    public void androidBuildGradlePackagesCommonResources() throws Exception {
        File gradle = new File("build.gradle");
        if (!gradle.isFile()) {
            gradle = new File("android/build.gradle");
        }
        assertTrue("build.gradle not found from " + new File(".").getAbsolutePath(), gradle.isFile());
        byte[] raw = Files.readAllBytes(gradle.toPath());
        String text = new String(raw, StandardCharsets.UTF_8);
        assertTrue(
                "android/build.gradle must include common/src/main/resources for BIP39",
                text.contains("common/src/main/resources"));
    }
}
