/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.android.gui.fragments.preference;

import android.os.Bundle;

import androidx.preference.Preference;

import com.frostwire.android.R;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.gui.views.preference.ButtonActionPreference;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.KarmaConstants;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

public final class DistributedSearchPreferenceFragment extends AbstractPreferenceFragment {

    private static final Logger LOG = Logger.getLogger(DistributedSearchPreferenceFragment.class);

    public DistributedSearchPreferenceFragment() {
        super(R.xml.settings_distributed_search);
    }

    @Override
    protected void initComponents() {
        setupDistributedToggle();
        refreshIdentityInfo();
        setupRefreshButton();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshIdentityInfo();
        refreshPeerList();
    }

    private void setupDistributedToggle() {
        Preference toggle = findPreference("frostwire.prefs.search.use_distributed");
        if (toggle != null) {
            toggle.setOnPreferenceChangeListener((pref, newValue) -> {
                pref.setSummary(Boolean.TRUE.equals(newValue)
                        ? getString(R.string.distributed_search_summary)
                        : getString(R.string.distributed_search_summary) + " (disabled)");
                return true;
            });
        }
    }

    private void refreshIdentityInfo() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            IdentityKeys identity = SearchEngine.DISTRIBUTED_WIRING.identity();
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (getActivity() == null) return;
                updateIdentityRows(identity);
            });
        });
    }

    private void updateIdentityRows(IdentityKeys identity) {
        Preference nodeIdPref = findPreference("frostwire.prefs.distributed.identity.node_id");
        Preference fingerprintPref = findPreference("frostwire.prefs.distributed.identity.fingerprint");
        Preference difficultyPref = findPreference("frostwire.prefs.distributed.identity.difficulty");

        if (identity == null) {
            if (nodeIdPref != null) nodeIdPref.setSummary(R.string.distributed_identity_not_initialized);
            if (fingerprintPref != null) fingerprintPref.setSummary(R.string.distributed_identity_not_initialized);
            if (difficultyPref != null) difficultyPref.setSummary(R.string.distributed_identity_not_initialized);
            return;
        }

        if (nodeIdPref != null) {
            String nodeIdHex = Hex.encode(identity.nodeId());
            nodeIdPref.setSummary(formatHex(nodeIdHex));
        }

        if (fingerprintPref != null) {
            String pubHex = Hex.encode(identity.ed25519PubRaw());
            fingerprintPref.setSummary(formatHex(pubHex));
        }

        if (difficultyPref != null) {
            int difficulty = IdentityKeys.countLeadingZeroBits(identity.nodeId());
            int required = KarmaConstants.IDENTITY_DIFFICULTY;
            if (difficulty >= required) {
                difficultyPref.setSummary(getString(R.string.distributed_identity_difficulty_meets, difficulty));
            } else {
                difficultyPref.setSummary(getString(R.string.distributed_identity_difficulty_below, difficulty, required));
            }
        }
    }

    private void setupRefreshButton() {
        ButtonActionPreference refreshBtn = findPreference("frostwire.prefs.distributed.peers.refresh");
        if (refreshBtn != null) {
            refreshBtn.setOnPreferenceClickListener(preference -> {
                refreshPeerList();
                return true;
            });
        }
    }

    private void refreshPeerList() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            PeerDirectory directory = SearchEngine.DISTRIBUTED_WIRING.peerDirectory();
            if (getActivity() == null) return;
            int peerCount = directory != null ? directory.size() : -1;
            int verifiedCount = directory != null ? directory.topByTrustVerified(100).size() : -1;
            getActivity().runOnUiThread(() -> {
                if (getActivity() == null) return;
                updatePeerCountRow(verifiedCount, peerCount);
            });
        });
    }

    private void updatePeerCountRow(int verifiedCount, int totalCount) {
        Preference countPref = findPreference("frostwire.prefs.distributed.peers.count");
        if (countPref == null) return;

        if (totalCount < 0) {
            countPref.setSummary(R.string.distributed_peers_not_available);
        } else if (totalCount == 0) {
            countPref.setSummary(R.string.distributed_peers_empty);
        } else {
            countPref.setSummary(getString(R.string.distributed_peers_count, verifiedCount, totalCount));
        }
    }

    private static String formatHex(String hex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) sb.append(' ');
            int end = Math.min(i + 4, hex.length());
            sb.append(hex, i, end);
        }
        return sb.toString();
    }
}
