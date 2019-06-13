package com.limegroup.gnutella.gui.bugs;

import com.limegroup.gnutella.util.FrostWireUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * This class maintains protected constants and variables for
 * <tt>LocalServletInfo</tt> and <tt>LocalClientInfo</tt>,
 * the classes that contain the data for the client machine
 * reporting the bug.  This class simply ensures that they are
 * using the same values.  It also handles generating a bug
 * report string, so that both classes will create bug reports
 * that are exactly alike.
 */
abstract class LocalAbstractInfo {
    /**
     * Constant for the OS.
     */
    protected static final String OS = "3";
    String _limewireVersion;
    String _javaVersion;
    String _os;
    String _osVersion;
    String _architecture;
    String _freeMemory;
    String _totalMemory;
    String _bug;
    String _currentThread;
    String _props;
    String _otherThreads;
    String _detail;
    String _javaVendor;
    String _threadCount;
    String _bugName;
    String _fatalError;
    String _peakThreads;
    String _loadAverage;
    String _pendingObjects;
    String _settingsFreeSpace;
    String _incompleteFreeSpace;
    String _heapUsage;
    String _nonHeapUsage;
    private String _userComments;

    /**
     * sets the variable _userComments value to the comments user entered
     *
     * @param comments is the comment user entered
     */
    void addUserComments(String comments) {
        _userComments = comments;
    }

    public SystemInfoWriters getBasicSystemInfo() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        SystemInfoWriters result = new SystemInfoWriters(sw, pw);
        result.pw.println("FrostWire version " + _limewireVersion + " build " + FrostWireUtils.getBuildNumber());
        result.pw.println("Java version " + _javaVersion + " from " + _javaVendor);
        result.pw.println(_os + " v. " + _osVersion + " on " + _architecture);
        result.pw.println("Free/total memory: " + _freeMemory + "/" + _totalMemory);
        result.pw.println();
        return result;
    }

    /**
     * Returns this bug as a bug report.
     */
    public String toBugReport() {
        SystemInfoWriters siw = getBasicSystemInfo();
        StringWriter sw = siw.sw;
        PrintWriter pw = siw.pw;
        if (isFatalError()) {
            pw.println("FATAL ERROR!");
            pw.println();
        }
        pw.println(_bug);
        pw.println();
        if (_detail != null) {
            pw.println("Detail: " + _detail);
            pw.println();
        }
        pw.println("-- class path --");
        //noinspection RegExpRedundantEscape,RegExpSingleCharAlternation
        pw.println(System.getProperty("java.class.path").replaceAll("\\;|\\:", "\n"));
        pw.println("-- listing session information --");
        pw.println("Current thread: " + _currentThread);
        pw.println("Active Threads: " + _threadCount);
        append(pw, "Peak Number of Thread", _peakThreads);
        append(pw, "System Load Avg", _loadAverage);
        append(pw, "Objects Pending GC", _pendingObjects);
        append(pw, "Free Space In Settings", _settingsFreeSpace);
        append(pw, "Free Space In Incomplete", _incompleteFreeSpace);
        append(pw, "Heap Memory Usage", _heapUsage);
        append(pw, "Non-Heap Memory Usage", _nonHeapUsage);
        pw.println();
        if (_otherThreads != null) {
            pw.println("-- listing threads --");
            pw.println(_otherThreads);
            pw.println();
        }
        pw.println(_props);
        pw.println();
        pw.println("**************** Comments from the user ****************\n" + _userComments);
        pw.flush();
        System.out.println(sw.toString());
        return sw.toString();
    }

    /**
     * Appends 'k: v' to pw if v is non null.
     */
    private void append(PrintWriter pw, final String k, final String v) {
        if (v != null) {
            pw.println(k + ": " + v);
        }
    }

    /**
     * Prints the bug's name. This is used primarily in generating the
     * servlet log.
     *
     * @return a <tt>String</tt> containing the bug's name.
     */
    public String toString() {
        return _bugName;
    }

    /**
     * Determines if this was a fatal error.
     */
    private boolean isFatalError() {
        return _fatalError != null && _fatalError.equalsIgnoreCase("true");
    }

    public final class SystemInfoWriters {
        public final StringWriter sw;
        final PrintWriter pw;

        SystemInfoWriters(StringWriter sWriter, PrintWriter pWriter) {
            sw = sWriter;
            pw = pWriter;
        }
    }
}






