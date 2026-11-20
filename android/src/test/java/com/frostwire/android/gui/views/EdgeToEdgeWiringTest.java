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

package com.frostwire.android.gui.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Targeting Android 15+ makes every window edge-to-edge and Android 16 removes the opt-out, so
 * every screen must keep its content clear of the system bars. The base activity does it for all of
 * them; the manifest must not opt out and must not pan the window under the keyboard (the keyboard
 * inset is already applied as padding, panning would shift the screen twice).
 */
public class EdgeToEdgeWiringTest {

  @Test
  public void baseActivityInstallsEdgeToEdgeInsetsOnEveryScreen() throws Exception {
    String source = read("src/main/java/com/frostwire/android/gui/views/AbstractActivity.java");
    assertTrue(source.contains("EdgeToEdge.enable("));
    assertTrue(source.contains("EdgeToEdgeInsets.install("));
    int setContentView = source.indexOf("setContentView(layoutResId);");
    int install = source.indexOf("installEdgeToEdge();", setContentView);
    assertTrue("insets are installed right after the content exists", install > setContentView);
  }

  @Test
  public void drawerScreenPadsOnlyTheContentColumn() throws Exception {
    String source = read("src/main/java/com/frostwire/android/gui/activities/MainActivity.java");
    assertTrue(source.contains("protected View getInsetsTarget()"));
    assertTrue(source.contains("R.id.activity_main_parent_layout"));
  }

  @Test
  public void manifestDoesNotOptOutAndDoesNotPan() throws Exception {
    String manifest = read("AndroidManifest.xml");
    assertFalse(
        "the opt-out is ignored at targetSdk 36",
        manifest.contains("windowOptOutEdgeToEdgeEnforcement"));
    assertFalse(
        "adjustPan on top of the keyboard padding shifts the screen twice",
        manifest.contains("adjustPan"));
  }

  @Test
  public void portraitScreensKeepTheirLayoutOnLargeScreensUntilTheyAreAdaptive() throws Exception {
    // Targeting 36 ignores screenOrientation on >= 600dp; the portrait-only layouts opt out.
    String manifest = read("AndroidManifest.xml");
    assertTrue(manifest.contains("android:screenOrientation=\"portrait\""));
    assertTrue(manifest.contains("android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY"));
  }

  @Test
  public void everyActivityScreenIsBuiltOnTheBaseActivityOrHasNoWindow() throws Exception {
    // Activities that extend AppCompatActivity/Activity directly do not get the insets.
    assertEquals(
        "only the transparent/no-display trampolines may skip the base activity",
        2,
        countDirectActivitySubclasses());
  }

  private static int countDirectActivitySubclasses() throws IOException {
    int count = 0;
    for (String file :
        new String[] {
          "apollo/src/com/andrew/apollo/ui/activities/ShortcutActivity.java",
          "apollo/src/com/andrew/apollo/ui/activities/AudioPlayerNotificationActivity.java"
        }) {
      String source = read(file);
      if (source.contains("extends AppCompatActivity") || source.contains("extends Activity")) {
        count++;
      }
    }
    return count;
  }

  private static String read(String relativePath) throws IOException {
    Path root = Path.of(System.getProperty("user.dir"));
    Path file = root.resolve(relativePath);
    if (!Files.exists(file)) {
      file = root.resolve("android").resolve(relativePath);
    }
    return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
  }
}
