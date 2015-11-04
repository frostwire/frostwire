package com.limegroup.gnutella.gui.bugs;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.limewire.setting.Setting;
import org.limewire.setting.SettingsFactory;
import org.limewire.util.CommonUtils;
import org.limewire.util.OSUtils;
import org.limewire.util.VersionUtils;

import com.limegroup.gnutella.LimeWireCore;
import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GuiCoreMediator;
import com.limegroup.gnutella.settings.LimeProps;
import com.limegroup.gnutella.util.FrostWireUtils;

/**
 * This class encapsulates all of the data for an individual client machine
 * for an individual bug report.<p>
 *
 * This class collects all of the data for the local machine and provides
 * access to that data in url-encoded form.
 */
//2345678|012345678|012345678|012345678|012345678|012345678|012345678|012345678|
public final class LocalClientInfo extends LocalAbstractInfo {
	
	/**
	 * Creates information about this bug from the bug, thread, and detail.
	 */
	public LocalClientInfo(Throwable bug, String threadName, String detail, boolean fatal, SessionInfo sessionInfo) {
	    //Store the basic information ...	    
	    _limewireVersion = FrostWireUtils.getFrostWireVersion();
	    _javaVersion = VersionUtils.getJavaVersion();
        _javaVendor = prop("java.vendor");
	    _os = OSUtils.getOS();
	    _osVersion = prop("os.version");
	    _architecture = prop("os.arch");
	    _freeMemory = "" + Runtime.getRuntime().freeMemory();
	    _totalMemory = "" + Runtime.getRuntime().totalMemory();
	    _peakThreads = "" + ManagementFactory.getThreadMXBean().getPeakThreadCount();
        _loadAverage = getLoadAvg();
        _pendingObjects = "" + ManagementFactory.getMemoryMXBean().getObjectPendingFinalizationCount();
        _heapUsage = "" + ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        _nonHeapUsage = "" + ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        _settingsFreeSpace = getFreeSpace(CommonUtils.getUserSettingsDir());
        _incompleteFreeSpace = "";//getFreeSpace(SharingSettings.INCOMPLETE_DIRECTORY.getValue());
        //_downloadFreeSpace = getFreeSpace(SharingSettings.getSaveDirectory());
        
	    
	    //Store information about the bug and the current thread.
	    StringWriter sw = new StringWriter();
	    PrintWriter pw = new PrintWriter(sw);
	    bug.printStackTrace(pw);
	    pw.flush();
	    _bug = sw.toString();
	    _currentThread = threadName;
	    
	    _bugName = bug.getClass().getName();
	    
	    _fatalError = "" + fatal;
	    
	    //Store the properties.
	    sw = new StringWriter();
	    pw = new PrintWriter(sw);
		Properties props = new Properties();
		// Load the properties from SettingsFactory, excluding
		// FileSettings and FileArraySettings.
		SettingsFactory sf = LimeProps.instance().getFactory();
		synchronized(sf) {
            for(Setting set : sf) {
		        if(!set.isPrivate() && !set.isDefault())
		            props.put(set.getKey(), set.getValueAsString());
            }
        }
//		sf = MojitoProps.instance().getFactory();
//		synchronized(sf) {
//		    for(Setting set : sf) {
//		        if(!set.isPrivate() && !set.isDefault())
//		            props.put(set.getKey(), set.getValueAsString());
//
//		    }
//		}
		// list the properties in the PrintWriter.
		props.list(pw);
		pw.flush();
		_props = sw.toString();
		
		//Store extra debugging information.
		if( GUIMediator.isConstructed() && LimeWireCore.instance() != null && GuiCoreMediator.getLifecycleManager().isLoaded() ) {
//            _upTime = CommonUtils.seconds2time(
//                (int)(sessionInfo.getCurrentUptime()/1000));
//            _upToUp = ""+sessionInfo.getNumUltrapeerToUltrapeerConnections();
//            _upToLeaf = "" + sessionInfo.getNumUltrapeerToLeafConnections();
//            _leafToUp = "" + sessionInfo.getNumLeafToUltrapeerConnections();
//            _oldConnections = "" + sessionInfo.getNumOldConnections();
//            _httpDownloaders = "" +sessionInfo.getNumIndividualDownloaders();
//            _waitingDownloaders = "" + sessionInfo.getNumWaitingDownloads();
//            _acceptedIncoming = "" +sessionInfo.acceptedIncomingConnection();
//            _guessCapable = "" + sessionInfo.isGUESSCapable();
//            _solicitedCapable= ""+sessionInfo.canReceiveSolicited();
//            _externalPort = ""+sessionInfo.getPort();
//            _responseSize = "" + sessionInfo.getContentResponsesSize();
//            _creationCacheSize = "" + sessionInfo.getCreationCacheSize();
//            _vfByteSize = "" + sessionInfo.getDiskControllerByteCacheSize();
//            _vfVerifyingSize = "" + sessionInfo.getDiskControllerVerifyingCacheSize();
//            _bbSize = "" + sessionInfo.getByteBufferCacheSize();
//            _vfQueueSize = "" + sessionInfo.getDiskControllerQueueSize();
//            _waitingSockets = "" + sessionInfo.getNumberOfWaitingSockets();
//            _pendingTimeouts = "" + sessionInfo.getNumberOfPendingTimeouts();

         }
            
        
        //Store the detail, thread counts, and other information.
        _detail = detail;

        Thread[] allThreads = new Thread[Thread.activeCount()];
        int copied = Thread.enumerate(allThreads);
        _threadCount = "" + copied;
        Map<String, Integer> threads = new HashMap<String, Integer>();
        for(int i = 0; i < copied; i++) {
            String name = allThreads[i].getName();
            Integer val = threads.get(name);
            if(val == null)
                threads.put(name, new Integer(1));
            else {
                int num = val.intValue()+1;
                threads.put(name,new Integer(num));
            }
        }
        sw = new StringWriter();
        pw = new PrintWriter(sw);
        for(Map.Entry<String, Integer> info : threads.entrySet())
            pw.println( info.getKey() + ": " + info.getValue());
        pw.flush();
        _otherThreads = sw.toString();
            
	}
	
