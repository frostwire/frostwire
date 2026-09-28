/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.search.relay.icebridge.MeshEnvelope;
import com.frostwire.search.relay.icebridge.udp.RelayFrame;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paged catalog-browse manifests (manifest {@code v=2}).
 *
 * <p>A catalog answer usually reaches the requester through a forwarder, and a forwarded mesh
 * payload must fit one rUDP datagram ({@link RelayFrame#MAX_APP_PAYLOAD}). A single v1 manifest
 * stops fitting after a handful of torrents, so the responder splits its catalog into pages that
 * each fit. Every page is independently signed over the request nonce and its
 * {@code page}/{@code pages} position, so a relay cannot replay, reorder, or relabel pages.
 */
public final class CatalogManifestPages {

    public static final int VERSION = 2;

    /** Wire budget per page: one relayed datagram, minus the mesh envelope, minus slack. */
    public static final int PAGE_BUDGET_BYTES =
            RelayFrame.MAX_APP_PAYLOAD - MeshEnvelope.HEADER_LENGTH - 16;

    /** Hard cap on pages per catalog (bounds requester memory and responder fan-out). */
    public static final int MAX_PAGES = 64;

    /** Catalog names are informational; long ones are shortened so any single row fits a page. */
    public static final int MAX_NAME_CHARS = 180;

    private static final int NONCE_LENGTH = 32;
    private static final int SIGNATURE_LENGTH = 64;

    private CatalogManifestPages() {
    }

    /** Signing bytes: the v1 manifest layout plus {@code nonce(32) | page(4) | pages(4)}. */
    public static byte[] canonicalBytes(String pubB64, long timestamp,
                                        List<RemoteIndexFetcher.RemoteTorrentEntry> rows,
                                        byte[] nonce, int page, int pages) {
        if (nonce == null || nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException("nonce must be 32 bytes");
        }
        byte[] base = RemoteIndexFetcher.manifestCanonicalBytes(VERSION, pubB64, timestamp, rows);
        return ByteBuffer.allocate(base.length + NONCE_LENGTH + 8)
                .put(base).put(nonce).putInt(page).putInt(pages).array();
    }

    public static byte[] buildJson(String pubB64, long timestamp,
                                   List<RemoteIndexFetcher.RemoteTorrentEntry> rows,
                                   byte[] nonce, int page, int pages, byte[] signature) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("v", VERSION);
        root.put("pub", pubB64);
        root.put("nonce", Base64.getEncoder().encodeToString(nonce));
        root.put("page", page);
        root.put("pages", pages);
        List<Map<String, Object>> rowMaps = new ArrayList<>(rows.size());
        for (RemoteIndexFetcher.RemoteTorrentEntry r : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ih", r.infoHashHex());
            row.put("n", r.name());
            row.put("s", r.sizeBytes());
            row.put("fc", (long) r.fileCount());
            rowMaps.add(row);
        }
        root.put("rows", rowMaps);
        root.put("ts", timestamp);
        root.put("sig", Base64.getEncoder().withoutPadding().encodeToString(signature));
        // No HTML escaping: '=' / '&' / '<' would otherwise cost 6 bytes each.
        return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** Shorten a display name so a single row always fits a page. */
    public static String catalogName(String name) {
        if (name == null) {
            return "";
        }
        if (name.length() <= MAX_NAME_CHARS) {
            return name;
        }
        return name.substring(0, MAX_NAME_CHARS - 1) + "\u2026";
    }

    /**
     * Split {@code entries} greedily into pages whose encoded JSON fits {@link #PAGE_BUDGET_BYTES}.
     * Sizing uses the widest page numbers and a full-length signature, so signed pages never grow
     * past the budget. Always returns at least one (possibly empty) page and at most
     * {@link #MAX_PAGES}; entries beyond that are dropped.
     */
    public static List<List<RemoteIndexFetcher.RemoteTorrentEntry>> paginate(
            String pubB64, long timestamp, List<RemoteIndexFetcher.RemoteTorrentEntry> entries) {
        byte[] sizingNonce = new byte[NONCE_LENGTH];
        byte[] sizingSig = new byte[SIGNATURE_LENGTH];
        List<List<RemoteIndexFetcher.RemoteTorrentEntry>> pages = new ArrayList<>();
        List<RemoteIndexFetcher.RemoteTorrentEntry> current = new ArrayList<>();
        for (RemoteIndexFetcher.RemoteTorrentEntry entry : entries) {
            current.add(entry);
            int size = sizeOf(pubB64, timestamp, current, sizingNonce, sizingSig);
            if (size <= PAGE_BUDGET_BYTES) {
                continue;
            }
            current.remove(current.size() - 1);
            if (!current.isEmpty()) {
                pages.add(current);
                if (pages.size() >= MAX_PAGES) {
                    return pages;
                }
                current = new ArrayList<>();
            }
            // A row that cannot fit even alone would make an undeliverable page: skip it.
            List<RemoteIndexFetcher.RemoteTorrentEntry> alone = Collections.singletonList(entry);
            if (sizeOf(pubB64, timestamp, alone, sizingNonce, sizingSig) <= PAGE_BUDGET_BYTES) {
                current.add(entry);
            }
        }
        if (!current.isEmpty() || pages.isEmpty()) {
            pages.add(current);
        }
        return pages.size() > MAX_PAGES ? new ArrayList<>(pages.subList(0, MAX_PAGES)) : pages;
    }

    private static int sizeOf(String pubB64, long timestamp,
                              List<RemoteIndexFetcher.RemoteTorrentEntry> rows,
                              byte[] sizingNonce, byte[] sizingSig) {
        return buildJson(pubB64, timestamp, rows, sizingNonce, MAX_PAGES - 1, MAX_PAGES,
                sizingSig).length;
    }

    /** Combine received pages (index to rows) in page order. */
    public static List<RemoteIndexFetcher.RemoteTorrentEntry> merge(
            Map<Integer, List<RemoteIndexFetcher.RemoteTorrentEntry>> byPage) {
        if (byPage == null || byPage.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> keys = new ArrayList<>(byPage.keySet());
        Collections.sort(keys);
        List<RemoteIndexFetcher.RemoteTorrentEntry> out = new ArrayList<>();
        for (Integer key : keys) {
            out.addAll(byPage.get(key));
        }
        return out;
    }
}
