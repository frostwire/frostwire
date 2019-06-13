package org.limewire.util;

/**
 * Thrown upon a version parsing error when the provided version format
 * is malformed.
 */
class VersionFormatException extends Exception {
    /**
     *
     */
    private static final long serialVersionUID = 8633755784769968524L;

    VersionFormatException(String s) {
        super(s);
    }
}