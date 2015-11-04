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

package com.frostwire.search.extratorrent;

import java.util.List;

/*
{
 "title":"Extratorrent Search: ...",
 "link":"http://extratorrent.com",
 "description":"Extratorrent Search: ...",
 "total_results":224,
 "list":[
    {
    "title":"...",
    "category":"Music",
    "subcategory":"Music Videos",
    "link":"...",
    "guid":"...",
    "pubDate":"Wed, 09 Jun 2010 18:08:27 +0100",
    "torrentLink":"...",
    "files":1,
    "comments":11,
    "hash":"...",
    "peers":393,
    "seeds":388,
    "leechs":5,
    "size":101146107
    },
*/

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class ExtratorrentResponse {

    public List<ExtratorrentItem> list;
}
