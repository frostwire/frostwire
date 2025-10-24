package org.limewire.collection;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Provides a way to compare various data types with static methods.
 * <p>
 * <code>Comparators</code> is a utility class that uses the strategy
 * pattern for {@link java.lang.Comparable} instances. Many of these comparators
 * are only necessary because the Java 1.1.8 versions of their classes
 * did not implement the {@link Comparable} interface.
 * <p>
 * <code>Comparators</code> are helpful when using a FixedsizePriorityQueue.
 * <pre>
 * FixedsizePriorityQueue&lt;String&gt; fpq =
 * new FixedsizePriorityQueue&lt;String&gt;(Comparators.stringComparator(), 3);
 * fpq.insert("Abby");
 * fpq.insert("Bob");
 * fpq.insert("Chris");
 * System.out.println(fpq);
 * String s = fpq.insert("Dan");
 * System.out.println("Inserting another String pushes out an element (" + s + ") since the max. size was reached.");
 * System.out.println(fpq);
 *
 * System.out.println("Minimum element: " + fpq.getMin());
 * System.out.println("Maximum element: " + fpq.getMax());
 * fpq.extractMax();
 * System.out.println(fpq);
 *
 * Output:
 * [Abby, Bob, Chris]
 * Inserting another String pushes out an element (Abby) since the max. size was reached.
 * [Bob, Chris, Dan]
 * Minimum element: Bob
 * Maximum element: Dan
 * [Bob, Chris]
 *
 * </pre>
 */
public final class Comparators {
    /**
     * <code>Comparator</code> for comparing two <code>String</code>s regardless of
     * case.
     */
    private static final Comparator<String> CASE_INSENSITIVE_STRING_COMPARATOR =
            new CaseInsensitiveStringComparator();

    /**
     * Ensure that this class cannot be constructed.
     */
    private Comparators() {
    }

    /**
     * Instance assessor for the <code>Comparator</code> for case insensitive
     * <code>String</code>s.  This is an instance because the
     * <code>CaseInsensitiveStringComparator</code> has no state, allowing a single
     * instance to be used whenever a <code>Comparator</code> is needed.
     *
     * @return the <code>CaseInsensitiveStringComparator</code> instance
     */
    public static Comparator<String> caseInsensitiveStringComparator() {
        return CASE_INSENSITIVE_STRING_COMPARATOR;
    }

    /**
     * Compares <code>String</code> objects without regard to case.
     */
    public static final class CaseInsensitiveStringComparator implements
            Comparator<String>, Serializable {
        private static final long serialVersionUID = 263123571237995212L;

        public int compare(String o1, String o2) {
            return o1.compareToIgnoreCase(o2);
        }
    }
}
