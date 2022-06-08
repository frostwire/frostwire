/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2022, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.android;

import android.app.Application;

import com.frostwire.android.gui.Librarian;
import com.frostwire.platform.DefaultFileSystem;

import java.io.File;

public class Android10QFileSystem extends DefaultFileSystem {
    private final Application app;

    public Android10QFileSystem(Application app) {
        this.app = app;
    }

    public boolean copy(File src, File dest) {
        return Librarian.mediaStoreSaveToDownloads(src, dest);
    }
}
