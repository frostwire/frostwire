package com.limegroup.gnutella;

import com.limegroup.gnutella.gui.VisualConnectionCallback;

/**
 * The module that defines what implementations are used within
 * LimeWire's core.  This class can be constructed with or without
 * an ActivitiyCallback class.  If it is without, then another module
 * must explicitly identify which class is going to define the
 * ActivityCallback.
 */
public class LimeWireCoreModule {
    private static LimeWireCoreModule INSTANCE;
    private final ActivityCallback activityCallback;
    private final LifecycleManager lifecycleManager;
    private final DownloadManager downloadManager;
    private LimeWireCoreModule() {
        this.activityCallback = VisualConnectionCallback.instance();
        this.downloadManager = new DownloadManagerImpl(activityCallback);
        this.lifecycleManager = new LifecycleManagerImpl(LimeCoreGlue.instance());
    }

    public static LimeWireCoreModule instance() {
        if (INSTANCE == null) {
            INSTANCE = new LimeWireCoreModule();
        }
        return INSTANCE;
    }

    public ActivityCallback getActivityCallback() {
        return activityCallback;
    }

    public LifecycleManager getLifecycleManager() {
        return lifecycleManager;
    }

    public DownloadManager getDownloadManager() {
        return downloadManager;
    }
}
