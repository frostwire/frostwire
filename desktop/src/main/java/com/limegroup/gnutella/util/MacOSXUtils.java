package com.limegroup.gnutella.util;

import com.frostwire.util.Logger;
import org.limewire.util.CommonUtils;

/**
 * A collection of utility methods for OSX.
 * These methods should only be called if run from OSX,
 * otherwise ClassNotFoundErrors may occur.
 * <p>
 * To determine if the Cocoa Foundation classes are present,
 * use the method CommonUtils.isCocoaFoundationAvailable().
 */
public class MacOSXUtils {
    /**
     * The name of the app that launches.
     */
    private static final String APP_NAME = "FrostWire.app";
    private static boolean initialized = false;
    private static final Logger LOG = Logger.getLogger(MacOSXUtils.class);

    static {
        try {
            System.loadLibrary("MacOSXUtils");
            initialized = true;
        } catch (Throwable err) {
            err.printStackTrace();
        }
    }

    private MacOSXUtils() {
    }

    /**
     * Modifies mac OSX environment to run this application on startup
     */
    public static void setLoginStatus(boolean allow) {
        if (initialized) {
            String rawDir = CommonUtils.getExecutableDirectory();
            if (!rawDir.contains(APP_NAME)) {
                // probably running in development mode
                LOG.info("MacOSXUtils::setLoginStatus(allow=" + allow + ") could not find \"FrostWire.app\" folder in this executable directory (perhaps running from source), not attempting to add/remove from macOS startup list");
                return;
            }
            String path = rawDir.substring(0, rawDir.indexOf(APP_NAME) + APP_NAME.length());
            LOG.info(":SetLoginStatusNative(allow=" + allow + ",path=" + path + ")");
            SetLoginStatusNative(allow, path);
        }
    }

    /**
     * [Un]registers FrostWire from the startup items list.
     */
    private static native void SetLoginStatusNative(boolean allow, String appPath);
}
