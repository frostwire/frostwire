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

import com.frostwire.search.torrent.TorrentJsonSearchPerformer;
import com.frostwire.util.JsonUtils;

import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class MininovaSearchPerformer extends TorrentJsonSearchPerformer<MininovaVuzeItem, MininovaVuzeSearchResult> {

    public MininovaSearchPerformer(String domainName, long token, String keywords, int timeout) {
        super(domainName, token, keywords, timeout, 1);
    }

    @Override
    protected String getUrl(int page, String encodedKeywords) {
        return "http://" + getDomainName() + "/vuze.php?search=" + encodedKeywords;
    }

    @Override
    protected List<MininovaVuzeItem> parseJson(String json) {
        //fix what seems to be an intentional JSON syntax typo put ther by mininova
        json = json.replace("\\n", " ");
        json = json.replace("\"hash\":", ", \"hash\":");
        json = json.replace("\"\"hash", "\", \"hash");
        MininovaVuzeResponse response = JsonUtils.toObject(json, MininovaVuzeResponse.class);
        return response.results;
    }

    @Override
    protected MininovaVuzeSearchResult fromItem(MininovaVuzeItem item) {
        return new MininovaVuzeSearchResult(item);
    }
}
