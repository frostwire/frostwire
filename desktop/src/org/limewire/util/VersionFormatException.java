package org.limewire.util;

/**
 * Thrown upon a version parsing error when the provided version format 
 * is malformed.
 * 
 */
public class VersionFormatException extends Exception {
    
    /**
     * 
     */
    private static final long serialVersionUID = 8633755784769968524L;

    VersionFormatException() {
        super();
    }
    
    VersionFormatException(String s) {
        super(s);
    }
}