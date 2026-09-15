/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.android.gui.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Only real audio files may open in Apollo (the in-app music player). Video containers that can
 * also carry audio (webm, mp4, 3gp) must NOT be classified as audio, otherwise opening a .webm
 * movie launches the music player instead of suggesting a video player.
 */
public class UIUtilsAudioClassificationTest {

  @Test
  public void realAudioExtensionsAreAudio() {
    for (String ext : new String[] {
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma", "flac", "mka", "amr", "m4b"
    }) {
      assertTrue(ext + " must be audio", UIUtils.isAudioFile("movie." + ext));
    }
  }

  @Test
  public void videoContainersAreNotAudio() {
    for (String ext : new String[] {"webm", "mp4", "3gp", "3gpp", "mkv", "avi", "mov", "m4v", "ts"}) {
      assertFalse(ext + " must not open in the audio player", UIUtils.isAudioFile("movie." + ext));
    }
  }

  @Test
  public void caseInsensitiveAndNullSafe() {
    assertTrue(UIUtils.isAudioFile("SONG.MP3"));
    assertFalse(UIUtils.isAudioFile("MOVIE.WEBM"));
    assertFalse(UIUtils.isAudioFile(null));
  }
}
