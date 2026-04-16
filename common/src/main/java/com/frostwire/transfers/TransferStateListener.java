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

package com.frostwire.transfers;

/**
 * Listener interface for Transfer state changes.
 * Allows UI components to receive immediate notifications when a transfer's state changes,
 * eliminating the need for polling.
 * 
 * @author gubatron
 */
public interface TransferStateListener {
    /**
     * Called when the transfer's state changes (e.g., DOWNLOADING -> COMPLETE).
     * 
     * @param transfer The transfer whose state changed
     * @param oldState The previous state
     * @param newState The new state
     */
    void onTransferStateChanged(Transfer transfer, TransferState oldState, TransferState newState);
    
    /**
     * Called when the transfer's progress changes significantly.
     * Note: This may be called frequently during active downloads.
     * 
     * @param transfer The transfer whose progress changed
     * @param progress The new progress percentage (0-100)
     */
    void onTransferProgressChanged(Transfer transfer, int progress);
}
