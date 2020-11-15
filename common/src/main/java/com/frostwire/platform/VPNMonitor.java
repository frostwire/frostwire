/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.platform;

/**
 * @author gubatron
 * @author aldenml
 */
public interface VPNMonitor {
    /**
     * This indicate if the OS networking is under active VPN or not.
     * It's based on routing heuristics, and not 100% reliable, but it's usable.
     * The value could be cached for speed and optimization purposes. The actual
     * implementation should not block for a long period of time and should be
     * safe to call it from the UI.
     *
     * @return if under VPN or not
     */
    boolean active();

    /**
     * Perform a manual status refresh, don't do this from the UI since a blocking
     * code could be used in any given implementation.
     */
    void refresh();
}
