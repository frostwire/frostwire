/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.limegroup.gnutella.gui.shell;

class MagnetAssociation implements ShellAssociation {
    /**
     * The extension for magnet: links, "magnet", without punctuation.
     */
    private static final String MAGNET_EXTENSION = "magnet";
    /**
     * The name of the magnet: link protocol, "Magnet Protocol".
     */
    private static final String MAGNET_PROTOCOL = "Magnet Protocol";
    private final ShellAssociation protocol, handler;

    MagnetAssociation(String program, String executable) {
        protocol = new WindowsProtocolShellAssociation(executable, MAGNET_EXTENSION, MAGNET_PROTOCOL);
        handler = new WindowsMagnetHandlerAssociation(program, executable);
    }

    public boolean isAvailable() {
        return protocol.isAvailable();
    }

    public boolean isRegistered() {
        return protocol.isRegistered();
    }

    public void register() {
        protocol.register();
        handler.register();
    }

    public void unregister() {
        protocol.unregister();
        if (handler.isRegistered())
            handler.unregister();
    }
}
