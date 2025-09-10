/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2023, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.search;

import java.util.List;

/**
 * Consolidated SearchEngine class that works across Android and Desktop platforms
 * through the use of adapter pattern for platform-specific functionality.
 * 
 * @author gubatron
 * @author aldenml
 */
public abstract class CommonSearchEngine {
    
    private final String name;
    private final String preferenceKey;
    private final String domainName;
    private final SearchEngineSettingsAdapter settingsAdapter;
    private final TellurideAdapter tellurideAdapter;
    
    private boolean active;

    protected CommonSearchEngine(String name, String preferenceKey, String domainName, 
                               SearchEngineSettingsAdapter settingsAdapter,
                               TellurideAdapter tellurideAdapter) {
        this.name = name;
        this.preferenceKey = preferenceKey;
        this.domainName = domainName;
        this.settingsAdapter = settingsAdapter;
        this.tellurideAdapter = tellurideAdapter;
        this.active = true;
        postInitWork();
    }

    /**
     * Override for things like picking the fastest mirror domainName
     */
    protected void postInitWork() {
    }

    /**
     * Override to check if the search engine is ready to be used
     * @return true if ready, false otherwise
     */
    protected boolean isReady() {
        return domainName != null;
    }

    public String getName() {
        return name;
    }

    public String getPreferenceKey() {
        return preferenceKey;
    }

    public String getDomainName() {
        return domainName;
    }

    /**
     * This is what's eventually checked to perform a search
     */
    public boolean isEnabled() {
        return isActive() && settingsAdapter.isEnabled(preferenceKey);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Create a search performer for the given keywords
     * @param token search token
     * @param keywords search keywords
     * @return SearchPerformer instance, or null if not available
     */
    public abstract SearchPerformer getPerformer(long token, String keywords);

    /**
     * Create a Telluride performer if supported by this platform
     * @param token search token
     * @param pageUrl page URL to search
     * @param adapter platform-specific adapter for handling results
     * @return platform-specific Telluride performer, or null if not supported
     */
    public Object getTelluridePerformer(long token, String pageUrl, Object adapter) {
        if (tellurideAdapter != null && tellurideAdapter.isTellurideSupported()) {
            return tellurideAdapter.createTelluridePerformer(token, pageUrl, adapter);
        }
        return null;
    }

    /**
     * Create a standard Telluride performer for keyword search
     * @param token search token
     * @param keywords search keywords
     * @return SearchPerformer for Telluride, or null if not supported
     */
    public SearchPerformer getTellurideSearchPerformer(long token, String keywords) {
        if (tellurideAdapter != null && tellurideAdapter.isTellurideSupported()) {
            return tellurideAdapter.createTelluridePerformer(token, keywords);
        }
        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        CommonSearchEngine that = (CommonSearchEngine) obj;
        return name != null ? name.equals(that.name) : that.name == null;
    }

    @Override
    public int hashCode() {
        return name != null ? name.hashCode() : 0;
    }

    /**
     * Ensure at least one search engine is enabled
     * @param engines list of available engines
     * @param defaultEngine fallback engine to enable if none are enabled
     */
    public static void ensureAtLeastOneEnabled(List<? extends CommonSearchEngine> engines, 
                                             CommonSearchEngine defaultEngine) {
        boolean oneEnabled = false;
        for (CommonSearchEngine engine : engines) {
            if (engine.isEnabled()) {
                oneEnabled = true;
                break;
            }
        }
        if (!oneEnabled && defaultEngine != null) {
            defaultEngine.settingsAdapter.setEnabled(defaultEngine.getPreferenceKey(), true);
        }
    }

    /**
     * Find a search engine by name
     * @param engines list of engines to search
     * @param name name to search for
     * @return matching engine or null if not found
     */
    public static CommonSearchEngine forName(List<? extends CommonSearchEngine> engines, String name) {
        for (CommonSearchEngine engine : engines) {
            if (engine.getName().equalsIgnoreCase(name)) {
                return engine;
            }
        }
        return null;
    }
}