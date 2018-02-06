/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.light.platform;

import com.frostwire.light.ConfigurationManager;
import com.frostwire.platform.AbstractPlatform;
import com.frostwire.platform.DefaultFileSystem;
import com.frostwire.platform.VPNMonitor;

public class LightPlatform extends AbstractPlatform {
    private final DesktopVPNMonitor vpn;

    public LightPlatform(ConfigurationManager configurationManager) {
        super(new DefaultFileSystem(), new LightPaths(configurationManager), new LightSettings(configurationManager));
        this.vpn = new DesktopVPNMonitor();
    }

    @Override
    public VPNMonitor vpn() {
        return vpn;
    }
}
