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

package com.limegroup.gnutella.gui.init;

import javax.swing.*;
import java.awt.*;

/**
 * This class serves two purposes.  First, it is a JPanel that
 * contains the body of a LimeWire setup window.  Second, it
 * serves as a proxy for the underlying SetupWindow object that
 * that handles the actual drawing.
 */
final class SetupWindowHolder extends JPanel {
    /**
     * The <tt>CardLayout</tt> instance for the setup windows.
     */
    private final CardLayout CARD_LAYOUT = new CardLayout();

    /**
     * Sets the <tt>CardLayout</tt> for the setup windows.
     */
    SetupWindowHolder() {
        setLayout(CARD_LAYOUT);
    }

    /**
     * Adds the specified window to the CardLayout based on its title.
     *
     * @param window the <tt>SetupWindow</tt> to add
     */
    void add(SetupWindow window) {
        add(window, window.getKey());
    }

    /**
     * Shows the window specified by its title.
     *
     * @param key the unique key of the <tt>Component</tt> to show
     */
    void show(String key) {
        CARD_LAYOUT.show(this, key);
    }
}
