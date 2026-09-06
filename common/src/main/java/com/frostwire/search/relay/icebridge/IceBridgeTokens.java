/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Every token grants full node administration, not tenant-isolated access.
 */
public final class IceBridgeTokens {

    private static final Logger LOG = Logger.getLogger(IceBridgeTokens.class);
    private static final int MAX_FILE_BYTES = 65536;
    private static final int MAX_TOKENS = 128;
    private static final int MAX_TOKEN_LENGTH = 256;

    private final File tokensFile;
    private volatile Set<String> tokens = Collections.emptySet();
    private volatile long lastLoadTime = 0;
    private final Set<String> runtimeTokens = new LinkedHashSet<>();

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
        loadIfNeeded(false);
        if (tokens.size() >= MAX_TOKENS) {
            throw new IllegalStateException("Too many configured tokens");
        }
        String token = generateToken();
        appendToFile(token);
        loadIfNeeded(true); // force reload
        if (!tokens.contains(token)) {
            throw new IllegalStateException("Generated token could not be loaded; review the tokens file");
        }
        // Explicit administrative generation only; never log a token at startup.
        System.out.println(token);

        LOG.info("Generated new bearer token and appended to " + tokensFile);
        return token;
    }

    public boolean isValid(String provided) {
        if (provided == null || provided.isEmpty() || provided.length() > MAX_TOKEN_LENGTH) {
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
     * Add a token for this runtime only (in-process embedders). Does not persist
     * to disk; survives unrelated file reloads. Child launches use a tokens file.
     */
    public synchronized void addRuntimeToken(String token) {
        if (token == null || token.isEmpty()) return;
        if (!validToken(token) || tokens.size() >= MAX_TOKENS) {
            throw new IllegalArgumentException("Invalid or excessive runtime token");
        }
        runtimeTokens.add(token);
        Set<String> mutable = new LinkedHashSet<>(tokens);
        mutable.add(token);
        tokens = Collections.unmodifiableSet(mutable);
    }

    private synchronized void loadIfNeeded(boolean force) {
        if (tokensFile == null || !tokensFile.exists()) {
            tokens = Collections.unmodifiableSet(new LinkedHashSet<>(runtimeTokens));
            lastLoadTime = 0;
            return;
        }
        long currentMod = tokensFile.lastModified();
        if (!force && currentMod == lastLoadTime) {
            return;
        }
        try {
            if (!tokensFile.isFile() || tokensFile.length() > MAX_FILE_BYTES) {
                throw new IOException("Token file exceeds limit or is not a regular file");
            }
            Set<String> loaded = new LinkedHashSet<>(runtimeTokens);
            byte[] bytes = new byte[MAX_FILE_BYTES + 1];
            int count = 0;
            try (FileInputStream input = new FileInputStream(tokensFile)) {
                int read;
                while (count < bytes.length && (read = input.read(bytes, count, bytes.length - count)) != -1) {
                    count += read;
                }
            }
            if (count > MAX_FILE_BYTES) throw new IOException("Token file exceeds 64 KiB");
            for (String line : new String(bytes, 0, count, StandardCharsets.UTF_8).split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (!validToken(line)) throw new IOException("Invalid token entry");
                loaded.add(line);
                if (loaded.size() > MAX_TOKENS) throw new IOException("Too many tokens");
            }
            tokens = Collections.unmodifiableSet(loaded);
            lastLoadTime = currentMod;
            LOG.debug("Loaded " + tokens.size() + " auth token(s) from " + tokensFile);
        } catch (IOException e) {
            LOG.warn("Failed to load auth tokens from " + tokensFile, e);
            tokens = Collections.unmodifiableSet(new LinkedHashSet<>(runtimeTokens));
            lastLoadTime = currentMod;
        }
    }

    private static boolean validToken(String token) {
        if (token.length() > MAX_TOKEN_LENGTH) return false;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c <= ' ' || c > '~') return false;
        }
        return !token.isEmpty();
    }

    private void appendToFile(String token) {
        if (tokensFile == null) {
            throw new IllegalStateException("Token generation requires a tokens file");
        }
        try {
            File parent = tokensFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            if (tokensFile.length() > MAX_FILE_BYTES - 128) {
                throw new IOException("Token file is full");
            }
            // Restrict permissions BEFORE any secret bytes are written.
            tokensFile.createNewFile();
            File absolute = tokensFile.getAbsoluteFile();
            File resolvedParent = absolute.getParentFile().getCanonicalFile();
            if (!tokensFile.isFile()
                    || !new File(resolvedParent, absolute.getName()).equals(tokensFile.getCanonicalFile())) {
                throw new IOException("Token file must be a regular non-symlink file");
            }
            if (!tokensFile.setReadable(false, false) || !tokensFile.setWritable(false, false)
                    || !tokensFile.setExecutable(false, false)
                    || !tokensFile.setReadable(true, true) || !tokensFile.setWritable(true, true)) {
                throw new IOException("Could not restrict token file permissions");
            }
            try (BufferedWriter w = new BufferedWriter(new FileWriter(tokensFile, StandardCharsets.UTF_8, true))) {
                w.write("\n# generated " + java.time.Instant.now() + "\n");
                w.write(token + "\n");
            }
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
