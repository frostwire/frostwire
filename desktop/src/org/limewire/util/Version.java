package org.limewire.util;

/**
 * Compares versions using major, minor, service and revision values. You must
 * include a major version in the string, however you may omit the other
 * fields. When applicable, the string version must have a dot between the
 * major, minor and service, however any-non digit separates service from
 * revision.
 * <table>
 * <tr><td><b>String</b></td>  <td><b>Major</b></td><td><b>Minor</b></td><td><b>Service</b></td><td><b>Revision</b></td></tr>
 * <tr><td>1</td>              <td>1</td>              <td>0</td>              <td>0</td>          <td>0</td></tr>
 * <tr><td>1.2</td>            <td>1</td>              <td>2</td>              <td>0</td>          <td>0</td></tr>
 * <tr><td>1.2.3</td>          <td>1</td>              <td>2</td>              <td>3</td>          <td>0</td></tr>
 * <tr><td>1.2.3_4</td>        <td>1</td>              <td>2</td>              <td>3</td>          <td>4</td></tr>
 * <tr><td>1.2.3a</td>         <td>1</td>              <td>2</td>              <td>3</td>          <td>0</td></tr>
 * <tr><td>1.2.3_4a</td>       <td>1</td>              <td>2</td>              <td>3</td>          <td>4</td></tr>
 * <tr><td>1.2.3 A</td>        <td>1</td>              <td>2</td>              <td>3</td>          <td>0</td></tr>
 * <tr><td>1.2.3a4</td>        <td>1</td>              <td>2</td>              <td>3</td>          <td>4</td></tr>
 * </table>
 * <p>
 * Unsupported versions include: <i>1a</i>, <i>1.2a</i>, <i>1.a</i>, <i>1.2.a</i>, etc.
 */
class Version implements Comparable<Version> {
    /**
     * The version string.
     */
    private final String v;
    /**
     * The major version.
     * X in X.Y.Z_r
     */
    private final int major;
    /**
     * The minor version.
     * Y in X.Y.Z_r
     */
    private final int minor;
    /**
     * The service version.
     * Z in X.Y.Z_r
     */
    private final int service;
    /**
     * The revision.
     * r in X.Y.Z_r
     */
    private final int revision;

    /**
     * Constructs a new Version.
     */
    public Version(String s) throws VersionFormatException {
        v = s;
        int[] nums = parse(s);
        major = nums[0];
        minor = nums[1];
        service = nums[2];
        revision = nums[3];
    }

    /**
     * Returns the version.
     */
    private String getVersion() {
        return v;
    }

    /**
     * Returns the version.
     */
    public String toString() {
        return getVersion();
    }

    /**
     * Compares two versions.
     */
    public int compareTo(Version other) {
        int retVal;
        if (major == other.major)
            if (minor == other.minor)
                if (service == other.service)
                    retVal = revision - other.revision;
                else
                    retVal = service - other.service;
            else
                retVal = minor - other.minor;
        else
            retVal = major - other.major;
        return retVal;
    }

    /**
     * Equality.
     */
    public boolean equals(Object o) {
        return o instanceof Version && compareTo((Version) o) == 0;
    }

    /**
     * Parses a version for major/minor/service & revision.
     * Only Major is required.  If Minor, Service or Revision don't exist,
     * they are assumed to be 0.
     */
    private int[] parse(String vers) throws VersionFormatException {
        int major, minor, service, revision;
        int dot1, dot2, lastNum;
        dot1 = vers.indexOf(".");
        if (dot1 != -1) {
            dot2 = vers.indexOf(".", dot1 + 1);
            if (dot2 == -1)
                dot2 = vers.length();
        } else {
            dot1 = vers.length();
            dot2 = -1;
        }
        try {
            major = Integer.parseInt(vers.substring(0, dot1));
        } catch (NumberFormatException nfe) {
            throw new VersionFormatException(vers);
        }
        minor = 0;
        service = 0;
        revision = 0;
        if (dot2 != -1) {
            try {
                minor = Integer.parseInt(vers.substring(dot1 + 1, dot2));
            } catch (NumberFormatException nfe) {
                throw new VersionFormatException(vers);
            }
            try {
                int q = dot2 + 1;
                // advance to the first digit
                while (q < vers.length() && Character.isDigit(vers.charAt(q)))
                    q++;
                lastNum = q;
                if (q <= vers.length())
                    service = Integer.parseInt(vers.substring(dot2 + 1, q));
            } catch (NumberFormatException nfe) {
                throw new VersionFormatException(vers);
            }
            try {
                int q = lastNum + 1;
                // advance to the first digit
                while (q < vers.length() && !Character.isDigit(vers.charAt(q)))
                    q++;
                int p = q;
                // advance to the first non-digit
                while (p < vers.length() && Character.isDigit(vers.charAt(p)))
                    p++;
                if (q < vers.length() && p <= vers.length())
                    revision = Integer.parseInt(vers.substring(q, p));
            } catch (NumberFormatException okay) {
                // not everything will have a revision digit.
            }
        }
        return new int[]{major, minor, service, revision};
    }
}