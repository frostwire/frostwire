/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.util;

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.transfers.TransferState;

import java.util.HashMap;
import java.util.Map;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 10/16/17.
 */


public final class TransferStateStrings {
    //private static Logger LOG = Logger.getLogger(TransferStateStrings.class);
    private final Map<TransferState, String> TRANSFER_STATE_STRING_MAP;
    private static final Object singletonLock = new Object();
    private static TransferStateStrings INSTANCE;

    private TransferStateStrings(Context ctx) {
        TRANSFER_STATE_STRING_MAP = new HashMap<>();
        initTransferStateStringMap(ctx);
    }

    public static TransferStateStrings getInstance(Context context) {
        synchronized (singletonLock) {
            if (INSTANCE == null && context != null) {
                INSTANCE = new TransferStateStrings(context);
            }
        }
        return INSTANCE;
    }

    public String get(TransferState stateEnum) {
        return TRANSFER_STATE_STRING_MAP.get(stateEnum);
    }

    private void initTransferStateStringMap(Context c) {
        TRANSFER_STATE_STRING_MAP.put(TransferState.FINISHING, c.getString(R.string.finishing));
        TRANSFER_STATE_STRING_MAP.put(TransferState.CHECKING, c.getString(R.string.checking_ellipsis));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DOWNLOADING_METADATA, c.getString(R.string.downloading_metadata));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DOWNLOADING_TORRENT, c.getString(R.string.torrent_fetcher_download_status_downloading_torrent));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DOWNLOADING, c.getString(R.string.azureus_manager_item_downloading));
        TRANSFER_STATE_STRING_MAP.put(TransferState.FINISHED, c.getString(R.string.azureus_peer_manager_status_finished));
        TRANSFER_STATE_STRING_MAP.put(TransferState.SEEDING, c.getString(R.string.azureus_manager_item_seeding));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ALLOCATING, c.getString(R.string.azureus_manager_item_allocating));
        TRANSFER_STATE_STRING_MAP.put(TransferState.PAUSED, c.getString(R.string.azureus_manager_item_paused));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR, c.getString(R.string.azureus_manager_item_error));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_MOVING_INCOMPLETE, c.getString(R.string.error_moving_incomplete));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_HASH_MD5, c.getString(R.string.error_wrong_md5_hash));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_SIGNATURE, c.getString(R.string.error_wrong_signature));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_NOT_ENOUGH_PEERS, c.getString(R.string.error_not_enough_peers));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_NO_INTERNET, c.getString(R.string.error_no_internet_connection));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_SAVE_DIR, c.getString(R.string.http_download_status_save_dir_error));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_TEMP_DIR, c.getString(R.string.http_download_status_temp_dir_error));
        TRANSFER_STATE_STRING_MAP.put(TransferState.STOPPED, c.getString(R.string.azureus_manager_item_stopped));
        TRANSFER_STATE_STRING_MAP.put(TransferState.PAUSING, c.getString(R.string.pausing));
        TRANSFER_STATE_STRING_MAP.put(TransferState.CANCELING, c.getString(R.string.canceling));
        TRANSFER_STATE_STRING_MAP.put(TransferState.CANCELED, c.getString(R.string.torrent_fetcher_download_status_canceled));
        TRANSFER_STATE_STRING_MAP.put(TransferState.WAITING, c.getString(R.string.waiting));
        TRANSFER_STATE_STRING_MAP.put(TransferState.COMPLETE, c.getString(R.string.peer_http_download_status_complete));
        TRANSFER_STATE_STRING_MAP.put(TransferState.UPLOADING, c.getString(R.string.peer_http_upload_status_uploading));
        TRANSFER_STATE_STRING_MAP.put(TransferState.UNCOMPRESSING, c.getString(R.string.http_download_status_uncompressing));
        TRANSFER_STATE_STRING_MAP.put(TransferState.DEMUXING, c.getString(R.string.transfer_status_demuxing));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_DISK_FULL, c.getString(R.string.error_no_space_left_on_device));
        TRANSFER_STATE_STRING_MAP.put(TransferState.SCANNING, c.getString(R.string.scanning));
        TRANSFER_STATE_STRING_MAP.put(TransferState.ERROR_CONNECTION_TIMED_OUT, c.getString(R.string.error_connection_timed_out));
        TRANSFER_STATE_STRING_MAP.put(TransferState.UNKNOWN, "");
    }
}
