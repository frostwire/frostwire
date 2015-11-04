package com.limegroup.gnutella.gui.bugs;

import org.limewire.util.Version;
import org.limewire.util.VersionFormatException;

import com.frostwire.logging.Logger;
import com.frostwire.util.HttpClientFactory;
import com.limegroup.gnutella.gui.LimeWireModule;
import com.limegroup.gnutella.settings.BugSettings;
import com.limegroup.gnutella.util.FrostWireUtils;

public class DeadlockBugManager {
    
    private static final Logger LOG = Logger.getLogger(DeadlockBugManager.class);

   private DeadlockBugManager() {}
    
    /** Handles a deadlock bug. */
    public static void handleDeadlock(DeadlockException bug, String threadName, String message) {
        bug.printStackTrace();
        System.err.println("Detail: " + message);
        
        LocalClientInfo info = LimeWireModule.instance().getLimeWireGUIModule().getLimeWireGUI().getLocalClientInfoFactory().createLocalClientInfo(bug, threadName, message, false);
        // If it's a sendable version & we're either a beta or the user said to send it, send it
        if(isSendableVersion() && (BugSettings.SEND_DEADLOCK_BUGS.getValue())) {
            sendToServlet(info);
        }
    }
    
    /** Determines if we're allowed to send a bug report. */
    private static boolean isSendableVersion() {
        Version myVersion;
        Version lastVersion;
        try {
            myVersion = new Version(FrostWireUtils.getFrostWireVersion());
            lastVersion = new Version(BugSettings.LAST_ACCEPTABLE_VERSION.getValue());
        } catch(VersionFormatException vfe) {
            return false;
        }
        
        return myVersion.compareTo(lastVersion) >= 0;
    }
    
    private static void sendToServlet(LocalClientInfo info) {
        try {
            HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC).post(BugSettings.BUG_REPORT_SERVER.getValue(), 6000, "FrostWire-" + FrostWireUtils.getFrostWireVersion(), info.toBugReport(), "text/plain", false);
        } catch (Exception e) {
            LOG.error("Error sending bug report", e);
        }
    }
}
