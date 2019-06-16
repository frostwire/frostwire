/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui;

import com.frostwire.util.Logger;
import org.limewire.service.ErrorService;

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
