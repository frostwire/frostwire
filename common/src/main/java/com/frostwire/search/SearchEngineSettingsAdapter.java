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

/**
 * Adapter interface for platform-specific settings persistence.
 * Allows SearchEngine to check if an engine is enabled/disabled
 * without knowing the underlying settings implementation.
 * 
 * @author gubatron
 * @author aldenml
 */
public interface SearchEngineSettingsAdapter {
    
    /**
     * Check if a search engine is enabled based on its preference key
     * @param preferenceKey the key used to store the enabled/disabled state
     * @return true if the search engine is enabled, false otherwise
     */
    boolean isEnabled(String preferenceKey);
    
    /**
     * Set the enabled state of a search engine
     * @param preferenceKey the key used to store the enabled/disabled state
     * @param enabled true to enable the engine, false to disable
     */
    void setEnabled(String preferenceKey, boolean enabled);
}