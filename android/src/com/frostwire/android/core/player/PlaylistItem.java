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

package com.frostwire.android.core.player;

import com.frostwire.android.core.FWFileDescriptor;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class PlaylistItem {

    private final FWFileDescriptor fd;

    public PlaylistItem(FWFileDescriptor fd) {
        this.fd = fd;
    }

    public FWFileDescriptor getFD() {
        return fd;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PlaylistItem)) {
            return false;
        }

        return fd.equals(((PlaylistItem) o).fd);
    }

    @Override
    public int hashCode() {
        return fd.hashCode();
    }
}
