package org.limewire.collection;

import java.io.File;
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
 * <code>Comparators</code> are helpful when using a {@link FixedsizePriorityQueue}.
 * <pre>   
    FixedsizePriorityQueue&lt;String&gt; fpq = 
        new FixedsizePriorityQueue&lt;String&gt;(Comparators.stringComparator(), 3);
    fpq.insert("Abby");
    fpq.insert("Bob");
    fpq.insert("Chris");
    System.out.println(fpq);
    String s = fpq.insert("Dan");
    System.out.println("Inserting another String pushes out an element (" + s + ") since the max. size was reached.");
    System.out.println(fpq);

    System.out.println("Minimum element: " + fpq.getMin());
    System.out.println("Maximum element: " + fpq.getMax());
    fpq.extractMax();
    System.out.println(fpq);

    Output:
        [Abby, Bob, Chris]
        Inserting another String pushes out an element (Abby) since the max. size was reached.
        [Bob, Chris, Dan]
        Minimum element: Bob
        Maximum element: Dan
        [Bob, Chris]

 * </pre>
 */
public final class Comparators { 

    /**
     * <code>Comparator</code> for comparing two <code>Integer</code>s.
     */
    private static final Comparator<Integer> INT_COMPARATOR = new IntComparator();

    /**
     * <code>Comparator</code> for comparing two <code>Integer</code>s the opposite way.
     */
    private static final Comparator<Integer> INVERSE_INT_COMPARATOR = new InverseIntComparator();
    
    /**
     * <code>Comparator</code> for comparing two <code>Long</code>s.
     */
    private static final Comparator<Long> LONG_COMPARATOR = new LongComparator();

    /**
     * Inverse <code>Comparator</code> for comparing two <code>Long</code>s.
     */
    private static final Comparator<Long> INVERSE_LONG_COMPARATOR = 
        new InverseLongComparator();
    
    /**
     * <code>Comparator</code> for comparing two <code>String</code>s.
     */
    private static final Comparator<String> STRING_COMPARATOR = new StringComparator();
    
    /**
     * <code>Comparator</code> for comparing two <code>File</code>s.
     */
    private static final Comparator<File> FILE_COMPARATOR = new FileComparator();
    
    /**
     * <code>Comparator</code> for comparing two <code>String</code>s regardless of
     * case.
     */
    private static final Comparator<String> CASE_INSENSITIVE_STRING_COMPARATOR =
        new CaseInsensitiveStringComparator();
    
    private static final Comparator<Double> INVERSE_DOUBLE_COMPARATOR = 
        new Comparator<Double>() {
        public int compare(Double a, Double b) {
            return b.compareTo(a);
        }
    };
    
    /**
     * Ensure that this class cannot be constructed.
     */
    private Comparators() {}
    
    /**
     * Instance assessor for the <code>Comparator</code> for <code>Integer</code>s.
     * This is necessary because the <code>Integer</code> class did not implement 
     * <code>Comparable</code> in Java 1.1.8.  This is an instance because the
     * <code>IntComparator</code> has no state, allowing a single instance to be
     * used whenever a <code>Comparator</code> is needed for <code>Integer</code>s.
     * 
     * @return the <code>IntComparator</code> instance
     */
    public static Comparator<Integer> integerComparator() {
        return INT_COMPARATOR;
    }
    
    public static Comparator<Integer> inverseIntegerComparator() {
        return INVERSE_INT_COMPARATOR;
    }
    
    /**
     * Instance assessor for the <code>Comparator</code> for <code>Long</code>s.  This
     * is necessary because the <code>Long</code> class did not implement 
     * <code>Comparable</code> in Java 1.1.8.  This is an instance because the
     * <code>LongComparator</code> has no state, allowing a single instance to be
     * used whenever a <code>Comparator</code> is needed for <code>Long</code>s.
     * 
     * @return the <code>LongComparator</code> instance
     */
    public static Comparator<Long> longComparator() {
        return LONG_COMPARATOR;
    }

    /**
     * Instance assessor for the inverse <code>Comparator</code> for <code>Long</code>s.  
     * This is necessary because the <code>Long</code> class did not implement 
     * <code>Comparable</code> in Java 1.1.8.  This is an instance because the
     * <code>LongComparator</code> has no state, allowing a single instance to be
     * used whenever a <code>Comparator</code> is needed for <code>Long</code>s.
     * 
     * @return the <code>LongComparator</code> instance
     */
    public static Comparator<Long> inverseLongComparator() {
        return INVERSE_LONG_COMPARATOR;
    }

    public static Comparator<Double> inverseDoubleComparator() {
        return INVERSE_DOUBLE_COMPARATOR;
    }
    
