/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui;

import android.content.Context;

import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;

import java.util.List;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 */
public final class Peer {

    private final String address;

    /**
     * 16 bytes (128bit - UUID identifier letting us know who is the sender)
     */
    private final String clientVersion;

    private int hashCode = -1;

    private final String key;

    public Peer() {
        String address = "0.0.0.0";
        int port = 0;
        String clientVersion = Constants.FROSTWIRE_VERSION_STRING;

        this.key = address + ":" + port;
        this.address = address;

        this.clientVersion = clientVersion;

        this.hashCode = key.hashCode();
    }

    public List<FileDescriptor> browse(final Context context, byte fileType) {
        return Librarian.instance().getFiles(context, fileType, 0, Integer.MAX_VALUE);
    }

    @Override
    public String toString() {
        return "Peer(" + (address != null ? address : "unknown") + ", v:" + clientVersion + ")";
    }

    @Override
    public boolean equals(Object o) {
        return !(o == null || !(o instanceof Peer)) && hashCode() == o.hashCode();
    }

    @Override
    public int hashCode() {
        return this.hashCode != -1 ? this.hashCode : super.hashCode();
    }

    public String getKey() {
        return key;
    }
}
