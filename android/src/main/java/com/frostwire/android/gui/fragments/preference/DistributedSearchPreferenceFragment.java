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
import android.content.ContentResolver;
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
import com.frostwire.platform.Platforms;
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
    /** Bounded retries when libtorrent homeDir is not ready yet on cold start. */
    private int identityPathRetries;

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
                    || text.equals(getString(R.string.distributed_identity_not_available))
                    || text.equals(getString(R.string.distributed_identity_loading))
                    || text.equals(getString(R.string.distributed_identity_loading_details))) {
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
            try {
                homeDir = Platforms.get().systemPaths().libtorrent();
            } catch (Throwable ignored) {
                return null;
            }
        }
        if (homeDir == null) {
            return null;
        }
        return new File(homeDir, RelayConstants.IDENTITY_FILE);
    }

    private static boolean identityFilePresent() {
        File f = identityFile();
        return f != null && f.isFile() && f.length() > 0;
    }

    private void refreshIdentityInfo() {
        // Instant paint from wiring if already preloaded — no flash of empty state.
        IdentityKeys live = SearchEngine.DISTRIBUTED_WIRING.identity();
        if (live != null) {
            applyIdentityToUi(live, -1, -1);
        } else {
            // Never show "Not initialized" until disk has been checked.
            showIdentityLoadingState();
        }

        // HIGH_PRIORITY: do not queue behind MISC (LocalIndex / IceBridge startup).
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.HIGH_PRIORITY, () -> {
            IdentityKeys identity = resolveIdentity();
            if (identity != null && SearchEngine.DISTRIBUTED_WIRING.identity() == null) {
                SearchEngine.DISTRIBUTED_WIRING.identity(identity);
            }
            final boolean filePresent = identityFilePresent();
            final long karma = identity != null ? readKarmaScore(identity) : -1;
            final int shared = identity != null ? readSharedCount() : -1;
            final IdentityKeys result = identity;
            SystemUtils.postToUIThread(() -> {
                if (result != null) {
                    identityPathRetries = 0;
                    applyIdentityToUi(result, karma, shared);
                } else if (filePresent) {
                    // File exists but load failed — still not "missing identity".
                    showIdentityLoadingState();
                    LOG.warn("identity.dat present but resolve returned null");
                } else if (identityFile() == null && identityPathRetries < 20) {
                    // Paths not ready yet — keep loading, schedule a retry.
                    identityPathRetries++;
                    showIdentityLoadingState();
                    SystemUtils.postToUIThreadDelayed(this::refreshIdentityInfo, 300);
                } else {
                    identityPathRetries = 0;
                    applyIdentityToUi(null, -1, -1);
                }
            });
        });
    }

    /**
     * Apply identity fields on the UI thread. Prefer calling this with the
     * freshly generated/imported keys so the screen updates even if a concurrent
     * stack restart has not yet re-wired {@link SearchEngine#DISTRIBUTED_WIRING}.
     *
     * @param karma  endorsements, or {@code < 0} to show a loading placeholder
     * @param shared torrent count, or {@code < 0} to show a loading placeholder
     */
    private void applyIdentityToUi(IdentityKeys identity, long karma, int shared) {
        if (!isAdded()) {
            return;
        }
        updateIdentityRows(identity, karma, shared);
        updateActionButtonLabels(identity != null);
    }

    private void showIdentityLoadingState() {
        if (!isAdded()) {
            return;
        }
        Preference nodeIdPref = findPreference("frostwire.prefs.distributed.identity.node_id");
        Preference fingerprintPref = findPreference("frostwire.prefs.distributed.identity.fingerprint");
        Preference pubkeyPref = findPreference("frostwire.prefs.distributed.identity.public_key");
        Preference karmaPref = findPreference("frostwire.prefs.distributed.identity.karma");
        Preference difficultyPref = findPreference("frostwire.prefs.distributed.identity.difficulty");
        Preference sharedPref = findPreference("frostwire.prefs.distributed.identity.shared_count");
        setSummary(nodeIdPref, R.string.distributed_identity_loading);
        setSummary(fingerprintPref, R.string.distributed_identity_loading);
        setSummary(pubkeyPref, R.string.distributed_identity_loading);
        setSummary(karmaPref, R.string.distributed_identity_loading_details);
        setSummary(difficultyPref, R.string.distributed_identity_loading_details);
        setSummary(sharedPref, R.string.distributed_identity_loading_details);
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
            if (karma < 0) {
                karmaPref.setSummary(R.string.distributed_identity_loading_details);
            } else {
                karmaPref.setSummary(getString(R.string.distributed_identity_karma_score, karma)
                        + "\n" + tap);
            }
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
            if (shared < 0) {
                sharedPref.setSummary(R.string.distributed_identity_loading_details);
            } else {
                sharedPref.setSummary(getString(R.string.distributed_identity_shared_torrents, shared));
            }
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
        // Progress text must set expectations: difficulty-20 PoW is ~1M Ed25519
        // attempts (tens of seconds on phones). Dialog is dismissed as soon as
        // keys are written — stack restart must not hold the spinner forever.
        ProgressDialog progress = ProgressDialog.show(
                requireContext(),
                getString(R.string.distributed_identity_initialize),
                getString(R.string.distributed_identity_initializing),
                true,
                false);

        // HIGH_PRIORITY: dedicated thread — never queue behind MISC (IceBridge
        // start/stop, peer discovery glue, etc.).
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.HIGH_PRIORITY, () -> {
            try {
                File file = identityFile();
                if (file == null) {
                    throw new IllegalStateException("libtorrent homeDir not available");
                }
                LOG.info("Identity generate: mining PoW difficulty="
                        + KarmaConstants.IDENTITY_DIFFICULTY
                        + " path=" + file.getAbsolutePath());
                long t0 = System.currentTimeMillis();
                IdentityKeys keys = IdentityLifecycle.generateAndInstall(
                        file, KarmaConstants.IDENTITY_DIFFICULTY);
                SearchEngine.DISTRIBUTED_WIRING.identity(keys);
                LOG.info("Identity generate: installed in "
                        + (System.currentTimeMillis() - t0) + " ms nodeId="
                        + Hex.encode(keys.nodeId()));
                final IdentityKeys installed = keys;
                final int shared = readSharedCount();

                // Success UI first — paint the known keys immediately so the
                // preference rows never stay stuck on "Not initialized".
                SystemUtils.postToUIThread(() -> {
                    dismiss(progress);
                    busy = false;
                    toast(R.string.distributed_identity_init_ok);
                    applyIdentityToUi(installed, 0, shared);
                });
                // Reload IceBridge with the new identity file (best-effort),
                // then refresh karma / peers / stack status.
                restartRelayStack(this::refreshAll);
            } catch (Throwable t) {
                LOG.error("Failed to generate identity", t);
                SystemUtils.postToUIThread(() -> {
                    dismiss(progress);
                    busy = false;
                    toast(getString(R.string.distributed_identity_failed, t.getMessage()));
                    refreshAll();
                });
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
                final IdentityKeys installed = keys;
                SystemUtils.postToUIThread(() -> {
                    toast(R.string.distributed_identity_restore_ok);
                    applyIdentityToUi(installed, 0, readSharedCount());
                    restartRelayStack(this::refreshAll);
                });
            } catch (Throwable t) {
                LOG.error("Failed to restore from seed", t);
                SystemUtils.postToUIThread(() ->
                        toast(getString(R.string.distributed_identity_invalid_seed, t.getMessage())));
            }
        });
    }

    private void onExportUri(Uri uri) {
        if (uri == null) {
            return;
        }
        // Capture resolver on the UI thread; CreateDocument already left a 0-byte
        // placeholder — we must open with "wt" and write the full payload.
        final ContentResolver resolver = requireContext().getContentResolver();
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.HIGH_PRIORITY, () -> {
            try {
                IdentityKeys identity = resolveIdentity();
                if (identity == null) {
                    throw new IllegalStateException(getString(R.string.distributed_identity_no_identity));
                }
                byte[] bytes = IdentityLifecycle.exportBytes(identity);
                if (bytes.length < 64) {
                    throw new IllegalStateException("encoded identity too small: " + bytes.length);
                }
                // "wt" truncates and opens for write — required so SAF Downloads
                // does not leave the empty CreateDocument stub.
                try (OutputStream out = resolver.openOutputStream(uri, "wt")) {
                    if (out == null) {
                        throw new java.io.IOException("could not open export destination");
                    }
                    out.write(bytes);
                    out.flush();
                }
                LOG.info("Identity export: wrote " + bytes.length + " bytes to " + uri);
                SystemUtils.postToUIThread(() -> toast(R.string.distributed_identity_export_ok));
            } catch (Throwable t) {
                LOG.error("Export failed", t);
                SystemUtils.postToUIThread(() ->
                        toast(getString(R.string.distributed_identity_failed, t.getMessage())));
            }
        });
    }

    private void onImportUri(Uri uri) {
        if (uri == null) {
            return;
        }
        final ContentResolver resolver = requireContext().getContentResolver();
        final File cacheDir = requireContext().getCacheDir();
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.HIGH_PRIORITY, () -> {
            try {
                File temp = new File(cacheDir, "import-identity.dat");
                try (InputStream in = resolver.openInputStream(uri);
                     OutputStream out = new FileOutputStream(temp)) {
                    if (in == null) {
                        throw new java.io.IOException("could not open import source");
                    }
                    long n = copyStream(in, out);
                    if (n < 64) {
                        throw new java.io.IOException("import file too small: " + n + " bytes");
                    }
                }
                File dest = identityFile();
                if (dest == null) {
                    throw new IllegalStateException("libtorrent homeDir not available");
                }
                IdentityKeys imported = IdentityLifecycle.importFromFile(temp, dest);
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                SearchEngine.DISTRIBUTED_WIRING.identity(imported);
                final IdentityKeys keys = imported;
                SystemUtils.postToUIThread(() -> {
                    toast(R.string.distributed_identity_import_ok);
                    applyIdentityToUi(keys, 0, readSharedCount());
                    restartRelayStack(this::refreshAll);
                });
            } catch (Throwable t) {
                LOG.error("Import failed", t);
                SystemUtils.postToUIThread(() ->
                        toast(getString(R.string.distributed_identity_failed, t.getMessage())));
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

    /** @return total bytes copied */
    private static long copyStream(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
            total += n;
        }
        out.flush();
        return total;
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
