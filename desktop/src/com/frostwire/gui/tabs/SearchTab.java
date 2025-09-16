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

package com.frostwire.gui.tabs;

import com.limegroup.gnutella.gui.I18n;
import com.limegroup.gnutella.gui.search.SearchMediator;

import javax.swing.*;

/**
 * This class constructs the search tab, including all UI elements.
 */
public final class SearchTab extends AbstractTab {
    public SearchTab() {
        super(I18n.tr("Search"),
                I18n.tr("Search and Download Files from the Internet."),
                "search_tab");
    }

    public JComponent getComponent() {
        return SearchMediator.getResultComponent();
    }
}
