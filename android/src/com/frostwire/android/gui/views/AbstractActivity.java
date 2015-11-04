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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.View;

/**
 * 
 * @author gubatron
 * @author aldenml
 * 
 */
public abstract class AbstractActivity extends Activity {

    private final int layoutResId;
    private final ArrayList<String> fragmentTags;

    public AbstractActivity(int layoutResId) {
        this.layoutResId = layoutResId;
        this.fragmentTags = new ArrayList<String>();
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        String tag = fragment.getTag();
        if (tag != null && !fragmentTags.contains(tag)) {
            fragmentTags.add(tag);
        }
    }

    List<Fragment> getVisibleFragments() {
        List<Fragment> result = new LinkedList<Fragment>();

        FragmentManager fm = getFragmentManager();
        for (String tag : fragmentTags) {
            Fragment f = fm.findFragmentByTag(tag);
            if (f != null && f.isVisible()) {
                result.add(f);
            }
        }

        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(layoutResId);
        initComponents(savedInstanceState);
    }

    protected abstract void initComponents(Bundle savedInstanceState);

    @SuppressWarnings("unchecked")
    protected final <T extends View> T findView(int id) {
        return (T) super.findViewById(id);
    }
}
