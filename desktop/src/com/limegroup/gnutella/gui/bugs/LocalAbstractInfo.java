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
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
abstract class LocalAbstractInfo {
    
    /**
     * Constant for the LimeWire version.
     */
    static final String LIMEWIRE_VERSION = "1";
    String _limewireVersion;
    
    /**
     * Constant for the Java version.
     */
    static final String JAVA_VERSION = "2";
    String _javaVersion;
    
    /**
     * Constant for the OS.
     */
    protected static final String OS = "3";
    String _os;
    
    /**
     * Constant for the OS version.
     */
    static final String OS_VERSION = "4";
    String _osVersion;
    
    /**
     * Constant for the architecture.
     */
    static final String ARCHITECTURE = "5";
    String _architecture;
    
    /**
     * Constant for the free memory.
     */
    static final String FREE_MEMORY = "6";
    String _freeMemory;
    
    /**
     * Constant for the total memory.
     */
    static final String TOTAL_MEMORY = "7";
    String _totalMemory;
    
    /**
     * Constant for the exception to report.
     */
    static final String BUG = "8";
    String _bug;
    
    /**
     * Constant for the current thread name.
     */
    static final String CURRENT_THREAD = "9";
    String _currentThread;
    
    /**
     * Constant for the Properties object.
     */
    static final String PROPS = "10";
    String _props;
    
    /**
     * Constant for the uptime.
     */
    static final String UPTIME = "11";
    String _upTime;
    
    /**
     * Constant for the connection status.
     */
    static final String CONNECTED = "12";
    String _connected;
    
    /**
     * Constant for the fileOffset of ultrapeer -> ultrapeer connections.
     */
    static final String UP_TO_UP = "13";
    String _upToUp;
    
    /**
     * Constant for the fileOffset of up -> leaf connections.
     */
    static final String UP_TO_LEAF = "14";
    String _upToLeaf;
    
    /**
     * Constant for the fileOffset of leaf -> up connections.
     */
    static final String LEAF_TO_UP = "15";
    String _leafToUp;
    
    /**
     * Constant for the fileOffset of old connections.
     */
    static final String OLD_CONNECTIONS = "16";
    String _oldConnections;
    
    /**
     * Constant for ultrapeer status.
     */
    static final String ULTRAPEER = "17";
    String _ultrapeer;
    
    /**
     * Constant for leaf status.
     */
    static final String LEAF = "18";
    String _leaf;
    
    /**
     * Constant for the fileOffset of active uploads.
     */
    static final String ACTIVE_UPLOADS = "19";
    String _activeUploads;
    
    /**
     * Constant for the fileOffset of queued uploads.
     */
    static final String QUEUED_UPLOADS = "20";
    String _queuedUploads;
    
    /**
     * Constant for the fileOffset of active downloads.
     */
    static final String ACTIVE_DOWNLOADS = "21";
    String _activeDownloads;
    
    /**
     * Constant for the fileOffset of http downloaders.
     */
    static final String HTTP_DOWNLOADERS = "22";
    String _httpDownloaders;
    
    /**
     * Constant for the fileOffset of waiting downloaders.
     */
    static final String WAITING_DOWNLOADERS = "23";
    String _waitingDownloaders;
    
    /**
     * Constant for whether or not incoming has been accepted.
     */
    static final String ACCEPTED_INCOMING = "24";
    String _acceptedIncoming;
    
    /**
     * Constant for the fileOffset of shared files.
     */
    static final String SHARED_FILES = "25";
    String _sharedFiles;
    
    /**
     * Constant for the other active threads.
     */
    static final String OTHER_THREADS = "26";
    String _otherThreads;
    
    /**
     * Constant for the detail message.
     */
    static final String DETAIL = "27";
    String _detail;
    
    /**
     * Constant for an underlying bug, if any.
     */
    static final String OTHER_BUG = "28";
    String _otherBug;
    
    /**
     * Constant for the java vendor.
     */
    static final String JAVA_VENDOR = "29";
    String _javaVendor;
    
    /**
     * Constant for the total amount of active threads.
     */
    static final String THREAD_COUNT = "30";
    String _threadCount;
    
    /**
     * Constant for the exception's name.
     */
    static final String BUG_NAME = "31";
    String _bugName;
    
    /**
     * Constant for guess capability.
     */
    static final String GUESS_CAPABLE = "32";
    String _guessCapable;
    
    static final String SOLICITED_CAPABLE = "33";
    String _solicitedCapable;
    
    static final String LATEST_SIMPP = "34";
    String _latestSIMPP;
    
//    static final String IP_STABLE = "35";
//    String _ipStable;
    
    static final String PORT_STABLE = "36";
    String _portStable;
    
    static final String CAN_DO_FWT = "37";
    String _canDoFWT;
    
    static final String LAST_REPORTED_PORT = "38";
    String _lastReportedPort;
    
    static final String EXTERNAL_PORT = "39";
    String _externalPort;
    
    static final String RECEIVED_IP_PONG = "40";
    String _receivedIpPong;
    
    static final String FATAL_ERROR = "41";
    String _fatalError;
    
    static final String RESPONSE_SIZE = "42";
    String _responseSize;
    
    static final String CT_SIZE = "43";
    String _creationCacheSize;
    
    static final String VF_VERIFY_SIZE = "44";
    String _vfVerifyingSize;
    
    static final String VF_BYTE_SIZE = "45";
    String _vfByteSize;
    
    static final String BB_BYTE_SIZE = "46";
    String _bbSize;
    
