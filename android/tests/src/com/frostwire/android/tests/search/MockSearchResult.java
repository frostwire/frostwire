package com.frostwire.android.tests.search;

import com.frostwire.licenses.License;
import com.frostwire.search.SearchResult;

public class MockSearchResult implements SearchResult {

    @Override
    public String getSource() {
        return "Tests";
    }

    @Override
    public String getDetailsUrl() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public long getCreationTime() {
        return -1;
    }

    @Override
    public License getLicense() {
        return License.UNKNOWN;
    }

    @Override
    public String getThumbnailUrl() {
        return null;
    }
}
