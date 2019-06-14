/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
    static final SettingsFactory FACTORY = INSTANCE.getFactory();

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
