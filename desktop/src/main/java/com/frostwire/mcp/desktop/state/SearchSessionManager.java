package com.frostwire.mcp.desktop.state;

import com.frostwire.search.ISearchPerformer;
import com.frostwire.search.SearchError;
import com.frostwire.search.SearchListener;
import com.frostwire.search.SearchManager;
import com.frostwire.search.SearchResult;
import com.limegroup.gnutella.gui.search.SearchEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class SearchSessionManager implements SearchListener {
    private static final SearchSessionManager INSTANCE = new SearchSessionManager();
    private static final long SESSION_TTL_MS = 10 * 60 * 1000L;

    private final Map<Long, SearchSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong tokenCounter = new AtomicLong(1);

    private SearchSessionManager() {
    }

    public static SearchSessionManager instance() {
        return INSTANCE;
    }

    public SearchSession createSearch(String keywords, List<String> engineIds) {
        long token = tokenCounter.getAndIncrement();
        List<SearchEngine> engines = SearchEngine.getEngines();
        List<ISearchPerformer> performers = new ArrayList<>();
        boolean filterEngines = engineIds != null && !engineIds.isEmpty();
        for (SearchEngine engine : engines) {
            if (filterEngines && !engineIds.contains(engine.getId().name())) {
                continue;
            }
            if (!engine.isEnabled()) {
                continue;
            }
            try {
                ISearchPerformer performer = engine.getPerformer(token, keywords);
                performer.setListener(this);
                performers.add(performer);
            } catch (Exception e) {
                // skip engines that fail to create performers (e.g. not ready)
            }
        }
        SearchSession session = new SearchSession(token, keywords, performers);
        sessions.put(token, session);
        for (ISearchPerformer performer : performers) {
            SearchManager.getInstance().perform(performer);
        }
        return session;
    }

    public SearchSession getSession(long token) {
        return sessions.get(token);
    }

    @Override
    public void onResults(long token, List<? extends SearchResult> results) {
        SearchSession session = sessions.get(token);
        if (session != null) {
            session.addResults(results);
        }
    }

    @Override
    public void onStopped(long token) {
        SearchSession session = sessions.get(token);
        if (session != null && session.allPerformersStopped()) {
            session.setComplete();
        }
    }

    @Override
    public void onError(long token, SearchError error) {
        SearchSession session = sessions.get(token);
        if (session != null) {
            session.setError(error.message());
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> {
            SearchSession session = entry.getValue();
            return session.isComplete() && (now - session.getCreatedAt()) > SESSION_TTL_MS;
        });
    }
}
