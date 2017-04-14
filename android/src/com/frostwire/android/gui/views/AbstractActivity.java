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
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;

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
        UIUtils.ScreenOrientationLocker.onRotationRequested(AbstractActivity.this,  getWindowManager().getDefaultDisplay().getRotation());
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
        setToolbarView(view, Gravity.LEFT | Gravity.CENTER_VERTICAL);
    }
}
