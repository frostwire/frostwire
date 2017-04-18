/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.frostwire.android.R;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractActivity extends AppCompatActivity {

    private final int layoutResId;
    private final ArrayList<String> fragmentTags;

    private boolean paused;
    private View toolbarView;

    private static boolean menuIconsVisible = false;

    public AbstractActivity(int layoutResId) {
        this.layoutResId = layoutResId;
        this.fragmentTags = new ArrayList<>();
        this.paused = false;
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        String tag = fragment.getTag();
        if (tag != null && !fragmentTags.contains(tag)) {
            fragmentTags.add(tag);
        }
    }

    @Override
    protected void onResume() {
        paused = false;
        super.onResume();
    }

    @Override
    protected void onPause() {
        paused = true;
        super.onPause();
    }

    public boolean isPaused() {
        return paused;
    }

    public final List<Fragment> getFragments() {
        List<Fragment> result = new LinkedList<>();

        FragmentManager fm = getFragmentManager();
        for (String tag : fragmentTags) {
            Fragment f = fm.findFragmentByTag(tag);
            if (f != null) {
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
        setToolbar();
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        Toolbar toolbar = findToolbar();
        if (toolbar != null) {
            toolbar.setTitle(title);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean r = super.onCreateOptionsMenu(menu);
        setMenuIconsVisible(menu);
        return r;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        setMenuIconsVisible(menu);
    }

    @Nullable
    @Override
    public ActionMode startSupportActionMode(@NonNull ActionMode.Callback callback) {
        return super.startSupportActionMode(new ActionModeCallback(callback));
    }

    protected void initComponents(Bundle savedInstanceState) {
    }

    protected void initToolbar(Toolbar toolbar) {
    }

    private void setToolbar() {
        Toolbar toolbar = findToolbar();
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            toolbar.setTitle(getTitle());
            initToolbar(toolbar);
        }
    }

    @SuppressWarnings("unchecked")
    protected final <T extends View> T findView(int id) {
        return (T) super.findViewById(id);
    }

    protected final Toolbar findToolbar() {
        return findView(R.id.toolbar_main);
    }

    protected final void setToolbarView(View view, int gravity) {
        FrameLayout placeholder = findView(R.id.toolbar_main_placeholder);
        if (toolbarView != null && placeholder != null) {
            placeholder.removeView(toolbarView);
        }
        toolbarView = view;
        if (toolbarView != null && placeholder != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = gravity;
            placeholder.addView(toolbarView, params);
            placeholder.setVisibility(View.VISIBLE);
        }
    }

    protected final void setToolbarView(View view) {
        setToolbarView(view, Gravity.START | Gravity.CENTER_VERTICAL);
    }

    /**
     * This settings is application wide and apply to all activities and
     * fragments that use our internal abstract activity. This enable
     * or disable the menu icons for both options and context menu.
     *
     * @param visible if icons are visible or not
     */
    public static void setMenuIconsVisible(boolean visible) {
        menuIconsVisible = visible;
    }

    private static void setMenuIconsVisible(Menu menu) {
        if (menu == null) { // in case the framework changes
            return;
        }

        // android by default set the field to false
        if (!menuIconsVisible) {
            return; // quick return
        }

        Class<?> clazz = menu.getClass();
        Field f = null;
        while (clazz != null && f == null) {
            try {
                f = clazz.getDeclaredField("mOptionalIconsVisible");
            } catch (Throwable e) {
                // next, no need to get them all, balanced cost of exception
            }
            clazz = clazz.getSuperclass();
        }

        if (f == null) {
            // the menu framework changed, nothing we can do, but visual
            // will reveal that a fix is necessary
            return;
        }

        try {
            f.setAccessible(true);
            f.set(menu, menuIconsVisible);
        } catch (Throwable e) {
            // ignore, unable to set icons for the menu, but visual
            // will reveal that a fix is necessary
        }
    }

    private static final class ActionModeCallback implements ActionMode.Callback {

        private ActionMode.Callback cb;

        private ActionModeCallback(ActionMode.Callback cb) {
            this.cb = cb;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            boolean r = cb.onCreateActionMode(mode, menu);
            setMenuIconsVisible(menu);
            return r;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return cb.onPrepareActionMode(mode, menu);
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return cb.onActionItemClicked(mode, item);
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            cb.onDestroyActionMode(mode);
        }
    }
}
