package com.limegroup.gnutella.gui.util;

import java.awt.Cursor;
import java.io.File;

import com.limegroup.gnutella.gui.GUIMediator;
import com.limegroup.gnutella.gui.GUIUtils;

/**
 * Static utility class that handles launching of downloaders and
 * displaying error messages.
 */
public class GUILauncher {

    /**
     * Provides a downloader or a file that should be launched.
     */
    public interface LaunchableProvider {
        /**
         * Can return null if only a downloader is avaialable 
         */
        File getFile();
    }

    /**
     * Launches an array of <code>providers</code> delegating the time
     * consuming construction of {@link Downloader#getDownloadFragment()}
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
}
