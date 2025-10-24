package com.limegroup.gnutella.gui.util;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;

import java.awt.*;
import java.io.File;

/**
 * Static utility class that handles launching of downloaders and
 * displaying error messages.
 */
public class GUILauncher {
    /**
     * Launches an array of <code>providers</code> delegating the time
     * consuming construction of
     * into a background threads.
     */
    public static void launch(LaunchableProvider[] providers) {
        boolean audioLaunched = false;
        GUIMediator.instance().setFrameCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        for (LaunchableProvider provider : providers) {
            File file = provider.getFile();
            if (file != null) {
                audioLaunched = GUIUtils.launchOrEnqueueFile(file, audioLaunched);
            }
        }
        GUIMediator.instance().setFrameCursor(Cursor.getDefaultCursor());
    }

    /**
     * Provides a downloader or a file that should be launched.
     */
    public interface LaunchableProvider {
        /**
         * Can return null if only a downloader is avaialable
         */
        File getFile();
    }
}
