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

package com.limegroup.gnutella.gui.dnd;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

/**
 * A simple transferable creator that can created a FileTransferable
 * from the selection of a LimeJTable.
 */
class BasicTransferableCreator {
    Transferable getTransferable() {
        List<File> l = new LinkedList<>();
        List<FileTransfer> lazy = new LinkedList<>();
        if (l.size() == 0 && lazy.size() == 0)
            return null;
        return new FileTransferable(l, lazy);
    }
}
