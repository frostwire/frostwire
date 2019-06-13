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

package com.frostwire.bittorrent;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public final class BTContext {
    public final int[] version = {0, 0, 0, 0};
    public File homeDir;
    public File torrentsDir;
    public File dataDir;
    public String interfaces;
    public int retries;
    public boolean optimizeMemory;
    /**
     * Indicates if the engine starts with the DHT enable.
     */
    public boolean enableDht = true;
}
