/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.search.mininova;

import com.frostwire.search.torrent.ComparableTorrentJsonItem;

/*
 *               { "title": "...", 
 *               "date": "Wed, 18 Nov 2009 10:07:42 +0100", 
 *               "peers": 0, 
 *               "seeds": 10, 
 *               "superseeds": 3, 
 *               "category": "Music", 
 *               "cdp": "...", 
 *               "comments": 1, 
 *               "size": 4828003, 
 *               "votes": 1, 
 *               "download":"..." #notice how there's no comma
 *               "hash": "..." }
 */
/**
 * @author gubatron
 * @author aldenml
 *
 */
public class MininovaVuzeItem implements ComparableTorrentJsonItem {

    public String title;

    public String date;

    public int peers;

    public int seeds;

    public int superseeds;

    public String cdp; //Torrent Detail URL

    public long size; //file size

    public String download; //.torrent download url

    public String hash;
    
    @Override
    public int getSeeds() {
        return seeds;
    }
}
