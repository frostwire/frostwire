/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.andrew.apollo.ui.activities;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/**
 * The now-playing screen registered its playback receiver in onStart but only unregistered it in
 * onDestroy, never stopped its 250-500 ms refresh loop (mIsPaused was never set), and registered
 * the receiver exported. Source check: the activity needs the whole music UI to be built.
 */
public class AudioPlayerActivityLifecycleTest {

  private static String method(String source, String signature) {
    String rest = source.substring(source.indexOf(signature));
    int end = rest.indexOf("@Override");
    return end < 0 ? rest : rest.substring(0, end);
  }

  private static String source() throws Exception {
    return new String(
            Files.readAllBytes(
                Path.of(System.getProperty("user.dir"))
                    .resolve(
                        "apollo/src/com/andrew/apollo/ui/activities/AudioPlayerActivity.java")),
            StandardCharsets.UTF_8)
        .replaceAll("\\s+", "");
  }

  @Test
  public void stoppingEndsTheRefreshLoopAndTheReceiver() throws Exception {
    String onStop = method(source(), "protectedvoidonStop()");
    assertTrue(onStop.contains("mIsPaused=true"));
    assertTrue(onStop.contains("removeMessages(REFRESH_TIME)"));
    assertTrue(onStop.contains("unregisterPlaybackStatus(this,mPlaybackStatus)"));
  }

  @Test
  public void startingResumesTheRefreshLoop() throws Exception {
    assertTrue(method(source(), "protectedvoidonStart()").contains("mIsPaused=false"));
  }

  @Test
  public void theReceiverOnlyHearsThisApp() throws Exception {
    assertTrue(
        method(source(), "privatevoidsafeRegisterReceiver").contains("RECEIVER_NOT_EXPORTED"));
  }
}
