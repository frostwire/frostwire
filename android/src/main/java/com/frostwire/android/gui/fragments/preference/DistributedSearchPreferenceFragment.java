/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments.preference;

import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;

import com.frostwire.android.R;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.gui.services.EngineForegroundService;
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
        setupInitializeButton();
        setupRefreshButton();
        refreshIdentityInfo();
        refreshPeerList();
        refreshStackStatus();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshIdentityInfo();
        refreshPeerList();
        refreshStackStatus();
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

    private void setupInitializeButton() {
        ButtonActionPreference initBtn =
                findPreference("frostwire.prefs.distributed.identity.initialize");
        if (initBtn == null) {
            return;
        }
        initBtn.setOnPreferenceClickListener(preference -> {
            EngineForegroundService svc = EngineForegroundService.getInstance();
            if (svc == null) {
                Toast.makeText(requireContext(),
                        R.string.distributed_identity_init_failed, Toast.LENGTH_LONG).show();
                return true;
            }
            Toast.makeText(requireContext(),
                    R.string.distributed_identity_initializing, Toast.LENGTH_SHORT).show();
            // Force restart so a previous failed partial start is cleared.
            svc.ensureRelayStack(true, () -> {
                if (getActivity() == null) {
                    return;
                }
                boolean ok = SearchEngine.DISTRIBUTED_WIRING.identity() != null
                        && SearchEngine.DISTRIBUTED_WIRING.searchTransport() != null;
                Toast.makeText(requireContext(),
                        ok ? R.string.distributed_identity_init_ok
                                : R.string.distributed_identity_init_failed,
                        Toast.LENGTH_LONG).show();
                refreshIdentityInfo();
                refreshPeerList();
                refreshStackStatus();
            });
            return true;
        });
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
                refreshStackStatus();
                refreshIdentityInfo();
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

    private void refreshStackStatus() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            EngineForegroundService svc = EngineForegroundService.getInstance();
            boolean running = svc != null && svc.isRelayStackRunning();
            boolean transport = SearchEngine.DISTRIBUTED_WIRING.searchTransport() != null;
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (getActivity() == null) return;
                Preference statusPref = findPreference("frostwire.prefs.distributed.stack.status");
                if (statusPref == null) return;
                if (running && transport) {
                    statusPref.setSummary(R.string.distributed_stack_running);
                } else if (SearchEngine.DISTRIBUTED_WIRING.identity() != null && !transport) {
                    statusPref.setSummary(R.string.distributed_identity_init_failed);
                } else {
                    statusPref.setSummary(R.string.distributed_stack_not_running);
                }
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
