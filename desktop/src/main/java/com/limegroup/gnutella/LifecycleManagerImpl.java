package com.limegroup.gnutella;

import com.frostwire.bittorrent.BTEngine;
import com.frostwire.concurrent.concurrent.ThreadExecutor;
import com.frostwire.service.ErrorService;
import com.frostwire.util.Logger;
import com.frostwire.util.OSUtils;
import com.limegroup.gnutella.settings.ApplicationSettings;
import org.limewire.setting.SettingsGroupManager;
import org.limewire.util.SystemUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final LimeCoreGlue limeCoreGlue;
    /**
     * A list of items that require running prior to shutting down LW.
     */
    private final List<Thread> SHUTDOWN_ITEMS = Collections.synchronizedList(new LinkedList<>());
    /**/
    LifecycleManagerImpl(
            LimeCoreGlue limeCoreGlue) {
        this.limeCoreGlue = limeCoreGlue;
    }

    private static String parseCommand(String toCall) {
        if (toCall.startsWith("\"")) {
            int end;
            if ((end = toCall.indexOf("\"", 1)) > -1) {
                return toCall.substring(0, end + 1);
            } else {
                return toCall + "\"";
            }
        }
        int space;
        if ((space = toCall.indexOf(" ")) > -1) {
            return toCall.substring(0, space);
        }
        return toCall;
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
        if (preinitializeBegin.getAndSet(true))
            return;
        LimeCoreGlue.preinstall();
        preinitializeDone.set(true);
    }

    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#loadBackgroundTasks()
     */
    public void loadBackgroundTasks() {
        if (backgroundBegin.getAndSet(true))
            return;
        installListeners();
        // Don't try using GUIMediator.instance().uiPool()
        ThreadExecutor.startThread(this::doBackgroundTasks, "BackgroundTasks");
    }

    private void loadBackgroundTasksBlocking() {
        if (backgroundBegin.getAndSet(true))
            return;
        installListeners();
        doBackgroundTasks();
    }

    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#start()
     */
    public void start() {
        if (startBegin.getAndSet(true))
            return;
        try {
            doStart();
        } finally {
            startLatch.countDown();
        }
    }

    private void doStart() {
        loadBackgroundTasksBlocking();
        if (ApplicationSettings.AUTOMATIC_MANUAL_GC.getValue())
            startManualGCThread();
        startDone.set(true);
    }

    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#shutdown()
     */
    public void shutdown() {
        try {
            doShutdown();
        } catch (Throwable t) {
            ErrorService.error(t);
        }
    }

    private void doShutdown() {
        if (!startBegin.get() || shutdownBegin.getAndSet(true))
            return;
        try {
            // TODO: should we have a time limit on how long we wait?
            startLatch.await(); // wait for starting to finish...
        } catch (InterruptedException ie) {
            LOG.error("Interrupted while waiting to finish starting", ie);
            return;
        }
        // save frostwire.props & other settings
        SettingsGroupManager.instance().save();
        LOG.info("Stopping BTEngine...");
        BTEngine.getInstance().stop();
        LOG.info("BTEngine stopped");
        shutdownDone.set(true);
    }

    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#shutdown(java.lang.String)
     */
    public void shutdown(String toExecute) {
        shutdown();
        if (toExecute != null) {
            try {
                // Parse toExecute as a shell command with arguments
                // Split by spaces, but respect quoted strings
                java.util.List<String> commandList = new java.util.ArrayList<>();
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(toExecute);
                while (matcher.find()) {
                    commandList.add(matcher.group(1).replace("\"", ""));
                }
                if (!commandList.isEmpty()) {
                    LOG.info("Starting restart process: " + commandList);
                    ProcessBuilder pb = new ProcessBuilder(commandList);
                    // Inherit the current working directory
                    pb.directory(new java.io.File(System.getProperty("user.dir")));
                    // Start the process
                    Process process = pb.start();
                    // Give the new process a moment to start before we exit
                    // This helps ensure the child process detaches properly
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ie) {
                        // Ignore interruption
                    }
                    LOG.info("Restart process started successfully");
                }
            } catch (IOException e) {
                LOG.error("Failed to execute command after shutdown: " + toExecute, e);
            }
        }
    }

    /* (non-Javadoc)
     * @see com.limegroup.gnutella.LifecycleManager#addShutdownItem(java.lang.Thread)
     */
    public void addShutdownItem(Thread t) {
        if (shutdownBegin.get())
            return;
        SHUTDOWN_ITEMS.add(t);
    }

    /**
     * Runs all tasks that can be done in the background while the gui inits.
     */
    private void doBackgroundTasks() {
        limeCoreGlue.install(); // ensure glue is set before running tasks.
        backgroundDone.set(true);
    }

    /**
     * Gets the current state of the lifecycle.
     */
    private State getCurrentState() {
        if (shutdownBegin.get())
            return State.STOPPED;
        else if (startDone.get())
            return State.STARTED;
        else if (startBegin.get())
            return State.STARTING;
        else
            return State.NONE;
    }

    /**
     * Starts a manual GC thread.
     */
    private void startManualGCThread() {
        Thread t = ThreadExecutor.newManagedThread(() -> {
            //noinspection InfiniteLoopStatement
            while (true) {
                try {
                    Thread.sleep(5 * 60 * 1000);
                } catch (InterruptedException ignored) {
                }
                //LOG.info("Running GC");
                System.gc();
            }
        }, "ManualGC");
        t.setDaemon(true);
        t.start();
        //LOG.info("Started manual GC thread.");
    }

    private enum State {NONE, STARTING, STARTED, STOPPED}
}