    /** Uses reflection to retrieve the free space in the partition the file is located on. */
    private static String getFreeSpace(File f) {
        return invoke16Method(f, File.class, "getUsableSpace", Long.class);
    }
    
    /** Uses reflection to get the load average of the computer. */
    private static String getLoadAvg() {
        return invoke16Method(ManagementFactory.getOperatingSystemMXBean(),
                              OperatingSystemMXBean.class,
                             "getSystemLoadAverage",
                             Double.class);
    }
    
    /**
     * Attempts to run the given method if it exists running on Java 1.6 or above.
     * This can only be used for calling methods with no parameters.
     * 
     * @param obj The object to run the method on
     * @param type The class the method can be found on
     * @param method The name of the method to find
     * @param retType The expected return type of the method.
     * @return The result, is some predefined error parameters.
     */
    private static String invoke16Method(Object obj, Class<?> type, String method, Class<?> retType) {
        if (!VersionUtils.isJava16OrAbove())
            return "-1";
        
        try {
            Method m = type.getMethod(method);
            Object ret = m.invoke(obj);
            if (ret == null)
                return "-7";
            if (! (retType.isAssignableFrom(ret.getClass())))
                return "-5";
            return ret.toString();
        } catch (NoSuchMethodException bail) {
            return "-2";
        } catch (IllegalAccessException bail) {
            return "-3";
        } catch (InvocationTargetException bail){
            return "-4";
        } catch (Throwable bad) {
            return "-6";
        }
    }
    
    /** 
	 * Returns the System property with the given name, or
     * "?" if it is unknown. 
	 */
    private final String prop(String name) {
        String value = System.getProperty(name);
        if (value == null) return "?";
        else return value;
    }	

