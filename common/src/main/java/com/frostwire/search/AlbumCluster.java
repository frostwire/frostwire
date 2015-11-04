/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search;

import com.frostwire.search.torrent.TorrentCrawlableSearchResult;
import com.frostwire.search.torrent.TorrentCrawledAlbumSearchResult;
import com.frostwire.search.torrent.TorrentItemSearchResult;
import com.frostwire.util.MimeDetector;
import org.apache.commons.io.FilenameUtils;

import java.util.*;

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
