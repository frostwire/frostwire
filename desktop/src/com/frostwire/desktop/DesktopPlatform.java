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

package com.frostwire.desktop;

import com.frostwire.platform.AbstractPlatform;
import com.frostwire.platform.DefaultFileSystem;
import com.frostwire.platform.VPNMonitor;

import javax.swing.*;

/**
 * @author gubatron
 * @author aldenml
 */
public final class DesktopPlatform extends AbstractPlatform {
    private final DesktopVPNMonitor vpn;

    public DesktopPlatform() {
        super(new DefaultFileSystem(), new DesktopPaths(), new DesktopSettings());
        this.vpn = new DesktopVPNMonitor();
    }

    @Override
    public VPNMonitor vpn() {
        return vpn;
    }

    @Override
    public boolean isUIThread() {
        return SwingUtilities.isEventDispatchThread();
    }
}
