/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.os.Bundle;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 10/10/17.
 */


public abstract class AbstractTransferDetailFragment extends AbstractFragment {
    public AbstractTransferDetailFragment(int layoutId) {
        super(layoutId);
        setHasOptionsMenu(true);
    }

    public String getTitle() {
        Bundle arguments = getArguments();
        if (arguments != null) {
            return arguments.getString("tabTitle");
        }
        return "[title]";
    }
}
