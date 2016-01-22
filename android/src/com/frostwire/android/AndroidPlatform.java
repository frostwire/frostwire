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
import com.frostwire.platform.GenericPlatform;
import com.frostwire.platform.Platforms;

/**
 * @author gubatron
 * @author aldenml
 */
public final class AndroidPlatform extends GenericPlatform {

    private static final int VERSION_CODE_LOLLIPOP = 21;

    public AndroidPlatform(Application app) {
        super(new LollipopFileSystem(app));
    }

    @Override
    public boolean android() {
        return true;
    }

    @Override
    public int androidVersion() {
        return Build.VERSION.SDK_INT;
    }

    public static boolean saf() {
        return Platforms.get().androidVersion() >= VERSION_CODE_LOLLIPOP;
    }
}
