/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.transfers;

import java.io.File;

/**
 * @author gubatron
 * @author aldenml
 */
public interface TransferItem {
    String getName();

    String getDisplayName();

    /**
     * Actual file in the file system to which the data is saved. Ideally it should be
     * inside the save path of the parent transfer.
     *
     * @return
     */
    File getFile();

    long getSize();

    boolean isSkipped();

    long getDownloaded();

    /**
     * [0..100]
     *
     * @return
     */
    int getProgress();

    boolean isComplete();
}
