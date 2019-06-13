/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
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
