package com.limegroup.gnutella;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import com.frostwire.bittorrent.BTEngine;
import org.limewire.concurrent.ThreadExecutor;
import org.limewire.listener.EventListener;
import org.limewire.listener.EventListenerList;
import org.limewire.service.ErrorService;
import org.limewire.setting.SettingsGroupManager;
import org.limewire.util.OSUtils;
import org.limewire.util.SystemUtils;

import com.frostwire.logging.Logger;
import com.limegroup.gnutella.settings.ApplicationSettings;

public class LifecycleManagerImpl implements LifecycleManager {
    
    private static final Logger LOG = Logger.getLogger(LifecycleManagerImpl.class);
   
    private final AtomicBoolean preinitializeBegin = new AtomicBoolean(false);
    private final AtomicBoolean preinitializeDone = new AtomicBoolean(false);
    private final AtomicBoolean backgroundBegin = new AtomicBoolean(false);
    private final AtomicBoolean backgroundDone = new AtomicBoolean(false);
    private final AtomicBoolean startBegin = new AtomicBoolean(false);
    private final AtomicBoolean startDone = new AtomicBoolean(false);
    private final AtomicBoolean shutdownBegin = new AtomicBoolean(false);
    private final AtomicBoolean shutdownDone = new AtomicBoolean(false);
    
    private final CountDownLatch startLatch = new CountDownLatch(1);
    
    private static enum State { NONE, STARTING, STARTED, STOPPED };

    private final LimeCoreGlue limeCoreGlue;
    
    /** A list of items that require running prior to shutting down LW. */
    private final List<Thread> SHUTDOWN_ITEMS =  Collections.synchronizedList(new LinkedList<Thread>());
    /** The time when this finished starting. */
    private long startFinishedTime;
    
    private final EventListenerList<LifeCycleEvent> listenerList;
    
    public static enum LifeCycleEvent {
        STARTING, STARTED, SHUTINGDOWN, SHUTDOWN
    }

    
    /**/
    public LifecycleManagerImpl(
            LimeCoreGlue limeCoreGlue) {
        
        this.listenerList = new EventListenerList<LifeCycleEvent>();

        this.limeCoreGlue = limeCoreGlue;
    }
    /**/
    
    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#isLoaded()
     */
    public boolean isLoaded() {
        State state = getCurrentState();
        return state == State.STARTED || state == State.STARTING;
    }
    
    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#isStarted()
     */
    public boolean isStarted() {
        State state = getCurrentState();
        return state == State.STARTED || state == State.STOPPED;
    }
    
    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#installListeners()
     */
    public void installListeners() {
        if(preinitializeBegin.getAndSet(true))
            return;
        
        LimeCoreGlue.preinstall();
       
        preinitializeDone.set(true);

        //NEW, for azureus core to be notified of FrostWire's lifecycle events.
        installBittorrentListeners();
    }
    
    
    /**
     * Make sure the azureus core knows about our LifeCycleEvents and does the right thing
     * when we shut down.
     */
    private void installBittorrentListeners() {
    	addListener(new EventListener<LifecycleManagerImpl.LifeCycleEvent>() {

			@Override
			public void handleEvent(
					com.limegroup.gnutella.LifecycleManagerImpl.LifeCycleEvent event) {
				if (event == LifeCycleEvent.SHUTINGDOWN) {

                    BTEngine.getInstance().stop();
				} 
			}
			
		});
    }
    
    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#loadBackgroundTasks()
     */
    public void loadBackgroundTasks() {
        if(backgroundBegin.getAndSet(true))
            return;

        installListeners();
        
        ThreadExecutor.startThread(new Runnable() {
            public void run() {
                doBackgroundTasks();
            }
        }, "BackgroundTasks");
    }
    
    private void loadBackgroundTasksBlocking() {
        if(backgroundBegin.getAndSet(true))
            return;

        installListeners();
        
        doBackgroundTasks();
    }

    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#start()
     */
    public void start() {
        if(startBegin.getAndSet(true))
            return;
        
        try {
            listenerList.broadcast(LifeCycleEvent.STARTING);
            doStart();
            listenerList.broadcast(LifeCycleEvent.STARTED);
        } finally {
            startLatch.countDown();
        }
    }
    
