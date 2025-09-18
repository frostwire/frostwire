/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

package com.frostwire.search;

import com.frostwire.search.torrent.TorrentCrawlableSearchResult;
import com.frostwire.search.torrent.TorrentCrawledAlbumSearchResult;
import com.frostwire.search.torrent.TorrentItemSearchResult;
import com.frostwire.util.MimeDetector;
import org.apache.commons.io.FilenameUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * To be used only inside PerformersHelper and only for torrents search related functions.
 * Private API.
 *
 * @author gubatron
 * @author aldenml
 */
public class AlbumCluster {
    private static final int ALBUM_SIZE_THRESHOLD = 4;

    /**
     * Try to extract album and artist from path.
     *
     * @param
     * @return
     */
    /*public static final Pair<String, String> albumArtistFromPath(String filepath, String defaultAlbum, String defaultArtist) {
        String album = defaultAlbum;
        String artist = defaultArtist;

        if (!filepath.contains("/")) { // does not contain directory parts
            return ImmutablePair.of(album, artist);
        }
        ArrayList<String> dirs = new ArrayList<String>(Arrays.asList(filepath.split("/")));

        if (dirs.get(0).equals("")) {
            dirs.remove(0);
        }

        if (dirs.size() > 0) {
            dirs.remove(dirs.size() - 1);
        }

        // strip disc subdirectory from list
        if (dirs.size() > 0) {
            String last = dirs.get(dirs.size() - 1);
            if (last.matches("(?is)(^|\\s)(CD|DVD|Disc)\\s*\\d+(\\s|$)")) {
                dirs.remove(dirs.size() - 1);
            }
        }

        if (dirs.size() > 0) {
            // for clustering assume %artist%/%album%/file or %artist% - %album%/file
            album = dirs.get(dirs.size() - 1);
            if (album.contains(" - ")) {
                String[] parts = album.split(" - ");
                artist = parts[0];
                album = parts[1];
            } else if (dirs.size() > 1) {
                artist = dirs.get(dirs.size() - 2);
            }
        }

        return ImmutablePair.of(album, artist);
    }*/
    public LinkedList<TorrentCrawledAlbumSearchResult> detect(TorrentCrawlableSearchResult parent, List<? extends TorrentItemSearchResult> results) {
        LinkedList<TorrentCrawledAlbumSearchResult> albums = new LinkedList<TorrentCrawledAlbumSearchResult>();
        Map<String, LinkedList<TorrentItemSearchResult>> dirs = new HashMap<String, LinkedList<TorrentItemSearchResult>>();
        for (TorrentItemSearchResult sr : results) {
            String path = sr.getFilePath();
            String dir = FilenameUtils.getPathNoEndSeparator(path);
            if (!dirs.containsKey(dir)) {
                dirs.put(dir, new LinkedList<TorrentItemSearchResult>());
            }
            LinkedList<TorrentItemSearchResult> items = dirs.get(dir);
            items.add(sr);
        }
        for (Map.Entry<String, LinkedList<TorrentItemSearchResult>> kv : dirs.entrySet()) {
            int numAudio = 0;
            for (TorrentItemSearchResult sr : kv.getValue()) {
                String mime = MimeDetector.getMimeType(sr.getFilePath());
                if (mime.startsWith("audio")) {
                    numAudio++;
                }
            }
//            if (numAudio >= ALBUM_SIZE_THRESHOLD) {
//                Pair<String, String> p = albumArtistFromPath(kv.getKey(), "", "");
//                TorrentCrawledAlbumSearchResult sr = new TorrentCrawledAlbumSearchResult(parent, p.getRight(), p.getLeft(), kv.getValue());
//                System.out.println(sr);
//                albums.add(sr);
//            }
        }
        return albums;
    }
}
