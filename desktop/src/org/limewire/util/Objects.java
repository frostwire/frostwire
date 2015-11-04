package org.limewire.util;

import java.util.Comparator;

/** A utility class designed to easily perform simple Object checks. */
public class Objects {

    private Objects() {
    }

    /** Throws an exception with the given message if <code>t</code> is null. */
    public static <T> T nonNull(T t, String msg) {
        if (t == null)
            throw new NullPointerException("null: " + msg);
        return t;
    }

    /**
     * @param o1
     * @param o2
     * @return true if both objects are null OR if <code>o1.equals(o2)</code>
     */
    public static boolean equalOrNull(Object o1, Object o2) {
        if (o1 == null && o2 == null) {
            return true;
        }
        if (o1 == null || o2 == null) {
            return false;
        }
        return o1.equals(o2);
    }

    /**
     * Compares tCompares two objects using the compareTo method of o1. This
     * method provides the convenience of null checking the objects, before
     * making the comparison. It sorts null objects as coming before non-null
     * objects.
     */
    public static <T extends Comparable<T>> int compareToNull(T o1, T o2) {
        return compareToNull(o1, o2, true);
    }

    /**
     * Compares two objects using the compareTo method of o1. This method
     * provides the convenience of null checking the objects, before making the
     * comparison. It allows you to choose what order to sort nulls in by use of
     * the nullsFirst variable;
     */
    public static <T extends Comparable<T>> int compareToNull(T o1, T o2, boolean nullsFirst) {
        if (o1 == o2) {
            return 0;
        } else if (o1 == null) {
            return nullsFirst ? -1 : 1;
        } else if (o2 == null) {
            return nullsFirst ? 1 : -1;
        } else {
            return o1.compareTo(o2);
        }
    }
    
    public static int compareToNullIgnoreCase(String o1, String o2, boolean nullsFirst) {
        if (o1 == o2) {
            return 0;
        } else if (o1 == null) {
            return nullsFirst ? -1 : 1;
        } else if (o2 == null) {
            return nullsFirst ? 1 : -1;
        } else {
            return o1.compareToIgnoreCase(o2);
        }
    }

    /**
     * Builds an returns a generic comparator that will compare objects using
     * the Objects.compareToNull method.
     */
    public static <T extends Comparable<T>> Comparator<T> getComparator(final boolean nullsFirst) {
        return new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                return Objects.compareToNull(o1, o2, nullsFirst);
            }
        };
    }
}
