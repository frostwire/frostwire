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

package com.andrew.apollo;

import static org.junit.Assert.assertEquals;

import android.app.Application;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** A preview (ringtone) that ends or fails to open must be reported as stopped. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class MusicPlaybackServicePreviewEndTest {

  @Test
  public void aPreviewThatEndsIsFinished() {
    int[] finished = {0};
    Player.Listener listener = MusicPlaybackService.previewEndListener(() -> finished[0]++);

    listener.onPlaybackStateChanged(Player.STATE_READY);
    assertEquals(0, finished[0]);
    listener.onPlaybackStateChanged(Player.STATE_ENDED);

    assertEquals(1, finished[0]);
  }

  @Test
  public void aPreviewThatFailsToOpenIsFinished() {
    int[] finished = {0};
    Player.Listener listener = MusicPlaybackService.previewEndListener(() -> finished[0]++);

    listener.onPlayerError(
        new PlaybackException(
            "unsupported", null, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED));

    assertEquals(1, finished[0]);
  }
}
