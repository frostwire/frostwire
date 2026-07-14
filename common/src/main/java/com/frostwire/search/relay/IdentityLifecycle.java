/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.crypto.Bip39Mnemonic;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Objects;

/**
 * Shared identity lifecycle operations used by desktop and Android UI.
 *
 * <p>Pure logic — no Swing/Android. Callers own path resolution
 * ({@link RelayConstants#identityFile} on desktop, libtorrent home on Android)
 * and any process/stack restart after install.
 */
public final class IdentityLifecycle {

    private static final Logger LOG = Logger.getLogger(IdentityLifecycle.class);

    /** BIP39 word count for a 32-byte Ed25519 seed. */
    public static final int SEED_PHRASE_WORDS = 24;

    private IdentityLifecycle() {
    }

    /**
     * Prefer live wired identity; otherwise load from {@code identityFile}
     * if present and non-empty.
     */
    public static IdentityKeys resolve(IdentityKeys live, File identityFile) {
        if (live != null) {
            return live;
        }
        if (identityFile == null || !identityFile.exists() || identityFile.length() == 0) {
            return null;
        }
        try {
            return IdentityKeys.load(identityFile);
        } catch (Throwable t) {
            LOG.warn("Failed to load identity from " + identityFile.getAbsolutePath(), t);
            return null;
        }
    }

    /**
     * Format raw bytes as grouped hex (4-char groups) for display.
     */
    public static String formatGroupedHex(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        return formatGroupedHex(Hex.encode(raw));
    }

    public static String formatGroupedHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(hex.length() + hex.length() / 4);
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) {
                sb.append(' ');
            }
            int end = Math.min(i + 4, hex.length());
            sb.append(hex, i, end);
        }
        return sb.toString();
    }

    /**
     * Generate a new PoW identity and install it at {@code identityFile},
     * backing up any existing file to {@code identity.dat.bak}.
     *
     * @return the newly generated keys
     */
    public static IdentityKeys generateAndInstall(File identityFile, int difficulty)
            throws IOException, GeneralSecurityException {
        if (difficulty < 0) {
            throw new IllegalArgumentException("difficulty must be >= 0");
        }
        IdentityKeys keys = IdentityKeys.generate(difficulty);
        install(keys, identityFile, true);
        return keys;
    }

    /**
     * 24-word BIP39 mnemonic for the identity's Ed25519 seed.
     */
    public static String seedPhrase(IdentityKeys identity) {
        Objects.requireNonNull(identity, "identity");
        return Bip39Mnemonic.entropyToMnemonic(identity.ed25519Seed());
    }

    /**
     * Parse, validate (exactly 24 words), restore keys from mnemonic, and install.
     *
     * @return installed keys
     */
    public static IdentityKeys restoreFromSeedPhrase(String rawMnemonic, File identityFile)
            throws IOException, GeneralSecurityException {
        byte[] seed = seedFromPhrase(rawMnemonic);
        IdentityKeys keys = IdentityKeys.fromSeed(seed);
        install(keys, identityFile, true);
        return keys;
    }

    /**
     * Normalize and validate a user-entered seed phrase; return 32-byte entropy.
     *
     * @throws IllegalArgumentException if phrase is invalid or not 24 words
     */
    public static byte[] seedFromPhrase(String rawMnemonic) {
        String mnemonic = Bip39Mnemonic.normalize(rawMnemonic);
        if (mnemonic == null || mnemonic.isEmpty()) {
            throw new IllegalArgumentException("seed phrase is empty");
        }
        int words = Bip39Mnemonic.wordCount(mnemonic);
        if (words != SEED_PHRASE_WORDS) {
            throw new IllegalArgumentException(
                    "Seed phrase must contain exactly " + SEED_PHRASE_WORDS
                            + " words; found " + words + ".");
        }
        Bip39Mnemonic.validate(mnemonic);
        return Bip39Mnemonic.mnemonicToEntropy(mnemonic);
    }

    /**
     * Load identity from {@code sourceFile} and install at {@code identityFile}.
     */
    public static IdentityKeys importFromFile(File sourceFile, File identityFile)
            throws IOException, GeneralSecurityException {
        if (sourceFile == null || !sourceFile.isFile()) {
            throw new IllegalArgumentException("sourceFile missing");
        }
        IdentityKeys keys = IdentityKeys.load(sourceFile);
        install(keys, identityFile, true);
        return keys;
    }

    /**
     * Write identity bytes to {@code destFile} (creates parent dirs).
     */
    public static void exportToFile(IdentityKeys identity, File destFile) throws IOException {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(destFile, "destFile");
        File parent = destFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create directory: " + parent);
        }
        IdentityKeys.save(identity, destFile);
    }

    /**
     * Install keys at {@code identityFile}, optionally backing up the previous file.
     */
    public static void install(IdentityKeys keys, File identityFile, boolean backupExisting)
            throws IOException {
        Objects.requireNonNull(keys, "keys");
        if (identityFile == null) {
            throw new IllegalArgumentException("identityFile is null");
        }
        File parent = identityFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create identity directory: " + parent);
        }
        if (backupExisting && identityFile.exists() && identityFile.length() > 0 && parent != null) {
            File bak = new File(parent, RelayConstants.IDENTITY_FILE + ".bak");
            copyFile(identityFile, bak);
            LOG.info("Backed up identity to " + bak.getAbsolutePath());
        }
        IdentityKeys.save(keys, identityFile);
        LOG.info("Identity installed at " + identityFile.getAbsolutePath()
                + " nodeId=" + Hex.encode(keys.nodeId()));
    }

    /**
     * Difficulty display helper: leading zero bits of node ID.
     */
    public static int difficultyBits(IdentityKeys identity) {
        Objects.requireNonNull(identity, "identity");
        return IdentityKeys.countLeadingZeroBits(identity.nodeId());
    }

    public static boolean meetsDifficultyRequirement(IdentityKeys identity) {
        return difficultyBits(identity) >= KarmaConstants.IDENTITY_DIFFICULTY;
    }

    /**
     * Multi-line identity summary for clipboard export.
     */
    public static String summaryText(IdentityKeys identity, long karmaEndorsements, int sharedTorrents) {
        Objects.requireNonNull(identity, "identity");
        return "Node ID: " + Hex.encode(identity.nodeId())
                + "\nFingerprint: " + Hex.encode(identity.ed25519PubRaw())
                + "\nPublic Key: " + Hex.encode(identity.ed25519PubRaw())
                + "\nKarma: " + karmaEndorsements + " endorsements"
                + "\nDifficulty: " + difficultyBits(identity) + " bits"
                + "\nShared torrents: " + sharedTorrents;
    }

    private static void copyFile(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        }
    }
}
