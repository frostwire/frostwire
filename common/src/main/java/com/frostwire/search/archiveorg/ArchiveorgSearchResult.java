/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search.archiveorg;

import com.frostwire.licenses.License;
import com.frostwire.licenses.Licenses;
import com.frostwire.search.AbstractSearchResult;
import com.frostwire.search.CrawlableSearchResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * @author gubatron
 * @author aldenml
 */
public final class ArchiveorgSearchResult extends AbstractSearchResult implements CrawlableSearchResult {
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
