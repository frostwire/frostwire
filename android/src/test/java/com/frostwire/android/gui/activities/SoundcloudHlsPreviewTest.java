/*
 *     Copyright (c) 2026, FrostWire(R). All rights reserved.
 *     Licensed under GPL v3. See LICENSE file.
 */
package com.frostwire.android.gui.activities;

import static org.junit.Assert.assertTrue;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class, sdk = 28)
public class SoundcloudHlsPreviewTest {
  @Test
  public void previewPlayersDefaultFactorySelectsAnHlsSourceForSoundcloud() {
    var factory = new DefaultMediaSourceFactory(RuntimeEnvironment.getApplication());
    var media = MediaItem.fromUri("https://playback.media-streaming.soundcloud.cloud/audio/playlist.m3u8?signature=fake");
    assertTrue(factory.createMediaSource(media) instanceof HlsMediaSource);
  }
}
