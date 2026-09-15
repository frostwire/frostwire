/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.gui.bittorrent;

/**
 * @author gubatron
 * @author aldenml
 */
final class TransferHolder {
    private final BTDownload dl;
    private final String displayName;
    // Per-row action state, computed off the EDT during the model refresh and
    // read by the (shared) renderer/editor. Keeping it on the holder prevents
    // one row's state from clobbering another's as the single renderer instance
    // is stamped across rows.
    private volatile boolean canShare;
    private volatile boolean canPlay;

    public TransferHolder(final BTDownload dl) {
        this.dl = dl;
        this.displayName = dl.getDisplayName();
    }

    public BTDownload getDl() {
        return dl;
    }

    void setActionsState(boolean canShare, boolean canPlay) {
        this.canShare = canShare;
        this.canPlay = canPlay;
    }

    boolean canShare() {
        return canShare;
    }

    boolean canPlay() {
        return canPlay;
    }

    public String toString() {
        return displayName;
    }
}
