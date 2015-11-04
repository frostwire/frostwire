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

package com.frostwire.search.archiveorg;

import com.frostwire.licences.License;
import com.frostwire.search.AbstractSearchResult;
import com.frostwire.search.CrawlableSearchResult;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public class ArchiveorgSearchResult extends AbstractSearchResult implements CrawlableSearchResult {

    private final String identifier;
    private final String title;
    private final String detailsUrl;
    private final String domainName;
    private final License licence;
    private final long creationTime;

    public ArchiveorgSearchResult(String domainName, ArchiveorgItem item) {
        this.identifier = item.identifier;
        this.domainName = domainName;
        this.detailsUrl = "http://" + domainName + "/details/" + item.identifier;
        this.title = item.title;
        this.licence = License.creativeCommonsByUrl(item.licenseurl);
        this.creationTime = parsePublicDate(item.publicdate);
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
        // 2009-12-02T15:41:50Z
        // 2008-02-20T22:02:21Z
        //"yyyy-MM-dd'T'HH:mm:ss'Z'"
        SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        long result = -1;
        try {
            result = date.parse(publicdate).getTime();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

    public String getDomainName() {
        return domainName;
    }
}
