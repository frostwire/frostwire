/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
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

package com.frostwire.android;

import android.app.Application;
import android.os.Build;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.DefaultFileSystem;
import com.frostwire.platform.AbstractPlatform;
import com.frostwire.platform.Platforms;

/**
 * @author gubatron
 * @author aldenml
 */
public final class AndroidPlatform extends AbstractPlatform {

    private static final int VERSION_CODE_LOLLIPOP = 21;

    private final int sdk;

    public AndroidPlatform(Application app) {
        super(buildFileSystem(app), new AndroidPaths(app));

        this.sdk = Build.VERSION.SDK_INT;
    }

    @Override
    public boolean android() {
        return true;
    }

    @Override
    public int androidVersion() {
        return sdk;
    }

    public static boolean saf() {
        return Platforms.get().fileSystem() instanceof LollipopFileSystem;
    }

    private static FileSystem buildFileSystem(Application app) {
        FileSystem fs;

        if (Build.VERSION.SDK_INT >= VERSION_CODE_LOLLIPOP) {
            fs = new LollipopFileSystem(app);
        } else {
            fs = new DefaultFileSystem();
        }

        return fs;
    }
}
