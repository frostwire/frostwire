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

import android.content.Context;

import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 10/10/17.
 */


public abstract class AbstractTransferDetailFragment extends AbstractFragment {
    private WeakReference<Context> contextRef; //TransferDetailActivity
    private final int titleStringId;
    public AbstractTransferDetailFragment(int layoutId, int titleStringId) {
        super(layoutId);
        this.titleStringId = titleStringId;
        setHasOptionsMenu(true);
    }

    public void setContext(Context context) {
        contextRef = Ref.weak(context);
    }

    public String getTitle() {
        if (Ref.alive(contextRef)) {
            return contextRef.get().getString(titleStringId);
        }
        return "";
    }
}
