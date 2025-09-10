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
 * Adapter interface for platform-specific Telluride integration.
 * Allows SearchEngine to create Telluride performers without knowing
 * the underlying implementation (HTTP server on Desktop vs TellurideCourier on Android).
 * 
 * @author gubatron
 * @author aldenml
 */
public interface TellurideAdapter {
    
    /**
     * Create a platform-specific Telluride search performer
     * @param token search token
     * @param keywords search keywords
     * @return a SearchPerformer that can handle Telluride searches, or null if not supported
     */
    SearchPerformer createTelluridePerformer(long token, String keywords);
    
    /**
     * Create a platform-specific Telluride search performer for a specific page URL
     * @param token search token 
     * @param pageUrl the URL to search within
     * @param adapter platform-specific adapter for handling results
     * @return a platform-specific performer, or null if not supported
     */
    Object createTelluridePerformer(long token, String pageUrl, Object adapter);
    
    /**
     * Check if Telluride functionality is available on this platform
     * @return true if Telluride is supported, false otherwise
     */
    boolean isTellurideSupported();
}