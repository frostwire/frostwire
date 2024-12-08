/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025 FrostWire(R). All rights reserved.
 *
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


package com.frostwire.android.gui.services;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.frostwire.android.util.SystemUtils;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.swig.bloom_filter_256;
import com.frostwire.jlibtorrent.swig.byte_vector;
import com.frostwire.jlibtorrent.swig.sha1_hash;
import com.frostwire.util.Hex;

public final class NotifiedStorage {

    private static final com.frostwire.util.Logger LOG = com.frostwire.util.Logger.getLogger(NotifiedStorage.class);

    // this is a preference key to be used only by this class
    private static final String PREF_KEY_NOTIFIED_HASHES = "frostwire.prefs.gui.notified_hashes";

    // not using ConfigurationManager to avoid setup/startup timing issues
    private final SharedPreferences preferences;
    private final bloom_filter_256 hashes;

    NotifiedStorage(Context context) {
        SystemUtils.ensureBackgroundThreadOrCrash("EngineService::NotifiedStorage::Constructor");
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        hashes = new bloom_filter_256();
        loadHashes();
    }

    public boolean contains(String infoHash) {
        if (infoHash == null || infoHash.length() != 40) {
            // not a valid info hash
            return false;
        }

        try {

            byte[] arr = Hex.decode(infoHash);
            sha1_hash ih = new sha1_hash(Vectors.bytes2byte_vector(arr));
            return hashes.find(ih);

        } catch (Throwable e) {
            LOG.warn("Error checking if info hash was notified", e);
        }

        return false;
    }

    public void add(String infoHash) {
        if (infoHash == null || infoHash.length() != 40) {
            // not a valid info hash
            return;
        }

        try {

            byte[] arr = Hex.decode(infoHash);
            sha1_hash ih = new sha1_hash(Vectors.bytes2byte_vector(arr));
            hashes.set(ih);

            byte_vector v = hashes.to_bytes();
            arr = Vectors.byte_vector2bytes(v);
            String s = Hex.encode(arr);

            SharedPreferences.Editor editor = preferences.edit();
            editor.putString(PREF_KEY_NOTIFIED_HASHES, s);
            editor.apply();

        } catch (Throwable e) {
            LOG.warn("Error adding info hash to notified storage", e);
        }
    }

    private void loadHashes() {
        SystemUtils.ensureBackgroundThreadOrCrash("EngineService::NotifiedStorage::loadHashes");
        String s = preferences.getString(PREF_KEY_NOTIFIED_HASHES, null);
        if (s != null) {
            try {
                byte[] arr = Hex.decode(s);
                hashes.from_bytes(Vectors.bytes2byte_vector(arr));
            } catch (Throwable e) {
                LOG.warn("Error loading notified storage from preference data", e);
            }
        }
    }
}