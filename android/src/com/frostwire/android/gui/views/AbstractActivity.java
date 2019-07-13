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
import android.content.BroadcastReceiver;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.frostwire.android.R;
import com.frostwire.android.util.Debug;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractActivity extends AppCompatActivity {

    private final int layoutResId;
    private final ArrayList<String> fragmentTags;

    private boolean paused;

    private static boolean menuIconsVisible = false;

    public AbstractActivity(@LayoutRes int layoutResId) {
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

    /**
     * Returns a list of the currently attached fragments with
     * a non null TAG.
     * <p>
     * If you are in API >= 26, the new method {@link FragmentManager#getFragments()}
     * give you access to a list of all fragments that are added to the FragmentManager.
     *
     * @return the list of attached fragments with TAG.
     */
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

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        try {
            super.unregisterReceiver(receiver);
        } catch (Throwable e) {
            if (Debug.isEnabled()) {
                // rethrow to actually see it and fix it
                throw e;
            }
            // else, ignore exception, it could be to a bad call from
            // third party frameworks
        }
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

    protected final <T extends View> T findView(@IdRes int id) {
        return super.findViewById(id);
    }

    @SuppressWarnings("unchecked")
    protected final <T extends Fragment> T findFragment(@IdRes int id) {
        return (T) getFragmentManager().findFragmentById(id);
    }

    /**
     * Returns the first fragment of type T (and with a non null tag) found in the
     * internal fragment manager.
     * <p>
     * This method should cover 98% of the actual UI designs, if you ever get to have
     * two fragments of the same type in the activity, review the components design.
     * If you present two different sets of information with the same type, that's not
     * a good OOP design, and if you present the same information, that's not a good
     * UI/UX design.
     *
     * @param clazz the class of the fragment to lookup
     * @param <T>   the type of the fragment to lookup
     * @return the first fragment of type T if found.
     */
    @SuppressWarnings("unchecked")
    public final <T extends Fragment> T findFragment(Class<T> clazz) {
        for (Fragment f : getFragments()) {
            if (clazz.isInstance(f)) {
                return (T) f;
            }
        }
        return null;
    }

    public final Toolbar findToolbar() {
        return findView(R.id.toolbar_main);
    }

    protected final void setToolbarView(View view, int gravity) {
        FrameLayout placeholder = findView(R.id.toolbar_main_placeholder);
        if (placeholder != null) {
            placeholder.removeAllViews();
        }
        if (view != null && placeholder != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = gravity;
            placeholder.addView(view, params);
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

        private final ActionMode.Callback cb;

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
