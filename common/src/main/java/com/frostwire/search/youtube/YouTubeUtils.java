/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.search.youtube;

/**
 * @author gubatron
 * @author aldenml
 */
class YouTubeUtils {

    private YouTubeUtils() {
    }

    public static boolean isDash(YouTubeExtractor.LinkInfo info) {
        switch (info.fmt) {
            case 133:
            case 134:
            case 135:
            case 136:
            case 137:
            case 139:
            case 140:
            case 141:
                return true;
            default:
                return false;
        }
    }

    public static String buildDownloadUrl(YouTubeExtractor.LinkInfo video, YouTubeExtractor.LinkInfo audio) {
        String downloadUrl;
        if (video != null && audio == null) {
            downloadUrl = video.link;
        } else if (video == null && audio != null) {
            downloadUrl = audio.link;
        } else if (video != null && audio != null) {
            downloadUrl = video.link;
        } else {
            throw new IllegalArgumentException("No track defined");
        }

        return downloadUrl;
    }
}
