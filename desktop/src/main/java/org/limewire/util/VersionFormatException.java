package org.limewire.util;

/**
 * Thrown upon a version parsing error when the provided version format
 * is malformed.
 */
class VersionFormatException extends Exception {
    /**
     *
     */

    VersionFormatException(String s) {
        super(s);
    }
}