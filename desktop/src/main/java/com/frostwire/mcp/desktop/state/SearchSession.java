package com.frostwire.mcp.desktop.state;

import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SearchSession {
    private final long token;
    private final String keywords;
    private final List<ISearchPerformer> performers;
    private final List<SearchResult> results = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean complete;
    private volatile String error;
    private final long createdAt;

    public SearchSession(long token, String keywords, List<ISearchPerformer> performers) {
        this.token = token;
        this.keywords = keywords;
        this.performers = performers;
        this.createdAt = System.currentTimeMillis();
    }

    public void addResults(List<? extends SearchResult> newResults) {
        results.addAll(newResults);
    }

    public void setComplete() {
        this.complete = true;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getToken() {
        return token;
    }

    public String getKeywords() {
        return keywords;
    }

    public List<SearchResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public boolean isComplete() {
        return complete;
    }

    public String getError() {
        return error;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean allPerformersStopped() {
        for (ISearchPerformer p : performers) {
            if (!p.isStopped()) {
                return false;
            }
        }
        return true;
    }

    public void cancel() {
        for (ISearchPerformer p : performers) {
            p.stop();
        }
        complete = true;
    }
}
