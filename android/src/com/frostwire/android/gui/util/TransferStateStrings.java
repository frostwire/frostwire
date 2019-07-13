/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.util;

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.transfers.TransferState;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public final class TransferStateStrings {

    private final Map<TransferState, String> stringsMap;

    private static final Object singletonLock = new Object();
    private static TransferStateStrings INSTANCE;

    private TransferStateStrings(Context ctx) {
        stringsMap = initTransferStateStringMap(ctx);
    }

    public static TransferStateStrings getInstance(@NonNull Context context) {
        synchronized (singletonLock) {
            if (INSTANCE == null) {
                INSTANCE = new TransferStateStrings(context);
            }
            return INSTANCE;
        }
    }

    public String get(TransferState stateEnum) {
        return stringsMap.get(stateEnum);
    }

    private Map<TransferState, String> initTransferStateStringMap(Context c) {
        Map<TransferState, String> map = new HashMap<>();
        map.put(TransferState.FINISHING, c.getString(R.string.finishing));
        map.put(TransferState.CHECKING, c.getString(R.string.checking_ellipsis));
        map.put(TransferState.DOWNLOADING_METADATA, c.getString(R.string.downloading_metadata));
        map.put(TransferState.DOWNLOADING_TORRENT, c.getString(R.string.torrent_fetcher_download_status_downloading_torrent));
        map.put(TransferState.DOWNLOADING, c.getString(R.string.azureus_manager_item_downloading));
        map.put(TransferState.FINISHED, c.getString(R.string.azureus_peer_manager_status_finished));
        map.put(TransferState.SEEDING, c.getString(R.string.azureus_manager_item_seeding));
        map.put(TransferState.ALLOCATING, c.getString(R.string.azureus_manager_item_allocating));
        map.put(TransferState.PAUSED, c.getString(R.string.azureus_manager_item_paused));
        map.put(TransferState.ERROR, c.getString(R.string.azureus_manager_item_error));
        map.put(TransferState.ERROR_MOVING_INCOMPLETE, c.getString(R.string.error_moving_incomplete));
        map.put(TransferState.ERROR_HASH_MD5, c.getString(R.string.error_wrong_md5_hash));
        map.put(TransferState.ERROR_SIGNATURE, c.getString(R.string.error_wrong_signature));
        map.put(TransferState.ERROR_NOT_ENOUGH_PEERS, c.getString(R.string.error_not_enough_peers));
        map.put(TransferState.ERROR_NO_INTERNET, c.getString(R.string.error_no_internet_connection));
        map.put(TransferState.ERROR_SAVE_DIR, c.getString(R.string.http_download_status_save_dir_error));
        map.put(TransferState.ERROR_TEMP_DIR, c.getString(R.string.http_download_status_temp_dir_error));
        map.put(TransferState.STOPPED, c.getString(R.string.azureus_manager_item_stopped));
        map.put(TransferState.PAUSING, c.getString(R.string.pausing));
        map.put(TransferState.CANCELING, c.getString(R.string.canceling));
        map.put(TransferState.CANCELED, c.getString(R.string.torrent_fetcher_download_status_canceled));
        map.put(TransferState.WAITING, c.getString(R.string.waiting));
        map.put(TransferState.COMPLETE, c.getString(R.string.peer_http_download_status_complete));
        map.put(TransferState.UPLOADING, c.getString(R.string.peer_http_upload_status_uploading));
        map.put(TransferState.UNCOMPRESSING, c.getString(R.string.http_download_status_uncompressing));
        map.put(TransferState.DEMUXING, c.getString(R.string.transfer_status_demuxing));
        map.put(TransferState.ERROR_DISK_FULL, c.getString(R.string.error_no_space_left_on_device));
        map.put(TransferState.SCANNING, c.getString(R.string.scanning));
        map.put(TransferState.ERROR_CONNECTION_TIMED_OUT, c.getString(R.string.error_connection_timed_out));
        map.put(TransferState.UNKNOWN, "");
        return map;
    }
}
