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
