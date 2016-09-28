package com.limegroup.gnutella.gui;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

import org.limewire.concurrent.ThreadExecutor;
import org.limewire.util.VersionUtils;

import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.bugs.DeadlockBugManager;
import com.limegroup.gnutella.gui.bugs.DeadlockException;

/** Simple class to help monitor deadlocking. */
public class DeadlockSupport {
    
    private static Logger LOG = Logger.getLogger(DeadlockSupport.class);
    
    /** 
     * How often to check for deadlocks. 
     * 
     * This class doubles as a workaround for bug_id=6435126,
     * so it doesn't use a multiple of 10 for the sleep interval.
     */
    private static final int DEADLOCK_CHECK_INTERVAL = 3001;

    public static void startDeadlockMonitoring() {
        Thread t = ThreadExecutor.newManagedThread(new Runnable() {
            public void run() {
                while(true) {
                    try {
                        Thread.sleep(DEADLOCK_CHECK_INTERVAL);
                    } catch (InterruptedException ignored) {}
                    //LOG.info("deadlock check start");
                    long [] ids = findDeadlockedThreads(ManagementFactory.getThreadMXBean());
                    
                    if (ids == null) {
                        //LOG.info("no deadlocks found");
                        continue;
                    }
                    
                    StringBuilder sb = new StringBuilder("Deadlock Report:\n");
                    StackTraceElement[] firstStackTrace = null;
                    ThreadInfo[] allThreadInfo = getThreadInfo(ids);
                    for (ThreadInfo info : allThreadInfo) {
                        sb.append("\"" + info.getThreadName() + "\" (id=" + info.getThreadId() + ")");
                        sb.append(" " + info.getThreadState() + " on " + info.getLockName() + " owned by ");
                        sb.append("\"" + info.getLockOwnerName() + "\" (id=" + info.getLockOwnerId() + ")");
                        if (info.isSuspended())
                            sb.append(" (suspended)");
                        if (info.isInNative())
                            sb.append(" (in native)");
                        sb.append("\n");
                        StackTraceElement[] trace = info.getStackTrace();
                        if(firstStackTrace == null)
                            firstStackTrace = trace;
                        for(int i = 0; i < trace.length; i++) {
                            sb.append("\tat " + trace[i].toString() + "\n");
                            if(i == 0)
                                addLockInfo(info, sb);
                            addMonitorInfo(info, sb, i);
                        }
                        
                        addLockedSynchronizers(info, sb);
                        
                        sb.append("\n");
                    }
                    
                    DeadlockException deadlock = new DeadlockException();
                    // Redirect the stack trace to separate deadlock reports.
                    if(firstStackTrace != null)
                        deadlock.setStackTrace(firstStackTrace);
                    
                    DeadlockBugManager.handleDeadlock(deadlock, Thread.currentThread().getName(), sb.toString());
                    return;
                }
            }
        });
        t.setDaemon(true);
        t.setName("Deadlock Detection Thread");
        t.start();
    }
    
    /** Uses reflection to add locked synchronizers data. */
    private static void addLockedSynchronizers(ThreadInfo info, StringBuilder sb) {
        if(VersionUtils.isJava16OrAbove()) {
            try {
                Method m = ThreadInfo.class.getMethod("getLockedSynchronizers");
                Object o = m.invoke(info);
                if(o != null) {
                    int length = Array.getLength(o);
                    if(length > 0) {
                        sb.append("\n\tNumber of locked synchronizers = " + length + "\n");
                        for(int i = 0; i < length; i++)
                            sb.append("\t- " + Array.get(o, i) + "\n");
                    }
                }
            } catch(Throwable t) {
                LOG.info("Error retrieving locked synchronizers", t);
            }
        }
    }
    
    /** Uses reflection to add more specific locking details. */
    private static void addMonitorInfo(ThreadInfo info, StringBuilder sb, int stackDepth) {
        if(VersionUtils.isJava16OrAbove()) {
            try {
                Method m = ThreadInfo.class.getMethod("getLockedMonitors");
                Object o = m.invoke(info);
                if(o != null) {
                    Class<?> monitorInfoClass = Class.forName("java.lang.management.MonitorInfo");
                    int length = Array.getLength(o);
                    for(int i = 0; i < length; i++) {
                        Object mi = Array.get(o, i);
                        Method depthMethod = monitorInfoClass.getMethod("getLockedStackDepth");
                        Object depth = depthMethod.invoke(mi);
                        if(depth != null && depth.equals(Integer.valueOf(stackDepth)))
                            sb.append("\t-  locked " + mi + "\n");
                    }
                }
            } catch(Throwable t) {
                LOG.info("Error retrieving monitor info", t);
            }
        }
    }
    
    /** Uses reflection to add the LockInfo data to the report. */
    private static void addLockInfo(ThreadInfo info, StringBuilder sb) {
        if(VersionUtils.isJava16OrAbove()) {
            try {
                Method m = ThreadInfo.class.getMethod("getLockInfo");
                Object o = m.invoke(info);
                if(o != null) {
                    Thread.State ts = info.getThreadState();
                    switch (ts) {
                        case BLOCKED: 
                            sb.append("\t-  blocked on " + o + "\n");
                            break;
                        case WAITING:
                        case TIMED_WAITING:
                            sb.append("\t-  waiting on " + o + "\n");
                            break;
                        default:
                    }
                }
            } catch(Throwable t) {
                LOG.info("Error calling getLockInfo", t);
            }
        }
    }

    /** Uses reflection to run the Java 1.6 method 'findDeadlockedThreads' on a bean. */
    private static long[] findDeadlockedThreads(ThreadMXBean bean) {
        if(VersionUtils.isJava16OrAbove()) {
            try {
                Method m = ThreadMXBean.class.getMethod("findDeadlockedThreads");
                Object o = m.invoke(bean);
                if(o instanceof long[] || o == null)
                    return (long[])o;
            } catch(Throwable t) {
                LOG.error("Error calling findDeadlockedthreads", t);
            }
        }
        
        // fallback to the monitor'd one if anything bad happens.
        return bean.findMonitorDeadlockedThreads();
    }
    
    /** Uses reflection to get ThreadInfo with monitors & synchronizers. */
    private static ThreadInfo[] getThreadInfo(long[] ids) {
        if(VersionUtils.isJava16OrAbove()) {
            try {
                Method m = ThreadMXBean.class.getDeclaredMethod("getThreadInfo", new Class[] { long[].class, boolean.class, boolean.class } );
                Object o = m.invoke(ManagementFactory.getThreadMXBean(), new Object[] { ids, true, true } );
                return (ThreadInfo[])o;
            } catch (Throwable t) {
                LOG.info("Error retrieving detailed thread info", t);
            }
        }
        
        //fallback to retrieving info w/o monitor & synchronizer info
        return ManagementFactory.getThreadMXBean().getThreadInfo(ids, Integer.MAX_VALUE);
    }
}
