/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.services;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class VpnDropProtectionScopeTest {

  @Test
  public void vpnGuardPausesTorrentsWithoutStoppingHttpSearchServices() throws Exception {
    String source =
        readProjectFile(
            "src/main/java/com/frostwire/android/gui/services/EngineBroadcastReceiver.java");
    String vpnGuardBlock =
        code(blockStartingAt(source, "VPN guard enabled but no VPN detected. Pausing torrents."));

    assertTrue(
        vpnGuardBlock.contains(code("TransferManager.instance().suspendTorrentsForPolicy();")));
    assertTrue(vpnGuardBlock.contains(code("Pausing torrents.")));
    assertTrue(vpnGuardBlock.contains(code("Engine.instance().stopServices(true);")));
    assertTrue(
        vpnGuardBlock.indexOf(code("TransferManager.instance().suspendTorrentsForPolicy();"))
            < vpnGuardBlock.indexOf(code("Engine.instance().stopServices(true);")));
  }

  @Test
  public void pausingTorrentsIncludesSeedingTransfers() throws Exception {
    String source =
        readProjectFile("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java");
    String pauseBlock = code(blockStartingAt(source, "public void pauseTorrents()"));

    assertTrue(pauseBlock.contains(code("d.pause();")));
    assertFalse(pauseBlock.contains(code("if (!d.isSeeding())")));
  }

  @Test
  public void startupGuardStillStartsEngineSoTransfersCanBeRestored() throws Exception {
    String source =
        readProjectFile("src/main/java/com/frostwire/android/gui/activities/MainActivity.java");
    String resumeBlock = code(blockStartingAt(source, "private void mainResume()"));

    assertTrue(resumeBlock.contains(code("Engine.instance().startServices();")));
  }

  @Test
  public void restoredTransfersArePausedAfterSessionRestoreWhenGuardIsActive() throws Exception {
    String source =
        readProjectFile("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java");
    String restoreBlock =
        code(blockStartingAt(source, "BTEngineListenerChain.install(btEngine, engineListener)"));

    assertTrue(restoreBlock.contains(code("isBittorrentOnVpnOnlyAndNoVpn()")));
    assertTrue(restoreBlock.contains(code("suspendTorrentsForPolicy();")));
  }

  @Test
  public void restoredTransferCannotAutoResumeWhenVpnGuardIsActive() throws Exception {
    String source =
        readProjectFile(
            "src/main/java/com/frostwire/android/gui/transfers/UIBittorrentDownload.java");
    String constructorBlock =
        code(
            blockStartingAt(
                source, "public UIBittorrentDownload(TransferManager manager, BTDownload dl)"));

    assertTrue(constructorBlock.contains(code("manager.isBittorrentOnVpnOnlyAndNoVpn()")));
    assertTrue(constructorBlock.contains(code("pauseForPolicy();")));
    assertTrue(constructorBlock.contains(code("cachedState = TransferState.PAUSED;")));

    String updateBlock = code(blockStartingAt(source, "public void updateCachedState()"));
    assertTrue(updateBlock.contains(code("boolean explicitlyPaused = dl.wasPaused();")));
    assertTrue(
        updateBlock.contains(
            code("cachedState = explicitlyPaused ? TransferState.PAUSED : dl.getState();")));
  }

  @Test
  public void enablingVpnGuardDoesNotDisconnectEngineServices() throws Exception {
    String source =
        readProjectFile(
            "src/main/java/com/frostwire/android/gui/fragments/preference/ApplicationPreferencesFragment.java");
    String vpnPreferenceBlock =
        code(blockStartingAt(source, "private void setupVPNRequirementOption()"));

    assertTrue(
        vpnPreferenceBlock.contains(
            code(
                "ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_NETWORK_BITTORRENT_ON_VPN_ONLY, newVal);")));
    assertTrue(
        vpnPreferenceBlock.contains(
            code("() -> TransferManager.instance().suspendTorrentsForPolicy()")));
    assertTrue(
        vpnPreferenceBlock.contains(
            code("SystemUtils.postToHandler(SystemUtils.HandlerThreadName.DOWNLOADER,")));
    assertTrue(
        vpnPreferenceBlock.contains(code("TransferManager.instance().resumeResumableTransfers()")));
    assertTrue(
        vpnPreferenceBlock.contains(code("TransferManager.instance().seedFinishedTransfers()")));
    assertFalse(vpnPreferenceBlock.contains(code("disconnect();")));
    assertFalse(
        vpnPreferenceBlock.contains(
            code(
                "setChecked(findPreference(\"frostwire.prefs.internal.connect_disconnect\"), false, false);")));
  }

  @Test
  public void vpnGuardIsAppliedAtBittorrentTransferEntryPointsOnly() throws Exception {
    String source =
        readProjectFile("src/main/java/com/frostwire/android/gui/transfers/TransferManager.java");

    assertTrue(code(source).contains(code("isBittorrentSearchResultAndVpnRequired(sr)")));
    assertTrue(
        code(source)
            .contains(
                code(
                    "return sr instanceof TorrentSearchResult && isBittorrentOnVpnOnlyAndNoVpn();")));
    assertTrue(code(source).contains(code("public boolean isBittorrentOnVpnOnlyAndNoVpn()")));
    assertTrue(
        code(source)
            .contains(
                code(
                    "return new InvalidBittorrentDownload(R.string.cannot_start_engine_without_vpn, sr);")));
    assertFalse(
        code(source)
            .contains(code("return new InvalidDownload(R.string.cannot_start_engine_without_vpn")));
  }

  @Test
  public void vpnGuardBlocksManualSeedingWithToast() throws Exception {
    String source =
        readProjectFile("src/main/java/com/frostwire/android/gui/adapters/menu/SeedAction.java");
    String onClickBlock = code(blockStartingAt(source, "public void onClick(Context context)"));
    String seedEmBlock = code(blockStartingAt(source, "private void seedEm()"));
    String onSeedingEnabledBlock = code(blockStartingAt(source, "private void onSeedingEnabled()"));

    assertTrue(
        onClickBlock.contains(code("TransferManager.instance().isBittorrentOnVpnOnlyAndNoVpn()")));
    assertTrue(
        onClickBlock.contains(
            code(
                "UIUtils.showLongMessage(getContext(), R.string.cannot_start_engine_without_vpn);")));
    assertTrue(
        seedEmBlock.contains(code("!TransferManager.instance().isBittorrentOnVpnOnlyAndNoVpn()")));
    assertTrue(
        onSeedingEnabledBlock.contains(
            code("TransferManager.instance().isBittorrentOnVpnOnlyAndNoVpn()")));
    assertTrue(
        onSeedingEnabledBlock.contains(
            code(
                "UIUtils.showLongMessage(getContext(), R.string.cannot_start_engine_without_vpn);")));
  }

  private static String blockStartingAt(String source, String marker) {
    int start = source.indexOf(marker);
    if (start < 0) {
      return "";
    }
    int openingBrace = source.indexOf('{', start);
    if (openingBrace < 0) {
      return source.substring(start);
    }
    int depth = 0;
    for (int i = openingBrace; i < source.length(); i++) {
      char c = source.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth == 0) {
        return source.substring(start, i);
      }
    }
    return source.substring(start);
  }

  private static String readProjectFile(String relativePath) throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relativePath);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relativePath);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }

  /**
   * The text without whitespace, so the checks don't depend on how google-java-format wraps a
   * statement.
   */
  private static String code(String s) {
    return s.replaceAll("\\s+", "");
  }
}
