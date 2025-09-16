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

package com.limegroup.gnutella.gui.actions;

import com.limegroup.gnutella.gui.GUIMediator;

import java.awt.event.ActionEvent;

/**
 * Opens the given url in a browser.
 */
public class OpenLinkAction extends AbstractAction {
    /**
     *
     */
    private static final long serialVersionUID = -7243267672146519961L;
    private final String url;

    public OpenLinkAction(String url, String name) {
        this(url, name, null);
    }

    public OpenLinkAction(String url, String name, String description) {
        super(name);
        this.url = url;
        putValue(LONG_DESCRIPTION, description);
    }

    public void actionPerformed(ActionEvent e) {
        GUIMediator.openURL(url);
    }
}
