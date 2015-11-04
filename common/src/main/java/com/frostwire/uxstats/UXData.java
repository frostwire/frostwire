/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014,, FrostWire(R). All rights reserved.
 
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

package com.frostwire.uxstats;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Temporary session.
 * @author gubatron
 * @author aldenml
 *
 */
public class UXData {

    public String guid;
    public String os;
    public String fwversion;
    public String build;
    public long time;

    public List<UXAction> actions;
    
    public UXData() {
    }

    public UXData(String guid, String os, String fwversion, String build) {
        this.guid = guid;
        this.os = os;
        this.fwversion = fwversion;
        this.build = build;
        this.time = System.currentTimeMillis();
        this.actions = Collections.synchronizedList(new LinkedList<UXAction>());
    }
}
