/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.transfers;

/**
 * @author gubatron
 * @author aldenml
 */
public enum TransferState {
    FINISHING,
    CHECKING,
    DOWNLOADING_METADATA,
    DOWNLOADING_TORRENT,
    DOWNLOADING,
    FINISHED,
    SEEDING,
    ALLOCATING,
    PAUSED,
    ERROR,
    ERROR_MOVING_INCOMPLETE,
    ERROR_HASH_MD5,
    ERROR_SIGNATURE,
    ERROR_NOT_ENOUGH_PEERS,
    ERROR_NO_INTERNET,
    ERROR_SAVE_DIR,
    ERROR_TEMP_DIR,
    STOPPED,
    PAUSING,
    CANCELING,
    CANCELED,
    WAITING,
    COMPLETE,
    UPLOADING,
    UNCOMPRESSING,
    DEMUXING,
    UNKNOWN,
    ERROR_DISK_FULL,
    REDIRECTING,
    STREAMING,
    SCANNING,
    ERROR_CONNECTION_TIMED_OUT;

    public static boolean isErrored(TransferState state) {
        return state.equals(TransferState.ERROR) ||
                state.equals(TransferState.ERROR_MOVING_INCOMPLETE) ||
                state.equals(TransferState.ERROR_HASH_MD5) ||
                state.equals(TransferState.ERROR_SIGNATURE) ||
                state.equals(TransferState.ERROR_NOT_ENOUGH_PEERS) ||
                state.equals(TransferState.ERROR_NO_INTERNET) ||
                state.equals(TransferState.ERROR_SAVE_DIR) ||
                state.equals(TransferState.ERROR_TEMP_DIR) ||
                state.equals(TransferState.ERROR_DISK_FULL) ||
                state.equals(TransferState.ERROR_CONNECTION_TIMED_OUT);
    }
}
