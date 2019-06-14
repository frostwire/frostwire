package org.limewire.util;

/**
 * Provides methods to get the current JVM version and compare Java versions.
 */
public class VersionUtils {
    private VersionUtils() {
    }

    /**
     * Utility methods for determing if we're atleast Java 1.6.
     */
    public static boolean isJava16OrAbove() {
        return isJavaVersionOrAbove("1.6");
    }

    /**
     * Determines if Java is above or equal to the given version.
     */
    private static boolean isJavaVersionOrAbove(String version) {
        try {
            Version java = new Version(getJavaVersion());
            Version given = new Version(version);
            return java.compareTo(given) >= 0;
        } catch (VersionFormatException vfe) {
            return false;
        }
    }

    /**
     * Returns the version of java we're using.
     */
    public static String getJavaVersion() {
        return System.getProperty("java.version");
    }
}
