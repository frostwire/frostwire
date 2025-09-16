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

package com.frostwire.gui.bittorrent;

import com.limegroup.gnutella.gui.ButtonRow;

/**
 * This class contains the buttons in the download window, allowing
 * classes in this package to enable or disable buttons at specific
 * indexes in the row.
 */
final class BTDownloadButtons {
    /**
     * The row of buttons for the download window.
     */
    private final ButtonRow BUTTONS;

    BTDownloadButtons(final BTDownloadMediator dm) {
        BUTTONS = new ButtonRow(dm.getActions(), ButtonRow.X_AXIS, ButtonRow.NO_GLUE);
    }

    ButtonRow getComponent() {
        return BUTTONS;
    }
}
