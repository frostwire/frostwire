/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.engines;

import com.frostwire.search.ISearchPerformer;

/**
 * Makes the performer that asks Telluride (FrostWire's yt-dlp wrapper) for the media behind a page
 * URL. Each client reaches Telluride its own way: desktop runs the Telluride launcher, Android its
 * embedded Python runtime (frostwire/frostwire#1002).
 */
@FunctionalInterface
public interface TellurideSearchFactory {
  /**
   * @param playlistMode list a playlist's items instead of the page's media
   * @return the performer, or null when Telluride is not available on this client
   */
  ISearchPerformer create(long token, String pageUrl, boolean playlistMode);
}
