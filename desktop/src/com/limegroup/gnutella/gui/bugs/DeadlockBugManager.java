package com.limegroup.gnutella.gui.bugs;

import com.frostwire.util.HttpClientFactory;
import com.frostwire.util.Logger;
import com.limegroup.gnutella.gui.LimeWireModule;
import com.limegroup.gnutella.settings.BugSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

public class DeadlockBugManager {
    private static final Logger LOG = Logger.getLogger(DeadlockBugManager.class);

    private DeadlockBugManager() {
    }

    /**
     * Handles a deadlock bug.
     */
    public static void handleDeadlock(DeadlockException bug, String threadName, String message) {
        bug.printStackTrace();
        System.err.println("Detail: " + message);
        LocalClientInfo info = LimeWireModule.instance().getLimeWireGUIModule().getLimeWireGUI().getLocalClientInfoFactory().createLocalClientInfo(bug, threadName, message, false);
        // If it's a sendable version & we're either a beta or the user said to send it, send it
        if (BugSettings.SEND_DEADLOCK_BUGS.getValue()) {
            sendToServlet(info);
        }
    }

    private static void sendToServlet(LocalClientInfo info) {
        try {
            HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).post(BugSettings.BUG_REPORT_SERVER.getValue(), 6000, "FrostWire-" + FrostWireUtils.getFrostWireVersion(), info.toBugReport(), "text/plain", false);
        } catch (Exception e) {
            LOG.error("Error sending bug report", e);
        }
    }
}
