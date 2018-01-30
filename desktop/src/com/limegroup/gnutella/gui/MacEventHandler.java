/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;

import java.awt.*;
import java.awt.desktop.*;
import java.io.File;
import java.util.List;

/**
 * This class handles Macintosh specific events. The handled events
 * include the selection of the "About" option in the Mac file menu,
 * the selection of the "Quit" option from the Mac file menu, and the
 * dropping of a file on LimeWire on the Mac, which LimeWire would be
 * expected to handle in some way.
 */
public class MacEventHandler {

    private static final Logger LOG = Logger.getLogger(MacEventHandler.class);

    private static MacEventHandler INSTANCE;

    public static synchronized MacEventHandler instance() {
        if (INSTANCE == null)
            INSTANCE = new MacEventHandler();

        return INSTANCE;
    }

    private MacEventHandler() {

        Desktop app = Desktop.getDesktop();

        app.setAboutHandler(new AboutHandler() {
            @Override
            public void handleAbout(AboutEvent aboutEvent) {
                MacEventHandler.this.handleAbout();
            }
        });

        app.setQuitHandler(new QuitHandler() {
            @Override
            public void handleQuitRequestWith(QuitEvent quitEvent, QuitResponse quitResponse) {
                handleQuit();
            }
        });

        app.setOpenFileHandler(new OpenFilesHandler() {
            @Override
            public void openFiles(OpenFilesEvent openFilesEvent) {
                List<File> files = openFilesEvent.getFiles();
                if (files != null && files.size() > 0) {
                    File file = files.get(0);
                    LOG.debug("File: " + file);
                    if (file.getName().toLowerCase().endsWith(".torrent")) {
                        GUIMediator.instance().openTorrentFile(file, false);
                    }
                }
            }
        });

        app.setOpenURIHandler(new OpenURIHandler() {
            @Override
            public void openURI(OpenURIEvent openURIEvent) {
                String uri = openURIEvent.getURI().toString();
                LOG.debug("URI: " + uri);
                if (uri.startsWith("magnet:?xt=urn:btih")) {
                    GUIMediator.instance().openTorrentURI(uri, false);
                }
            }
        });

        /*app.addAppEventListener(new AppReOpenedListener() {
            @Override
            public void appReOpened(AppReOpenedEvent appReOpenedEvent) {
                handleReopen();
            }
        });*/

        app.setPreferencesHandler(new PreferencesHandler() {
            @Override
            public void handlePreferences(PreferencesEvent preferencesEvent) {
                MacEventHandler.this.handlePreferences();
            }
        });
    }

    /**
     * This responds to the selection of the about option by displaying the
     * about window to the user.  On OSX, this runs in a new ManagedThread to handle
     * the possibility that event processing can become blocked if launched
     * in the calling thread.
     */
    private void handleAbout() {
        GUIMediator.showAboutWindow();
    }

    /**
     * This method responds to a quit event by closing the application in
     * the whichever method the user has configured (closing after completed
     * file transfers by default).  On OSX, this runs in a new ManagedThread to handle
     * the possibility that event processing can become blocked if launched
     * in the calling thread.
     */
    private void handleQuit() {
        GUIMediator.applyWindowSettings();
        GUIMediator.close(false);
    }

    private void handleReopen() {
        GUIMediator.handleReopen();
    }

    private void handlePreferences() {
        GUIMediator.instance().setOptionsVisible(true);
    }
}
