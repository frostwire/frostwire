/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class AbstractFragment extends Fragment {

    private final int layoutResId;

    public AbstractFragment(int layoutResId) {
        this.layoutResId = layoutResId;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(layoutResId, container, false);

        if (!rootView.isInEditMode()) {
            initComponents(rootView);
        }

        return rootView;
    }

    @SuppressWarnings("unchecked")
    protected final <T extends View> T findView(View v, int id) {
        T result = null;
        if (v != null) {
           result = (T) v.findViewById(id);
        }
        return result;
    }

    protected abstract void initComponents(View rootView);
}