    static final String VF_QUEUE_SIZE = "47";
    String _vfQueueSize;
    
    static final String WAITING_SOCKETS = "48";
    String _waitingSockets;
    
    static final String PENDING_TIMEOUTS = "49";
    String _pendingTimeouts;
    
    static final String PEAK_THREADS = "50";
    String _peakThreads;
    
    static final String SP2_WORKAROUNDS = "51";
    String _sp2Workarounds;
    
    static final String LOAD_AVERAGE = "52";
    String _loadAverage;
    
    static final String PENDING_GCOBJ = "53";
    String _pendingObjects;
    
    static final String SETTINGS_FREE_SPACE = "54";
    String _settingsFreeSpace;
    
    static final String INCOMPLETES_FREE_SPACE = "55";
    String _incompleteFreeSpace;
    
    static final String DOWNLOAD_FREE_SPACE = "56";
    String _downloadFreeSpace;
    
    static final String HEAP_USAGE = "57";
    String _heapUsage;
    
    static final String NON_HEAP_USAGE = "58";
    String _nonHeapUsage;
    
    static final String SLOT_MANAGER = "59";
    String _slotManager;
    
    static final String NUM_SELECTS = "60";
    String _numSelects;
    
    static final String NUM_IMMEDIATE_SELECTS = "61";
    String _numImmediateSelects;
    
    static final String AVG_SELECT_TIME = "62";
    String _avgSelectTime;
    
    static final String USER_COMMENTS = "63";
    String _userComments;
    /**
     * sets the variable _userComments value to the comments user entered
     * @param comments is the comment user entered
     */
    void addUserComments(String comments) {
        _userComments = comments;
    }

    public final class SystemInfoWriters {
        public final StringWriter sw;
        public final PrintWriter pw;
        SystemInfoWriters(StringWriter sWriter, PrintWriter pWriter) {
            sw = sWriter;
            pw = pWriter;
        }
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

        if(isFatalError()) {
            pw.println("FATAL ERROR!");
            pw.println();
        }
		
		pw.println(_bug);
		pw.println();
		
		if( _detail != null ) {
		    pw.println("Detail: " + _detail);
		    pw.println();
		}

        pw.println("-- class path --");
        //noinspection RegExpRedundantEscape
        pw.println(System.getProperty("java.class.path").replaceAll("\\;|\\:","\n"));

        pw.println("-- listing session information --");
        pw.println("Current thread: " + _currentThread);
        pw.println("Active Threads: " + _threadCount);
        append(pw, "Uptime", _upTime);
        append(pw, "Is Connected", _connected);
        append(pw, "Number of Ultrapeer -> Ultrapeer Connections", _upToUp);
        append(pw, "Number of Ultrapeer -> Leaf Connections", _upToLeaf);
        append(pw, "Number of Leaf -> Ultrapeer Connections", _leafToUp);
        append(pw, "Number of Old Connections", _oldConnections);
        append(pw, "Acting as Ultrapeer", _ultrapeer);
        append(pw, "Acting as Shielded Leaf", _leaf);
        append(pw, "Number of Active Uploads", _activeUploads);
        append(pw, "Number of Queued Uploads", _queuedUploads);
    	append(pw, "Number of Active Managed Downloads", _activeDownloads);
    	append(pw, "Number of Active HTTP Downloaders", _httpDownloaders);
        append(pw, "Number of Waiting Downloads", _waitingDownloaders);
    	append(pw, "Received incoming this session", _acceptedIncoming);
    	append(pw, "Number of Shared Files", _sharedFiles);
    	append(pw, "Guess Capable", _guessCapable);
    	append(pw, "Received Solicited UDP",_solicitedCapable);
    	append(pw, "SIMPP version",_latestSIMPP);
    	append(pw, "Port Stable", _portStable);
    	append(pw, "FWT Capable", _canDoFWT);
    	append(pw, "Last Reported Port",_lastReportedPort);
    	append(pw, "External Port", _externalPort);
    	append(pw, "IP Pongs Received",_receivedIpPong);
        append(pw, "Number of Content Response URNs", _responseSize);
        append(pw, "Number of CreationTimeCache URNs", _creationCacheSize);
        append(pw, "VF Byte Cache Size", _vfByteSize);
        append(pw, "VF Verify Cache Size", _vfVerifyingSize);
        append(pw, "VF Queue Size", _vfQueueSize);
        append(pw, "ByteBuffer Cache Size", _bbSize);
        append(pw, "Number of Waiting Sockets", _waitingSockets);
        append(pw, "Number of Pending Timeouts", _pendingTimeouts);
        append(pw, "Peak Number of Thread", _peakThreads);
        append(pw, "Number of SP2 Workarounds", _sp2Workarounds);
        append(pw, "System Load Avg", _loadAverage);
        append(pw, "Objects Pending GC", _pendingObjects);
        append(pw, "Free Space In Settings", _settingsFreeSpace);
        append(pw, "Free Space In Incomplete", _incompleteFreeSpace);
        append(pw, "Free Space In Downloads", _downloadFreeSpace);
        append(pw, "Heap Memory Usage", _heapUsage);
        append(pw, "Non-Heap Memory Usage", _nonHeapUsage);
        append(pw, "SlotManager dump:", _slotManager);
        append(pw, "Number of select calls", _numSelects);
        append(pw, "Number of immediate selects", _numImmediateSelects);
        append(pw, "Average time in select",_avgSelectTime);
        pw.println();

	    if( _otherThreads != null ) {
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
        if( v != null ) {
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
}






