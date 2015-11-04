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

package com.frostwire.search.kat;

import com.frostwire.search.torrent.ComparableTorrentJsonItem;

/**
 * @author gubatron
 * @author aldenml
 */
public final class KATItem implements ComparableTorrentJsonItem {

    public String title;
    public String link;
    public String pubDate;
    public String torrentLink;
    public String hash;
    public int seeds;
    public long size;
    public int verified;

    @Override
    public int getSeeds() { // TODO: remove this bad design
        return seeds;
    }
}
