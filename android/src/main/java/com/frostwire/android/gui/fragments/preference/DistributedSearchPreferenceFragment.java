/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments.preference;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.frostwire.android.R;
import com.frostwire.android.gui.SearchEngine;
import com.frostwire.android.gui.services.EngineForegroundService;
import com.frostwire.android.gui.views.AbstractPreferenceFragment;
import com.frostwire.android.gui.views.preference.ButtonActionPreference;
import com.frostwire.android.search.AndroidKarmaChainStore;
import com.frostwire.android.search.AndroidLocalIndex;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.search.relay.IdentityKeys;
import com.frostwire.search.relay.IdentityLifecycle;
import com.frostwire.search.relay.KarmaConstants;
import com.frostwire.search.relay.PeerDirectory;
import com.frostwire.search.relay.PeerKarmaCache;
import com.frostwire.search.relay.RelayConstants;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Android UI for identity lifecycle. Business logic is
 * {@link IdentityLifecycle} (shared with desktop).
 */
public final class DistributedSearchPreferenceFragment extends AbstractPreferenceFragment {

    private static final Logger LOG = Logger.getLogger(DistributedSearchPreferenceFragment.class);

    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;
    private volatile boolean busy;

    public DistributedSearchPreferenceFragment() {
        super(R.xml.settings_distributed_search);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                this::onExportUri);
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onImportUri);
    }

    @Override
    protected void initComponents() {
        setupDistributedToggle();
        setupCopyableFields();
        setupActionButtons();
        setupRefreshButton();
        refreshAll();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshAll();
    }

    private void refreshAll() {
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

    private void setupCopyableFields() {
        wireCopyOnClick("frostwire.prefs.distributed.identity.node_id", true);
        wireCopyOnClick("frostwire.prefs.distributed.identity.fingerprint", true);
        wireCopyOnClick("frostwire.prefs.distributed.identity.public_key", true);
        wireCopyOnClick("frostwire.prefs.distributed.identity.karma", false);
    }

    private void wireCopyOnClick(String key, boolean stripSpaces) {
        Preference pref = findPreference(key);
        if (pref == null) {
            return;
        }
        pref.setOnPreferenceClickListener(p -> {
            CharSequence summary = p.getSummary();
            if (summary == null) {
                return true;
            }
            String text = summary.toString();
            if (text.equals(getString(R.string.distributed_identity_not_initialized))
                    || text.equals(getString(R.string.distributed_identity_not_available))) {
                return true;
            }
            // Drop UI helper line ("Tap to copy") and optional grouping spaces.
            int nl = text.indexOf('\n');
            if (nl >= 0) {
                text = text.substring(0, nl);
            }
            if (stripSpaces) {
                text = text.replace(" ", "");
            }
            copyToClipboard(text);
            return true;
        });
    }

    private void setupActionButtons() {
        ButtonActionPreference initBtn =
                findPreference("frostwire.prefs.distributed.identity.initialize");
        if (initBtn != null) {
            initBtn.setOnActionListener(v -> onInitializeOrCreateNew());
        }

        ButtonActionPreference showSeed =
                findPreference("frostwire.prefs.distributed.identity.show_seed");
        if (showSeed != null) {
            showSeed.setOnActionListener(v -> showSeedPhrase());
        }

        ButtonActionPreference restoreSeed =
                findPreference("frostwire.prefs.distributed.identity.restore_seed");
        if (restoreSeed != null) {
            restoreSeed.setOnActionListener(v -> restoreFromSeedPhrase());
        }

        ButtonActionPreference exportBtn =
                findPreference("frostwire.prefs.distributed.identity.export");
        if (exportBtn != null) {
            exportBtn.setOnActionListener(v -> {
                if (resolveIdentity() == null) {
                    toast(R.string.distributed_identity_no_identity);
                    return;
                }
                exportLauncher.launch("frostwire-identity.dat");
            });
        }

        ButtonActionPreference importBtn =
                findPreference("frostwire.prefs.distributed.identity.import_file");
        if (importBtn != null) {
            importBtn.setOnActionListener(v -> confirmThen(
                    R.string.distributed_identity_import_confirm,
                    () -> importLauncher.launch(new String[]{"*/*", "application/octet-stream"})));
        }

        ButtonActionPreference copyAll =
                findPreference("frostwire.prefs.distributed.identity.copy_all");
        if (copyAll != null) {
            copyAll.setOnActionListener(v -> copyIdentitySummary());
        }
    }

    private void setupRefreshButton() {
        ButtonActionPreference refreshBtn =
                findPreference("frostwire.prefs.distributed.peers.refresh");
        if (refreshBtn != null) {
            refreshBtn.setOnActionListener(v -> refreshAll());
        }
    }

    private IdentityKeys resolveIdentity() {
        return IdentityLifecycle.resolve(
                SearchEngine.DISTRIBUTED_WIRING.identity(), identityFile());
    }

    private static File identityFile() {
        File homeDir = BTEngine.ctx != null ? BTEngine.ctx.homeDir : null;
        if (homeDir == null) {
            return null;
        }
        return new File(homeDir, RelayConstants.IDENTITY_FILE);
    }

    private void refreshIdentityInfo() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            IdentityKeys identity = resolveIdentity();
            long karma = identity != null ? readKarmaScore(identity) : -1;
            int shared = readSharedCount();
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(() -> {
                if (getActivity() == null) {
                    return;
                }
                updateIdentityRows(identity, karma, shared);
                updateActionButtonLabels(identity != null);
            });
        });
    }

    private void updateActionButtonLabels(boolean hasIdentity) {
        ButtonActionPreference initBtn =
                findPreference("frostwire.prefs.distributed.identity.initialize");
        if (initBtn != null) {
            initBtn.setTitle(hasIdentity
                    ? R.string.distributed_identity_create_new
                    : R.string.distributed_identity_initialize);
        }
    }

    private void updateIdentityRows(IdentityKeys identity, long karma, int shared) {
        Preference nodeIdPref = findPreference("frostwire.prefs.distributed.identity.node_id");
        Preference fingerprintPref = findPreference("frostwire.prefs.distributed.identity.fingerprint");
        Preference pubkeyPref = findPreference("frostwire.prefs.distributed.identity.public_key");
        Preference karmaPref = findPreference("frostwire.prefs.distributed.identity.karma");
        Preference difficultyPref = findPreference("frostwire.prefs.distributed.identity.difficulty");
        Preference sharedPref = findPreference("frostwire.prefs.distributed.identity.shared_count");

        if (identity == null) {
            setSummary(nodeIdPref, R.string.distributed_identity_not_initialized);
            setSummary(fingerprintPref, R.string.distributed_identity_not_initialized);
            setSummary(pubkeyPref, R.string.distributed_identity_not_initialized);
            setSummary(karmaPref, R.string.distributed_identity_not_available);
            setSummary(difficultyPref, R.string.distributed_identity_not_initialized);
            setSummary(sharedPref, R.string.distributed_identity_not_available);
            return;
        }

        String nodeIdHex = Hex.encode(identity.nodeId());
        String pubHex = Hex.encode(identity.ed25519PubRaw());
        String tap = getString(R.string.distributed_identity_tap_to_copy);
        if (nodeIdPref != null) {
            nodeIdPref.setSummary(IdentityLifecycle.formatGroupedHex(nodeIdHex) + "\n" + tap);
        }
        if (fingerprintPref != null) {
            fingerprintPref.setSummary(IdentityLifecycle.formatGroupedHex(pubHex) + "\n" + tap);
        }
        if (pubkeyPref != null) {
            pubkeyPref.setSummary(pubHex + "\n" + tap);
        }
        if (karmaPref != null) {
            karmaPref.setSummary(getString(R.string.distributed_identity_karma_score, Math.max(0, karma))
                    + "\n" + tap);
        }
        if (difficultyPref != null) {
            int difficulty = IdentityLifecycle.difficultyBits(identity);
            if (IdentityLifecycle.meetsDifficultyRequirement(identity)) {
                difficultyPref.setSummary(getString(R.string.distributed_identity_difficulty_meets, difficulty));
            } else {
                difficultyPref.setSummary(getString(
                        R.string.distributed_identity_difficulty_below,
                        difficulty, KarmaConstants.IDENTITY_DIFFICULTY));
            }
        }
        if (sharedPref != null) {
            sharedPref.setSummary(getString(R.string.distributed_identity_shared_torrents, Math.max(0, shared)));
        }
    }

    private long readKarmaScore(IdentityKeys identity) {
        try {
            Context ctx = getContext();
            if (ctx == null) {
                return 0;
            }
            try (AndroidKarmaChainStore store =
                         new AndroidKarmaChainStore(ctx, AndroidLocalIndex.DEFAULT_DB_NAME)) {
                return PeerKarmaCache.computeScore(store.loadChain(identity.ed25519PubRaw()).entries());
            }
        } catch (Throwable t) {
            LOG.warn("Failed to read karma score", t);
            return 0;
        }
    }

    private int readSharedCount() {
        try {
            var index = SearchEngine.LOCAL_WIRING.localIndex();
            if (index != null) {
                return index.size();
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private void onInitializeOrCreateNew() {
        if (busy) {
            return;
        }
        boolean replacing = resolveIdentity() != null;
        confirmThen(
                replacing
                        ? R.string.distributed_identity_confirm_create_new
                        : R.string.distributed_identity_confirm_initialize,
                () -> generateAndSaveIdentity());
    }

    private void generateAndSaveIdentity() {
        if (busy) {
            return;
        }
        busy = true;
        ProgressDialog progress = ProgressDialog.show(
                requireContext(),
                getString(R.string.distributed_identity_initialize),
                getString(R.string.distributed_identity_initializing),
                true,
                false);

        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            try {
                File file = identityFile();
                if (file == null) {
                    throw new IllegalStateException("libtorrent homeDir not available");
                }
                IdentityKeys keys = IdentityLifecycle.generateAndInstall(
                        file, KarmaConstants.IDENTITY_DIFFICULTY);
                SearchEngine.DISTRIBUTED_WIRING.identity(keys);
                restartRelayStack(() -> {
                    dismiss(progress);
                    busy = false;
                    toast(R.string.distributed_identity_init_ok);
                    refreshAll();
                });
            } catch (Throwable t) {
                LOG.error("Failed to generate identity", t);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        dismiss(progress);
                        busy = false;
                        toast(getString(R.string.distributed_identity_failed, t.getMessage()));
                        refreshAll();
                    });
                } else {
                    busy = false;
                }
            }
        });
    }

    private void showSeedPhrase() {
        IdentityKeys identity = resolveIdentity();
        if (identity == null) {
            toast(R.string.distributed_identity_no_identity);
            return;
        }
        try {
            String mnemonic = IdentityLifecycle.seedPhrase(identity);
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.distributed_identity_seed_title)
                    .setMessage(getString(R.string.distributed_identity_seed_warning)
                            + "\n\n" + mnemonic)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.distributed_identity_seed_copy,
                            (d, w) -> copyToClipboard(mnemonic))
                    .show();
        } catch (Throwable t) {
            LOG.error("Failed to show seed phrase", t);
            toast(getString(R.string.distributed_identity_failed, t.getMessage()));
        }
    }

    private void restoreFromSeedPhrase() {
        confirmThen(R.string.distributed_identity_restore_confirm, () -> {
            final EditText input = new EditText(requireContext());
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setMinLines(3);
            input.setHint(R.string.distributed_identity_restore_prompt);
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.distributed_identity_restore_seed)
                    .setView(input)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        String raw = input.getText() != null ? input.getText().toString() : "";
                        applySeedPhrase(raw);
                    })
                    .show();
        });
    }

    private void applySeedPhrase(String raw) {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            try {
                File file = identityFile();
                if (file == null) {
                    throw new IllegalStateException("libtorrent homeDir not available");
                }
                IdentityKeys keys = IdentityLifecycle.restoreFromSeedPhrase(raw, file);
                SearchEngine.DISTRIBUTED_WIRING.identity(keys);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        toast(R.string.distributed_identity_restore_ok);
                        restartRelayStack(this::refreshAll);
                    });
                }
            } catch (Throwable t) {
                LOG.error("Failed to restore from seed", t);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            toast(getString(R.string.distributed_identity_invalid_seed, t.getMessage())));
                }
            }
        });
    }

    private void onExportUri(Uri uri) {
        if (uri == null) {
            return;
        }
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            try {
                IdentityKeys identity = resolveIdentity();
                if (identity == null) {
                    throw new IllegalStateException(getString(R.string.distributed_identity_no_identity));
                }
                File temp = new File(requireContext().getCacheDir(), "export-identity.dat");
                IdentityLifecycle.exportToFile(identity, temp);
                try (InputStream in = new FileInputStream(temp);
                     OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        throw new java.io.IOException("could not open export destination");
                    }
                    copyStream(in, out);
                }
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> toast(R.string.distributed_identity_export_ok));
                }
            } catch (Throwable t) {
                LOG.error("Export failed", t);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            toast(getString(R.string.distributed_identity_failed, t.getMessage())));
                }
            }
        });
    }

    private void onImportUri(Uri uri) {
        if (uri == null) {
            return;
        }
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            try {
                File temp = new File(requireContext().getCacheDir(), "import-identity.dat");
                try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(temp)) {
                    if (in == null) {
                        throw new java.io.IOException("could not open import source");
                    }
                    copyStream(in, out);
                }
                File dest = identityFile();
                if (dest == null) {
                    throw new IllegalStateException("libtorrent homeDir not available");
                }
                IdentityKeys imported = IdentityLifecycle.importFromFile(temp, dest);
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                SearchEngine.DISTRIBUTED_WIRING.identity(imported);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        toast(R.string.distributed_identity_import_ok);
                        restartRelayStack(this::refreshAll);
                    });
                }
            } catch (Throwable t) {
                LOG.error("Import failed", t);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() ->
                            toast(getString(R.string.distributed_identity_failed, t.getMessage())));
                }
            }
        });
    }

    private void copyIdentitySummary() {
        IdentityKeys identity = resolveIdentity();
        if (identity == null) {
            toast(R.string.distributed_identity_no_identity);
            return;
        }
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            long karma = readKarmaScore(identity);
            int shared = readSharedCount();
            String summary = IdentityLifecycle.summaryText(identity, karma, shared);
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> copyToClipboard(summary));
            }
        });
    }

    private void refreshPeerList() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            PeerDirectory directory = SearchEngine.DISTRIBUTED_WIRING.peerDirectory();
            if (getActivity() == null) {
                return;
            }
            int peerCount = directory != null ? directory.size() : -1;
            int verifiedCount = directory != null ? directory.topByTrustVerified(100).size() : -1;
            getActivity().runOnUiThread(() -> {
                if (getActivity() == null) {
                    return;
                }
                Preference countPref = findPreference("frostwire.prefs.distributed.peers.count");
                if (countPref == null) {
                    return;
                }
                if (peerCount < 0) {
                    countPref.setSummary(R.string.distributed_peers_not_available);
                } else if (peerCount == 0) {
                    countPref.setSummary(R.string.distributed_peers_empty);
                } else {
                    countPref.setSummary(getString(
                            R.string.distributed_peers_count, verifiedCount, peerCount));
                }
            });
        });
    }

    private void refreshStackStatus() {
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            EngineForegroundService svc = EngineForegroundService.getInstance();
            boolean running = svc != null && svc.isRelayStackRunning();
            boolean transport = SearchEngine.DISTRIBUTED_WIRING.searchTransport() != null;
            if (getActivity() == null) {
                return;
            }
            getActivity().runOnUiThread(() -> {
                if (getActivity() == null) {
                    return;
                }
                Preference statusPref = findPreference("frostwire.prefs.distributed.stack.status");
                if (statusPref == null) {
                    return;
                }
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

    private void restartRelayStack(Runnable after) {
        EngineForegroundService svc = EngineForegroundService.getInstance();
        if (svc == null) {
            if (after != null && getActivity() != null) {
                getActivity().runOnUiThread(after);
            }
            return;
        }
        svc.ensureRelayStack(true, after);
    }

    private void confirmThen(int messageRes, Runnable onYes) {
        new AlertDialog.Builder(requireContext())
                .setMessage(messageRes)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> onYes.run())
                .show();
    }

    private void copyToClipboard(String text) {
        Context ctx = getContext();
        if (ctx == null || text == null) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("frostwire-identity", text));
            toast(R.string.distributed_identity_copied);
        }
    }

    private void toast(int resId) {
        Context ctx = getContext();
        if (ctx != null) {
            Toast.makeText(ctx, resId, Toast.LENGTH_LONG).show();
        }
    }

    private void toast(String msg) {
        Context ctx = getContext();
        if (ctx != null) {
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show();
        }
    }

    private static void setSummary(Preference pref, int resId) {
        if (pref != null) {
            pref.setSummary(resId);
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        out.flush();
    }

    private static void dismiss(ProgressDialog progress) {
        try {
            if (progress != null && progress.isShowing()) {
                progress.dismiss();
            }
        } catch (Throwable ignored) {
        }
    }
}
