/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2019, FrostWire(R). All rights reserved.
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

package com.limegroup.gnutella.gui.options;

import com.limegroup.gnutella.gui.options.panes.AbstractPaneItem;
import org.limewire.service.ErrorService;

/**
 * Static factory class that creates the option panes based on their keys.
 * <p>
 * This class constructs all of the elements of the options window.  To add
 * a new option, this class should be used.  This class allows for options
 * to be added to already existing panes as well as for options to be added
 * to new panes that you can also add here.  To add a new top-level pane,
 * create a new <tt>OptionsPaneImpl</tt> and call the addOption method.
 * To add option items to that pane, add subclasses of
 * <tt>AbstractPaneItem</tt>.
 */
class OptionsPaneFactory {
    /**
     * Constructs a new OptionsPaneFactory.
     * <p>
     * Due to intermixing within Saved & Shared pane items, these two need special
     * setups.
     */
    OptionsPaneFactory() {
    }

    /**
     * Creates the options pane for a key.
     */
    OptionsPane createOptionsPane(OptionsTreeNode node) {
        Class<? extends AbstractPaneItem>[] clazzes = node.getClasses();
        if (clazzes != null) {
            final OptionsPane pane = new OptionsPaneImpl(node.getTitleKey());
            for (Class<? extends AbstractPaneItem> clazz : clazzes) {
                try {
                    pane.add(clazz.getDeclaredConstructor().newInstance());
                } catch (Exception e) {
                    ErrorService.error(e);
                }
            }
            return pane;
        } else {
            throw new IllegalArgumentException("no options pane for this key: " + node.getTitleKey());
        }
    }
}
