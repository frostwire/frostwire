/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Manages bearer tokens for the IceBridge control HTTP API (X-IceBridge-Token header).
 *
 * <p>Tokens are stored in a simple text file (one token per line).
 * Comments start with #. Empty lines are ignored.
 *
 * <p>Supports multiple tokens and hot-reloading when the file changes on disk
 * (no server restart required to add tokens via the generate command).
 */
public final class IceBridgeTokens {

    private static final Logger LOG = Logger.getLogger(IceBridgeTokens.class);

    private final File tokensFile;
    private volatile Set<String> tokens = Collections.emptySet();
    private volatile long lastLoadTime = 0;

    public IceBridgeTokens(File tokensFile) {
        this.tokensFile = tokensFile;
        loadIfNeeded(true);
    }

    /**
     * Generate a new cryptographically secure bearer token (64 hex chars).
     * Prints only the token to stdout (as requested for admin hand-off).
     * Appends it to the tokens file with a timestamp comment.
     * Returns the token.
     */
    public synchronized String generateAndAdd() {
        String token = generateToken();

        // Print ONLY the token to stdout so it can be captured easily by admin
        System.out.println(token);

        appendToFile(token);
        loadIfNeeded(true); // force reload

        LOG.info("Generated new bearer token and appended to " + tokensFile);
        return token;
    }

    public boolean isValid(String provided) {
        if (provided == null || provided.isEmpty()) {
            return false;
        }
        loadIfNeeded(false);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        for (String candidate : tokens) {
            if (constantTimeEquals(providedBytes, candidate.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    public boolean isEmpty() {
        loadIfNeeded(false);
        return tokens.isEmpty();
    }

    /**
     * Returns one configured token for co-located clients. When multiple tokens
     * exist, returns the first loaded entry (stable for the lifetime of this object).
     */
    public synchronized String clientToken() {
        loadIfNeeded(false);
        if (tokens.isEmpty()) {
            return null;
        }
        return tokens.iterator().next();
    }

    /**
     * Add a token for this runtime only (used for legacy --auth-token single value support
     * in local child launches). Does not persist to disk.
     */
    public synchronized void addRuntimeToken(String token) {
        if (token == null || token.isEmpty()) return;
        Set<String> mutable = new LinkedHashSet<>(tokens);
        mutable.add(token);
        tokens = Collections.unmodifiableSet(mutable);
    }

    private void loadIfNeeded(boolean force) {
        if (tokensFile == null || !tokensFile.exists()) {
            if (force) tokens = Collections.emptySet();
            return;
        }
        long currentMod = tokensFile.lastModified();
        if (!force && currentMod <= lastLoadTime) {
            return;
        }
        try {
            Set<String> loaded = new LinkedHashSet<>();
            try (BufferedReader r = new BufferedReader(new FileReader(tokensFile, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    loaded.add(line);
                }
            }
            tokens = Collections.unmodifiableSet(loaded);
            lastLoadTime = currentMod;
            LOG.debug("Loaded " + tokens.size() + " auth token(s) from " + tokensFile);
        } catch (IOException e) {
            LOG.warn("Failed to load auth tokens from " + tokensFile, e);
            if (force) tokens = Collections.emptySet();
        }
    }

    private void appendToFile(String token) {
        if (tokensFile == null) return;
        try {
            File parent = tokensFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (BufferedWriter w = new BufferedWriter(new FileWriter(tokensFile, StandardCharsets.UTF_8, true))) {
                w.write("# generated " + java.time.Instant.now() + "\n");
                w.write(token + "\n");
            }
            // best effort secure perms on unix
            try {
                Files.setPosixFilePermissions(tokensFile.toPath(),
                    java.util.EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            } catch (Exception ignored) {}
        } catch (IOException e) {
            LOG.error("Failed to append generated token to " + tokensFile, e);
            throw new RuntimeException("Could not store token", e);
        }
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Hex.encode(bytes);
    }

    public File getTokensFile() {
        return tokensFile;
    }
}
