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
 * Example usage of the consolidated SearchEngine approach.
 * This demonstrates how both Android and Desktop can use the same
 * common infrastructure with platform-specific adapters.
 * 
 * @author gubatron
 * @author aldenml
 */
public class ConsolidatedSearchEngineExample {
    
    /**
     * Example of how Android would use the consolidated search engines:
     * 
     * <pre>
     * // Get all available engines
     * List<AndroidSearchEngine> engines = AndroidSearchEngine.getEngines(true);
     * 
     * // Ensure at least one is enabled
     * CommonSearchEngine.ensureAtLeastOneEnabled(engines, AndroidSearchEngine.ARCHIVE);
     * 
     * // Find a specific engine
     * AndroidSearchEngine tpb = AndroidSearchEngine.forName("TPB");
     * if (tpb != null && tpb.isEnabled()) {
     *     SearchPerformer performer = tpb.getPerformer(12345L, "ubuntu");
     *     if (performer != null) {
     *         SearchManager.getInstance().perform(performer);
     *     }
     * }
     * 
     * // Use Telluride
     * AndroidSearchEngine telluride = AndroidSearchEngine.TELLURIDE_COURIER;
     * Object telluridePerformer = telluride.getTelluridePerformer(12345L, 
     *     "https://youtube.com/watch?v=example", searchResultAdapter);
     * </pre>
     */
    public static void androidExample() {
        // Implementation would go here
    }
    
    /**
     * Example of how Desktop would use the consolidated search engines:
     * 
     * <pre>
     * // Get all available engines
     * List<DesktopSearchEngine> engines = DesktopSearchEngine.getEngines();
     * 
     * // Find a specific engine
     * DesktopSearchEngine archiveOrg = engines.stream()
     *     .filter(e -> e.getName().equals("Archive.org"))
     *     .findFirst()
     *     .orElse(null);
     * 
     * if (archiveOrg != null && archiveOrg.isEnabled()) {
     *     SearchPerformer performer = archiveOrg.getPerformer(12345L, "linux");
     *     if (performer != null) {
     *         SearchManager.getInstance().perform(performer);
     *     }
     * }
     * 
     * // Use Telluride with HTTP server
     * DesktopSearchEngine telluride = DesktopSearchEngine.getSearchEngineByName("Cloud:");
     * if (telluride != null) {
     *     SearchPerformer performer = telluride.getTellurideSearchPerformer(12345L, 
     *         "https://youtube.com/watch?v=example");
     *     if (performer != null) {
     *         SearchManager.getInstance().perform(performer);
     *     }
     * }
     * </pre>
     */
    public static void desktopExample() {
        // Implementation would go here
    }
    
    /**
     * Benefits of the consolidated approach:
     * 
     * 1. Shared common functionality - domain handling, settings check, engine management
     * 2. Platform-specific adapters for settings and Telluride integration
     * 3. Consistent interface across platforms
     * 4. Easy to add new search engines in one place
     * 5. Better maintainability and less code duplication
     * 6. Type safety and better IDE support
     * 
     * Migration path:
     * 1. Both platforms can gradually switch to using the consolidated engines
     * 2. Old SearchEngine classes can be kept for backward compatibility initially
     * 3. New code should use the consolidated approach
     * 4. Eventually old classes can be deprecated and removed
     */
    public static void benefits() {
        // This method just documents the benefits
    }
}