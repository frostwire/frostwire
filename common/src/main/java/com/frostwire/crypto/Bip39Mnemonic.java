/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.crypto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

/**
 * BIP39 mnemonic encoding/decoding for the standard English wordlist.
 *
 * <p>Converts raw entropy (16-32 bytes, multiple of 4) to a sequence of
 * 12-24 words and back, using the SHA-256 checksum defined by BIP39.
 * Designed to back up and restore the 32-byte Ed25519 identity seed as
 * a 24-word recovery phrase.
 *
 * <p>The wordlist is loaded lazily from the classpath resource
 * {@code /com/frostwire/crypto/bip39_english.txt} and cached for the
 * lifetime of the JVM.
 */
public final class Bip39Mnemonic {

    private static final int WORDLIST_SIZE = 2048;
    private static final String RESOURCE_PATH = "/com/frostwire/crypto/bip39_english.txt";

    private static volatile String[] words;
    private static volatile Map<String, Integer> wordIndex;

    private Bip39Mnemonic() {
    }

    public static String entropyToMnemonic(byte[] entropy) {
        if (entropy == null) {
            throw new IllegalArgumentException("entropy is null");
        }
        int entBits = entropy.length * 8;
        if (entBits < 128 || entBits > 256 || entBits % 32 != 0) {
            throw new IllegalArgumentException(
                    "entropy must be 16/20/24/28/32 bytes");
        }
        int csBits = entBits / 32;
        int wordCount = (entBits + csBits) / 11;

        byte[] hash = sha256(entropy);
        byte[] combined = new byte[entropy.length + 1];
        System.arraycopy(entropy, 0, combined, 0, entropy.length);
        combined[entropy.length] = hash[0];

        String[] list = wordlist();
        StringBuilder sb = new StringBuilder(wordCount * 10);
        for (int i = 0; i < wordCount; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(list[extractBits(combined, i * 11, 11)]);
        }
        return sb.toString();
    }

    public static byte[] mnemonicToEntropy(String mnemonic) {
        return decode(mnemonic);
    }

    public static void validate(String mnemonic) {
        decode(mnemonic);
    }

    private static byte[] decode(String mnemonic) {
        if (mnemonic == null) {
            throw new IllegalArgumentException("mnemonic is null");
        }
        String trimmed = mnemonic.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("mnemonic is empty");
        }
        String[] parts = trimmed.split("\\s+");
        int wordCount = parts.length;
        if (wordCount != 12 && wordCount != 15
                && wordCount != 18 && wordCount != 21 && wordCount != 24) {
            throw new IllegalArgumentException(
                    "invalid word count: " + wordCount);
        }

        Map<String, Integer> idx = wordIndexMap();
        int totalBits = wordCount * 11;
        int entBits = totalBits * 32 / 33;
        int csBits = totalBits / 33;
        int entBytes = entBits / 8;

        byte[] combined = new byte[(totalBits + 7) / 8];
        for (int i = 0; i < wordCount; i++) {
            Integer wi = idx.get(parts[i]);
            if (wi == null) {
                throw new IllegalArgumentException(
                        "invalid word: " + parts[i]);
            }
            int value = wi;
            for (int j = 0; j < 11; j++) {
                int bitPos = i * 11 + j;
                int bytePos = bitPos / 8;
                int bitInByte = 7 - (bitPos % 8);
                int bit = (value >> (10 - j)) & 1;
                combined[bytePos] |= (bit << bitInByte);
            }
        }

        byte[] entropy = new byte[entBytes];
        System.arraycopy(combined, 0, entropy, 0, entBytes);

        byte[] hash = sha256(entropy);
        int csFromData = extractBits(combined, entBits, csBits);
        int csExpected = extractBits(hash, 0, csBits);
        if (csFromData != csExpected) {
            throw new IllegalArgumentException("invalid checksum");
        }
        return entropy;
    }

    private static int extractBits(byte[] data, int bitOffset, int bitCount) {
        int value = 0;
        for (int i = 0; i < bitCount; i++) {
            int bitPos = bitOffset + i;
            int bytePos = bitPos / 8;
            int bitInByte = 7 - (bitPos % 8);
            int bit = (data[bytePos] >> bitInByte) & 1;
            value = (value << 1) | bit;
        }
        return value;
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String[] wordlist() {
        if (words == null) {
            loadWordlist();
        }
        return words;
    }

    private static Map<String, Integer> wordIndexMap() {
        if (wordIndex == null) {
            loadWordlist();
        }
        return wordIndex;
    }

    private static synchronized void loadWordlist() {
        if (words != null) {
            return;
        }
        try (InputStream is = Bip39Mnemonic.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new IllegalStateException(
                        "BIP39 wordlist resource not found: " + RESOURCE_PATH);
            }
            String[] list = new String[WORDLIST_SIZE];
            Map<String, Integer> map = new HashMap<>(WORDLIST_SIZE * 2);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int i = 0;
                while ((line = reader.readLine()) != null) {
                    String w = line.trim();
                    if (w.isEmpty()) {
                        continue;
                    }
                    if (i >= WORDLIST_SIZE) {
                        throw new IllegalStateException(
                                "wordlist exceeds 2048 entries");
                    }
                    list[i] = w;
                    map.put(w, i);
                    i++;
                }
                if (i != WORDLIST_SIZE) {
                    throw new IllegalStateException(
                            "wordlist has " + i + " words, expected " + WORDLIST_SIZE);
                }
            }
            words = list;
            wordIndex = map;
        } catch (IOException e) {
            throw new IllegalStateException("failed to load BIP39 wordlist", e);
        }
    }
}
