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

package com.frostwire.android.gui;

import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Peer {

    private String address;

    /**
     * 16 bytes (128bit - UUID identifier letting us know who is the sender)
     */
    private String clientVersion;

    private int hashCode = -1;

    private String key;

    public Peer() {
        String address = "0.0.0.0";
        int port = 0;
        String clientVersion = Constants.FROSTWIRE_VERSION_STRING;

        this.key = address + ":" + port;
        this.address = address;

        this.clientVersion = clientVersion;

        this.hashCode = key.hashCode();
    }

    public String getAddress() {
        return address;
    }

    public Finger finger() {
        return Librarian.instance().finger();
    }

    public List<FileDescriptor> browse(byte fileType) {
        return Librarian.instance().getFiles(fileType, 0, Integer.MAX_VALUE);
    }

    @Override
    public String toString() {
        return "Peer(" + (address != null ? address : "unknown") + ", v:" + clientVersion + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof Peer)) {
            return false;
        }

        return hashCode() == ((Peer) o).hashCode();
    }

    @Override
    public int hashCode() {
        return this.hashCode != -1 ? this.hashCode : super.hashCode();
    }

    public String getKey() {
        return key;
    }
}
