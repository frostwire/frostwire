/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay.icebridge;

/**
 * Runtime exception thrown when the IceBridge servent encounters an
 * unrecoverable configuration or protocol error.
 */
public class IceBridgeException extends RuntimeException {

    public IceBridgeException(String message) {
        super(message);
    }

    public IceBridgeException(String message, Throwable cause) {
        super(message, cause);
    }
}