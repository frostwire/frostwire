package org.limewire.collection;

import java.io.File;
import java.io.Serializable;
import java.util.Comparator;

/**
 * Compares two Files by file name. Useful for storing Java 1.1.8
 * Files in Java 1.2+ sorted collections classes. This is needed because Files
 * in 1.1.8 do not implement the Comparable interface, unlike Files in 1.2+.
 */
final class FileComparator implements Comparator<File>, Serializable {
    static final long serialVersionUID = 879961226428880051L;

    /** Returns (((File)a).getAbsolutePath()).compareTo(
     *              ((File)b).getAbsolutePath()) 
     *  Typically you'll want to make sure a and b are canonical files,
     *  but that isn't strictly necessary.
     */
    public int compare(File as, File bs) {
        return as.compareTo(bs);
    }
}
