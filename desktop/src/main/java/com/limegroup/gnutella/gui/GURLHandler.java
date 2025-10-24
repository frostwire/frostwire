/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;
import com.frostwire.service.ErrorService;

/**
 * JNI based GetURL AppleEvent handler for Mac OS X
 */
public final class GURLHandler {
    private static final Logger LOG = Logger.getLogger(GURLHandler.class);
    private static GURLHandler instance;

    static {
        try {
            System.loadLibrary("GURL");
        } catch (Throwable err) {
            ErrorService.error(err);
        }
    }

    private volatile boolean registered = false;

    public static synchronized GURLHandler getInstance() {
        if (instance == null)
            instance = new GURLHandler();
        return instance;
    }

    /**
     * Registers the GetURL AppleEvent handler.
     */
    public void register() {
        if (!registered) {
            int error = InstallEventHandler();
            if (error == 0) {
                LOG.info("GURLHandler.register() AppleEvent handler registered");
                registered = true;
            } else {
                LOG.error("GURLHandler.register() AppleEvent handler not registered, error " + error);
            }
        }
    }

    /**
     * Called by the native code
     */
    @SuppressWarnings("unused")
    private void callback(final String uri) {
        LOG.debug("URI: " + uri);
        if (uri.startsWith("magnet:?xt=urn:btih")) {
            GUIMediator.instance().openTorrentURI(uri, false);
        }
    }

    private synchronized native int InstallEventHandler();
}
