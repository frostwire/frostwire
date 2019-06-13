package com.limegroup.gnutella;

/**
 * Contains mostly all references to singletons within LimeWire.
 * This class should only be used if it is not possible to inject
 * the correct values into what you're using.  In most cases,
 * it should be possible to just get the injector and call
 * injector.injectMembers(myObject), which is still a superior
 * option to retrieving the individual objects from this class.
 */
public class LimeWireCore {
    private static LimeWireCore INSTANCE;

    private LimeWireCore() {
    }

    public static LimeWireCore instance() {
        if (INSTANCE == null) {
            INSTANCE = new LimeWireCore();
        }
        return INSTANCE;
    }

    public DownloadManager getDownloadManager() {
        return LimeWireCoreModule.instance().getDownloadManager();
    }

    public LifecycleManager getLifecycleManager() {
        return LimeWireCoreModule.instance().getLifecycleManager();
    }

    public ExternalControl getExternalControl() {
        return ExternalControl.instance(LimeWireCoreModule.instance().getActivityCallback());
    }

    public LimeCoreGlue getLimeCoreGlue() {
        return LimeCoreGlue.instance();
    }
}
