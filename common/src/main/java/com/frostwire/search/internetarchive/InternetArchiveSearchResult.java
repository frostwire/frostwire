/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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

package com.frostwire.search.internetarchive;

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.AbstractSearchResult;
import com.frostwire.search.CrawlableSearchResult;
import com.frostwire.util.DateParser;

import java.util.ArrayList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class InternetArchiveSearchResult extends AbstractSearchResult implements CrawlableSearchResult {
    private final String identifier;
    private final String title;
    private final String detailsUrl;
    private final String domainName;
    private final License licence;
    private final long creationTime;

    public InternetArchiveSearchResult(String domainName, InternetArchiveItem item) {
        this.identifier = item.identifier;
        this.domainName = domainName;
        this.detailsUrl = "http://" + domainName + "/details/" + item.identifier;
        this.title = buildTitle(item.title);
        this.licence = Licenses.creativeCommonsByUrl(item.licenseurl);
        this.creationTime = parsePublicDate(item.publicdate);
    }

    private static String buildTitle(Object obj) {
        if (obj instanceof String) {
            return (String) obj;
        } else if (obj instanceof ArrayList<?>) {
            ArrayList<?> l = (ArrayList<?>) obj;
            if (l.size() > 0) {
                return l.get(0).toString();
            }
        }
        return "<unknown>";
    }

    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getDisplayName() {
        return title;
    }

    @Override
    public String getSource() {
        return "Archive.org";
    }

    @Override
    public String getDetailsUrl() {
        return detailsUrl;
    }

    @Override
    public License getLicense() {
        return licence;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public boolean isComplete() {
        return false;
    }

    private long parsePublicDate(String publicdate) {
        return DateParser.parseIsoDate(publicdate);
    }

    public String getDomainName() {
        return domainName;
    }
}
