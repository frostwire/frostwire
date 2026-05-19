/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.fragments.preference;

import com.frostwire.android.core.Constants;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class TorrentAdvancedJlibtorrentSettingsSurfaceTest {

    @Test
    public void torrentPreferencesExposeAdvancedJlibtorrentSettings() throws IOException {
        String xml = readProjectFile("res/xml/settings_torrent.xml");

        assertTrue(xml.contains("@string/advanced_bittorrent_settings"));
        assertTrue(xml.contains("android:key=\"" + Constants.PREF_KEY_NETWORK_ALLOW_MULTIPLE_CONNECTIONS_PER_PID + "\""));
        assertTrue(xml.contains("android:key=\"" + Constants.PREF_KEY_NETWORK_NATPMP_GATEWAY + "\""));
        assertTrue(xml.contains("android:key=\"" + Constants.PREF_KEY_NETWORK_NATPMP_LEASE_DURATION + "\""));
        assertTrue(xml.contains("android:defaultValue=\"3600\""));
    }

    @Test
    public void torrentPreferenceFragmentAppliesAdvancedSettingsLive() throws IOException {
        String source = readProjectFile("src/main/java/com/frostwire/android/gui/fragments/preference/TorrentPreferenceFragment.java");

        assertTrue(source.contains("setupAdvancedJlibtorrentOptions()"));
        assertTrue(source.contains("settings.allowMultipleConnectionsPerPid"));
        assertTrue(source.contains("settings.natpmpGateway"));
        assertTrue(source.contains("settings.natpmpLeaseDuration"));
        assertTrue(source.contains("R.string.natpmp_lease_duration_invalid"));
    }

    private static String readProjectFile(String relativePath) throws IOException {
        Path projectRoot = Paths.get(".").toAbsolutePath().normalize();
        Path sourcePath = projectRoot.resolve(relativePath);
        if (!Files.exists(sourcePath)) {
            String classFile = TorrentAdvancedJlibtorrentSettingsSurfaceTest.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getFile();
            Path buildDir = Paths.get(classFile).normalize();
            projectRoot = buildDir.getParent().getParent().getParent().getParent();
            sourcePath = projectRoot.resolve(relativePath);
        }
        return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
    }
}
