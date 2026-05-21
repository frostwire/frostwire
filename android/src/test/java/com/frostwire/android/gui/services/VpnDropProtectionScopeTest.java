package com.frostwire.android.gui.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class VpnDropProtectionScopeTest {

    @Test
    public void vpnGuardPausesTorrentsWithoutStoppingHttpSearchServices() throws Exception {
        String source = readProjectFile("src/main/java/com/frostwire/android/gui/services/EngineBroadcastReceiver.java");
        String vpnGuardBlock = blockStartingAt(source, "VPN guard enabled but no VPN detected. Pausing torrents.");

        assertTrue(vpnGuardBlock.contains("TransferManager.instance().pauseTorrents();"));
        assertFalse(vpnGuardBlock.contains("Engine.instance().stopServices(true);"));
    }

    @Test
    public void enablingVpnGuardDoesNotDisconnectEngineServices() throws Exception {
        String source = readProjectFile("src/main/java/com/frostwire/android/gui/fragments/preference/ApplicationPreferencesFragment.java");
        String vpnPreferenceBlock = blockStartingAt(source, "private void setupVPNRequirementOption()");

        assertTrue(vpnPreferenceBlock.contains("ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY, newVal);"));
        assertTrue(vpnPreferenceBlock.contains("TransferManager.instance().pauseTorrents();"));
        assertTrue(vpnPreferenceBlock.contains("SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER,"));
        assertTrue(vpnPreferenceBlock.contains("TransferManager.instance().resumeResumableTransfers()"));
        assertFalse(vpnPreferenceBlock.contains("disconnect();"));
        assertFalse(vpnPreferenceBlock.contains("setChecked(findPreference(\"frostwire.prefs.internal.connect_disconnect\"), false, false);"));
    }

    @Test
    public void vpnGuardIsAppliedAtBittorrentTransferEntryPointsOnly() throws Exception {
        String source = readProjectFile("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java");

        assertTrue(source.contains("isBittorrentSearchResultAndVpnRequired(sr)"));
        assertTrue(source.contains("return sr instanceof TorrentSearchResult && isBittorrentOnVpnOnlyAndNoVpn();"));
        assertTrue(source.contains("public boolean isBittorrentOnVpnOnlyAndNoVpn()"));
        assertTrue(source.contains("return new InvalidBittorrentDownload(R.string.cannot_start_engine_without_vpn, sr);"));
        assertFalse(source.contains("return new InvalidDownload(R.string.cannot_start_engine_without_vpn"));
    }

    @Test
    public void vpnGuardBlocksManualSeedingWithToast() throws Exception {
        String source = readProjectFile("src/main/java/com/frostwire/android/gui/adapters/menu/SeedAction.java");
        String onClickBlock = blockStartingAt(source, "public void onClick(Context context)");
        String seedEmBlock = blockStartingAt(source, "private void seedEm()");
        String onSeedingEnabledBlock = blockStartingAt(source, "private void onSeedingEnabled()");

        assertTrue(onClickBlock.contains("TransferManager.instance().isBittorrentOnVpnOnlyAndNoVpn()"));
        assertTrue(onClickBlock.contains("UIUtils.showLongMessage(getContext(), R.string.cannot_start_engine_without_vpn);"));
        assertTrue(seedEmBlock.contains("!TransferManager.instance().isBittorrentOnVpnOnlyAndNoVpn()"));
        assertTrue(onSeedingEnabledBlock.contains("TransferManager.instance().isBittorrentOnVpnOnlyAndNoVpn()"));
        assertTrue(onSeedingEnabledBlock.contains("UIUtils.showLongMessage(getContext(), R.string.cannot_start_engine_without_vpn);"));
    }

    private static String blockStartingAt(String source, String marker) {
        int start = source.indexOf(marker);
        if (start < 0) {
            return "";
        }
        int end = source.indexOf("\n    }\n", start);
        return end > start ? source.substring(start, end) : source.substring(start);
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path root = Path.of(System.getProperty("user.dir"));
        Path file = root.resolve(relativePath);
        if (!Files.exists(file)) {
            file = root.resolve("android").resolve(relativePath);
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
