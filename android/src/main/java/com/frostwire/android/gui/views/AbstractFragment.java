/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views;

import android.app.Activity;
import androidx.fragment.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IdRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractFragment extends Fragment {

    private final int layoutResId;

    private boolean paused;

    public AbstractFragment(int layoutResId) {
        this.layoutResId = layoutResId;
        this.paused = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(layoutResId, container, false);

        if (!rootView.isInEditMode()) {
            initComponents(rootView, savedInstanceState);
        }

        return rootView;
    }

    @Override
    public void onResume() {
        paused = false;
        super.onResume();
    }

    @Override
    public void onPause() {
        paused = true;
        super.onPause();
    }

    public boolean isPaused() {
        return paused;
    }

    public final void startActionMode(ActionMode.Callback callback) {
        Activity activity = getActivity();

        if (!(activity instanceof AppCompatActivity))
            throw new UnsupportedOperationException("operation only supported when using AppCompatActivity");

        ((AppCompatActivity) activity).startSupportActionMode(callback);
    }

    /**
     * onCreateView calls this before it returns the rootView it has just inflated
     *
     * @param rootView
     * @param savedInstanceState
     */
    protected void initComponents(final View rootView, Bundle savedInstanceState) {
    }

    protected final <T extends View> T findView(View v, @IdRes int id) {
        T result = null;
        if (v != null) {
            result = v.findViewById(id);
        }
        return result;
    }
}
