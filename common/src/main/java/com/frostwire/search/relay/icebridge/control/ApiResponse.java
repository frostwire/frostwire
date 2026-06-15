/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge.control;

/**
 * JSON response envelope returned by the IceBridge control API.
 *
 * @param <T> payload type
 */
public final class ApiResponse<T> {

    public boolean ok;
    public String error;
    public T data;

    public ApiResponse() {
    }

    private ApiResponse(boolean ok, T data, String error) {
        this.ok = ok;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> error(String error) {
        return new ApiResponse<>(false, null, error);
    }
}