    private void doStart() {
        loadBackgroundTasksBlocking();

        if(ApplicationSettings.AUTOMATIC_MANUAL_GC.getValue())
            startManualGCThread();
        
        startDone.set(true);
        startFinishedTime = System.currentTimeMillis();
    }

    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#shutdown()
     */
    public void shutdown() {
        try {
            listenerList.broadcast(LifeCycleEvent.SHUTINGDOWN);
            doShutdown();
            listenerList.broadcast(LifeCycleEvent.SHUTDOWN);
        } catch(Throwable t) {
            ErrorService.error(t);
        }
    }
    
    private void doShutdown() {
        if(!startBegin.get() || shutdownBegin.getAndSet(true))
            return;
        
        try {
            // TODO: should we have a time limit on how long we wait?
            startLatch.await(); // wait for starting to finish...
        } catch(InterruptedException ie) {
            LOG.error("Interrupted while waiting to finish starting", ie);
            return;
        }

        // save frostwire.props & other settings
        SettingsGroupManager.instance().save();

        BTEngine.getInstance().stop();
        
        shutdownDone.set(true);
    }

    
    private static String parseCommand(String toCall) {
        if (toCall.startsWith("\"")) {
            int end;
            if ((end = toCall.indexOf("\"", 1)) > -1) {
                return toCall.substring(0,end+1);
            }
            else {
                return toCall+"\"";
            }
        }
        int space;
        if ((space = toCall.indexOf(" ")) > -1) {
            return toCall.substring(0, space);
        }
        
        return toCall;
    }
    
    
    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#shutdown(java.lang.String)
     */
    public void shutdown(String toExecute) {
        shutdown();
        if (toExecute != null) {
            try {
                if (OSUtils.isWindowsVista()) {
                    String cmd = parseCommand(toExecute).trim();
                    String params = toExecute.substring(cmd.length()).trim();
                    SystemUtils.openFile(cmd, params);
                }
                else {
                    Runtime.getRuntime().exec(toExecute);
                }
            } catch (IOException tooBad) {}
        }
    }
    
    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#getStartFinishedTime()
     */
    public long getStartFinishedTime() {
        return startFinishedTime;
    }

    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#addShutdownItem(java.lang.Thread)
     */
    public boolean addShutdownItem(Thread t) {
        if(shutdownBegin.get())
            return false;
    
        SHUTDOWN_ITEMS.add(t);
        return true;
    }

    /** Runs all tasks that can be done in the background while the gui inits. */
    private void doBackgroundTasks() {
        limeCoreGlue.install(); // ensure glue is set before running tasks.
        
        backgroundDone.set(true);
    }

    /** Gets the current state of the lifecycle. */
    private State getCurrentState() {
        if(shutdownBegin.get())
            return State.STOPPED;
        else if(startDone.get())
            return State.STARTED;
        else if(startBegin.get())
            return State.STARTING;
        else
            return State.NONE;
    }

    /** Starts a manual GC thread. */
    private void startManualGCThread() {
        Thread t = ThreadExecutor.newManagedThread(new Runnable() {
            public void run() {
                while(true) {
                    try {
                        Thread.sleep(5 * 60 * 1000);
                    } catch(InterruptedException ignored) {}
                    //LOG.info("Running GC");
                    System.gc();
                    //LOG.info("GC finished, running finalizers");
                    System.runFinalization();
                    //LOG.info("Finalizers finished.");
                }
            }
        }, "ManualGC");
        t.setDaemon(true);
        t.start();
        //LOG.info("Started manual GC thread.");
    }

    public void addListener(EventListener<LifeCycleEvent> listener) {
        listenerList.addListener(listener);
    }

    public boolean removeListener(EventListener<LifeCycleEvent> listener) {
        return listenerList.removeListener(listener);
    }
}
