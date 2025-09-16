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

package com.frostwire.gui.library;

import com.limegroup.gnutella.gui.RefreshListener;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The left hand side JPanels that contain JLists when used
 * switch and refresh the right hand side mediators (aka Tables)
 * as necessary.
 * <p>
 * Since it takes some time to refresh the contents of the table,
 * we cannot order these tables to scroll down to a certain position
 * until all the data has been loaded.
 * <p>
 * This Abstract class has been created to enqueue Runnable tasks
 * that should be performed once the right hand side mediators
 * have finished loading.
 * <p>
 * Use enqueueRunnable on your implementations of the {@link AbstractLibraryListPanel}
 *
 * @author gubatron
 * @author aldenml
 */
abstract class AbstractLibraryListPanel extends JPanel implements RefreshListener {
    private final List<Runnable> PENDING_RUNNABLES;

    AbstractLibraryListPanel() {
        PENDING_RUNNABLES = Collections.synchronizedList(new ArrayList<>());
    }

    void enqueueRunnable(Runnable r) {
        PENDING_RUNNABLES.add(r);
    }

    void executePendingRunnables() {
        if (PENDING_RUNNABLES != null && PENDING_RUNNABLES.size() > 0) {
            synchronized (PENDING_RUNNABLES) {
                Iterator<Runnable> it = PENDING_RUNNABLES.iterator();
                while (it.hasNext()) {
                    try {
                        Runnable r = it.next();
                        r.run();
                        it.remove();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    List<Runnable> getPendingRunnables() {
        return PENDING_RUNNABLES;
    }
}
