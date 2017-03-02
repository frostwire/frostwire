/*
 * By Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2013-2017, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.fragments.helpers;

import android.view.Menu;

import com.frostwire.util.Logger;

import java.lang.reflect.Method;

/**
 * Created on 3/2/17.
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */

public final class ToolbarMenuOptionalIconsEnabler {
    private static final Logger LOG = Logger.getLogger(ToolbarMenuOptionalIconsEnabler.class);

    public static void setOptionalIconsVisible(Menu menu, boolean visible) {
        Class menuClass = menu.getClass();
        if (menu != null && menuClass.getSimpleName().equals("MenuBuilder")) {
            try {
                Method m = menuClass.getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                if (m != null) {
                    m.setAccessible(true);
                    m.invoke(menu, visible);
                } else {
                    LOG.warn("unable to set icons for overflow menu. MenuBuilder.setOptionalIconsVisible(boolean) method not available");
                }
            } catch (Throwable e) {
                LOG.warn("unable to set icons for overflow menu", e);
            }
        }
    }
}
