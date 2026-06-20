/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Serializes {@link RemoteSearchRequest} and {@link RemoteSearchResponse}
 * to and from compact byte arrays for transport over IceBridge.
 *
 * <p>Uses Gson to encode the existing {@code toBencodeableMap()} structure as
 * JSON. Both sides share the same map format, so the encoding is symmetric and
 * self-describing — no external schema or bencode codec is required.
 */
public final class SearchPayloadCodec {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    private SearchPayloadCodec() {
    }

    /**
     * Encode a signed search request to UTF-8 JSON bytes.
     */
    public static byte[] encodeRequest(RemoteSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
        return GSON.toJson(request.toBencodeableMap()).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decode a search request from JSON bytes.
     *
     * @return the request, or {@code null} if the bytes are empty or malformed
     */
    public static RemoteSearchRequest decodeRequest(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Map<String, Object> map = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), MAP_TYPE);
            return RemoteSearchRequest.fromBencodeableMap(map);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Encode a signed search response to UTF-8 JSON bytes.
     */
    public static byte[] encodeResponse(RemoteSearchResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("response is null");
        }
        return GSON.toJson(response.toBencodeableMap()).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decode a search response from JSON bytes.
     *
     * @return the response, or {@code null} if the bytes are empty or malformed
     */
    public static RemoteSearchResponse decodeResponse(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Map<String, Object> map = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), MAP_TYPE);
            return RemoteSearchResponse.fromBencodeableMap(map);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Encode a signed catalog browse request to UTF-8 JSON bytes.
     */
    public static byte[] encodeCatalogBrowseRequest(RemoteCatalogBrowseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is null");
        }
        return GSON.toJson(request.toBencodeableMap()).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Decode a catalog browse request from JSON bytes.
     *
     * @return the request, or {@code null} if the bytes are empty or malformed
     */
    public static RemoteCatalogBrowseRequest decodeCatalogBrowseRequest(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Map<String, Object> map = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), MAP_TYPE);
            return RemoteCatalogBrowseRequest.fromBencodeableMap(map);
        } catch (Throwable t) {
            return null;
        }
    }
}
