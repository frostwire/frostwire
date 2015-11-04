package com.limegroup.gnutella.gui;

import com.frostwire.logging.Logger;
import org.limewire.service.ErrorService;

/**
 * JNI based GetURL AppleEvent handler for Mac OS X
 */
public final class GURLHandler {

    private static final Logger LOG = Logger.getLogger(GURLHandler.class);

    private static GURLHandler instance;
    private volatile boolean registered = false;

    static {
        try {
            System.loadLibrary("GURLLeopard");
        } catch (UnsatisfiedLinkError err) {
            ErrorService.error(err);
        }
    }

    public static synchronized GURLHandler getInstance() {
        if (instance == null)
            instance = new GURLHandler();
        return instance;
    }

    /**
     * Called by the native code
     */
    private void callback(final String uri) {
        LOG.debug("URI: " + uri);
        if (uri.startsWith("magnet:?xt=urn:btih")) {
            GUIMediator.instance().openTorrentURI(uri, false);
        }
    }

    /**
     * Registers the GetURL AppleEvent handler.
     */
    public void register() {
        if (!registered) {
            if (InstallEventHandler() == 0) {
                //System.out.println("GURLHandler - AppleEvent handler registered");
                registered = true;
            }
        }
    }

    /**
     * We're nice guys and remove the GetURL AppleEvent handler although
     * this never happens
     */
    @Override
    protected void finalize() throws Throwable {
        if (registered) {
            RemoveEventHandler();
        }
    }

    private synchronized final native int InstallEventHandler();

    private synchronized final native int RemoveEventHandler();
}
