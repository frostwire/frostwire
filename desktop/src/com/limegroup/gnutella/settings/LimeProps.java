/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

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

package com.limegroup.gnutella.settings;

import org.limewire.setting.SettingsFactory;

/**
 * Handler for all 'FrostWire.props' settings.  Classes such
 * as SearchSettings, ConnectionSettings, etc... should retrieve
 * the factory via LimeProps.instance().getFactory() and add
 * settings to that factory.
 */
public class LimeProps extends LimeWireSettings {
    private static final LimeProps INSTANCE = new LimeProps();
    // The FACTORY is used for subclasses of LimeProps, so they know
    // which factory to add classes to.
    public static final SettingsFactory FACTORY = INSTANCE.getFactory();

    // This is protected so that subclasses can extend from it, but
    // subclasses should NEVER instantiate a copy themselves.
    LimeProps() {
        super("frostwire.props", "FrostWire properties file");
        assert getClass() == LimeProps.class : "should not have a subclass instantiate";
    }

    /**
     * Returns the only instance of this class.
     */
    public static LimeProps instance() {
        return INSTANCE;
    }
}