	/** 
	 * Returns a an array of the name/value pairs of this info.
     *
     * @return an array of the name/value pairs of this info.
	 */
	//public final NameValuePair[] getPostRequestParams() {
	public final List<NameValuePair> getPostRequestParams() {
	    List<NameValuePair> params = new LinkedList<NameValuePair>();
        append(params, LIMEWIRE_VERSION, _limewireVersion);
        append(params, JAVA_VERSION, _javaVersion);
        append(params, OS, _os);
        append(params, OS_VERSION, _osVersion);
        append(params, ARCHITECTURE, _architecture);
        append(params, FREE_MEMORY, _freeMemory);
        append(params, TOTAL_MEMORY, _totalMemory);
        append(params, BUG, _bug);
        append(params, CURRENT_THREAD, _currentThread);
        append(params, PROPS, _props);
        append(params, UPTIME, _upTime);
        append(params, CONNECTED, _connected);
        append(params, UP_TO_UP, _upToUp);
        append(params, UP_TO_LEAF, _upToLeaf);
        append(params, LEAF_TO_UP, _leafToUp);
        append(params, OLD_CONNECTIONS, _oldConnections);
        append(params, ULTRAPEER, _ultrapeer);
        append(params, LEAF, _leaf);
        append(params, ACTIVE_UPLOADS, _activeUploads);
        append(params, QUEUED_UPLOADS, _queuedUploads);
        append(params, ACTIVE_DOWNLOADS, _activeDownloads);
        append(params, HTTP_DOWNLOADERS, _httpDownloaders);
        append(params, WAITING_DOWNLOADERS, _waitingDownloaders);
        append(params, ACCEPTED_INCOMING, _acceptedIncoming);
        append(params, SHARED_FILES, _sharedFiles);
        append(params, OTHER_THREADS, _otherThreads);
        append(params, DETAIL, _detail);
        append(params, OTHER_BUG, _otherBug);
        append(params, JAVA_VENDOR, _javaVendor);
        append(params, THREAD_COUNT, _threadCount);
        append(params, BUG_NAME, _bugName);
        append(params, GUESS_CAPABLE, _guessCapable);
        append(params, SOLICITED_CAPABLE,_solicitedCapable);
        append(params, LATEST_SIMPP,_latestSIMPP);
        //append(params, IP_STABLE,_ipStable);
        append(params, PORT_STABLE, _portStable);
        append(params, CAN_DO_FWT, _canDoFWT);
        append(params, LAST_REPORTED_PORT, _lastReportedPort);
        append(params, EXTERNAL_PORT, _externalPort);
        append(params, RECEIVED_IP_PONG, _receivedIpPong);
        append(params, FATAL_ERROR, _fatalError);
        append(params, RESPONSE_SIZE, _responseSize);
        append(params, CT_SIZE, _creationCacheSize);
        append(params, VF_BYTE_SIZE, _vfByteSize);
        append(params, VF_VERIFY_SIZE, _vfVerifyingSize);
        append(params, BB_BYTE_SIZE, _bbSize);
        append(params, VF_QUEUE_SIZE, _vfQueueSize);
        append(params, WAITING_SOCKETS, _waitingSockets);
        append(params, PENDING_TIMEOUTS, _pendingTimeouts);
        append(params, PEAK_THREADS, _peakThreads);
        append(params, SP2_WORKAROUNDS, _sp2Workarounds);
        append(params, LOAD_AVERAGE, _loadAverage);
        append(params, PENDING_GCOBJ, _pendingObjects);
        append(params, SETTINGS_FREE_SPACE, _settingsFreeSpace);
        append(params, INCOMPLETES_FREE_SPACE, _incompleteFreeSpace);
        append(params, DOWNLOAD_FREE_SPACE, _downloadFreeSpace);
        append(params, HEAP_USAGE, _heapUsage);
        append(params, NON_HEAP_USAGE, _nonHeapUsage);
        append(params, SLOT_MANAGER, _slotManager);
        append(params, NUM_SELECTS, _numSelects);
        append(params, NUM_IMMEDIATE_SELECTS, _numImmediateSelects);
        append(params, AVG_SELECT_TIME, _avgSelectTime);
        append(params, USER_COMMENTS, _userComments);
        // APPEND OTHER PARAMETERS HERE.
        
        return params;
        //return params.toArray(new NameValuePair[params.size()]);
	}
    
    /**
     * @return compact printout of the list of parameters
     */
    public String getShortParamList() {
        StringBuilder sb = new StringBuilder(2000);
        for (NameValuePair nvp : getPostRequestParams())
            sb.append(nvp.name).append("=").append(nvp.value).append("\n");
        return sb.toString();
    }
	
	/**
	 * Appends a NameValuePair of k/v to l if v is non-null.
	 */
	private final void append(List<? super NameValuePair> l, final String k, final String v){
	    if( v != null )
	        l.add(new NameValuePair(k, v));
	}
	
    private static final class NameValuePair {

        private final String name;
        private final String value;

        public NameValuePair(final String name, final String value) {
            this.name = name;
            this.value = value;
        }
    }
}
