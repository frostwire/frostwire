/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.search.zooqle;

import com.frostwire.search.AbstractSearchResult;
import com.frostwire.search.CrawlableSearchResult;

/**
 * @author aldenml
 * @author gubatron
 * Created on 4/17/17.
 */
public class ZooqleTempSearchResult extends AbstractSearchResult implements CrawlableSearchResult {

    final String detailUrl;

    ZooqleTempSearchResult(String domainName, String detailPath) {
        detailUrl = "https://" + domainName + "/" + detailPath;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getDetailsUrl() {
        return detailUrl;
    }

    @Override
    public String getSource() {
        return null;
    }

    @Override
    public boolean isComplete() {
        return false;
    }
}