    /**
     * Instance assessor for the <code>Comparator</code> for Strings.  This
     * is necessary because the String class did not implement 
     * <code>Comparable</code> in Java 1.1.8. This is an instance because the
     * <code>StringComparator</code> has no state, allowing a single instance to be
     * used whenever a <code>Comparator</code> is needed for Strings.
     * 
     * @return the <code>StringComparator</code> instance
     */
    public static Comparator<String> stringComparator() {
        return STRING_COMPARATOR;
    }

    /**
     * Instance assessor for the <code>Comparator</code> for <code>File</code>s. This
     * is necessary because the <code>File</code> class did not implement 
     * <code>Comparable</code> in Java 1.1.8. This is an instance because the
     * <code>FileComparator</code> has no state, allowing a single instance to be
     * used whenever a <code>Comparator</code> is needed for <code>File</code>s.
     * 
     * @return the <code>FileComparator</code> instance
     */
    public static Comparator<File> fileComparator() {
        return FILE_COMPARATOR;
    }
    
    /** 
     * Returns a Comparator that uses the natural compareTo methods on the
     * given objects.
     */
    public static <T extends Comparable<T>> Comparator<T> naturalComparator(Class<? extends T> clazz) {
        return new Comparator<T>() {
            public int compare(T o1, T o2) {
                return o1.compareTo(o2);
            }
        };
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
     * Compares two Integers. 
     */
    private static final class IntComparator implements
        Comparator<Integer>, Serializable {
        private static final long serialVersionUID = 830281396810831681L;        
            
        public int compare(Integer o1, Integer o2) {
            return intCompareTo(o1, o2);
        }
    }
    
    /**
     * Compares two Integers the opposite way. 
     */
    private static final class InverseIntComparator implements
    Comparator<Integer> {
        public int compare(Integer o1, Integer o2) {
            return -intCompareTo(o1, o2);
        }
    }

    /**
     * Compares two <code>Long</code>s.  Useful for storing Java
     * 1.1.8 <code>Long</code>s in Java 1.2+ sorted collections classes.  This is 
     * needed because <code>Long</code>s in 1.1.8 do not implement the 
     * <code>Comparable</code> interface, unlike <code>Long</code>s in 1.2+. 
     */
    private static final class LongComparator implements 
        Comparator<Long>, Serializable {
        private static final long serialVersionUID = 226428887996180051L;
     
        public int compare(Long o1, Long o2) {
            return longCompareTo(o1, o2);
        }
    }

    /**
     * Inverse comparison for two <code>Long</code>s.  Useful for storing Java
     * 1.1.8 <code>Long</code>s in Java 1.2+ sorted collections classes.  This is 
     * needed because <code>Long</code>s in 1.1.8 do not implement the 
     * <code>Comparable</code> interface, unlike <code>Long</code>s in 1.2+. 
     */    
    private static final class InverseLongComparator implements 
        Comparator<Long>, Serializable {
        private static final long serialVersionUID = 316426787496198051L;
                                                             
     
        public int compare(Long o1, Long o2) {
            return -longCompareTo(o1, o2);
        }
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
    
    /**
     * Compares two Integer objects numerically.  This function is identical
     * to the Integer compareTo method.  The Integer compareTo method
     * was added in Java 1.2, however, so any app that is 1.1.8 compatible
     * must use this method.
     */
    public static int intCompareTo(Integer thisInt, Integer anotherInt) {
    	int thisVal = thisInt.intValue();
    	int anotherVal = anotherInt.intValue();
    	return (thisVal<anotherVal ? -1 : (thisVal==anotherVal ? 0 : 1));
    }
    
    /**
     * Compares two <code>Long</code> objects numerically.  This function is
     * identical to the Long compareTo method.  The Long compareTo method was
     * added in Java 1.2, however, so any app that is 1.1.8 compatible must use
     * this method.
     *
     * @param firstLong the first <code>Long</code> to be compared.
     * @param secondLong the second <code>Long</code> to be compared.
     * @return the value <code>0</code> if the first <code>Long</code> 
     *  argument is equal to the second <code>Long</code> argument; a value 
     *  less than <code>0</code> if the first <code>Long</code> argument is  
     *  numerically less than the second <code>Long</code>; and a 
     *  value greater than <code>0</code> if the first <code>Long</code>  
     *  argument is numerically greater than the second <code>Long</code> 
     *  argument (signed comparison).
     */
    public static int longCompareTo(Long firstLong, Long secondLong) {
        long firstVal = firstLong.longValue();
        long secondVal = secondLong.longValue();
        return (firstVal<secondVal ? -1 : (firstVal==secondVal ? 0 : 1));
    }
}
