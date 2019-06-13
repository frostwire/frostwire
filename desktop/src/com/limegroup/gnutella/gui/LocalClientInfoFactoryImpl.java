package com.limegroup.gnutella.gui;

import com.limegroup.gnutella.gui.bugs.LocalClientInfo;

/**
 * A factory for creating LocalClientInfo objects.
 */
public class LocalClientInfoFactoryImpl implements LocalClientInfoFactory {
    private static LocalClientInfoFactoryImpl INSTANCE;

    private LocalClientInfoFactoryImpl() {
    }

    public static LocalClientInfoFactoryImpl instance() {
        if (INSTANCE == null) {
            INSTANCE = new LocalClientInfoFactoryImpl();
        }
        return INSTANCE;
    }

    /* (non-Javadoc)
     * @see com.limegroup.gnutella.gui.LocalClientInfoFactory#createLocalClientInfo(java.lang.Throwable, java.lang.String, java.lang.String, boolean)
     */
    public LocalClientInfo createLocalClientInfo(Throwable bug, String threadName, String detail, boolean fatal) {
        return new LocalClientInfo(bug, threadName, detail, fatal);
    }
}
