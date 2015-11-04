/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.andrew.apollo.ui.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.GridView;

import com.frostwire.android.R;
import com.andrew.apollo.recycler.RecycleHolder;
import com.andrew.apollo.ui.MusicHolder;
import com.andrew.apollo.utils.ThemeUtils;
import com.devspark.appmsg.AppMsg;

import java.util.List;

/**
 * Used to show all of the available themes on a user's device.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class ThemeFragment extends Fragment implements OnItemClickListener {

    private static final int OPEN_IN_PLAY_STORE = 0;

    private GridView mGridView;

    private PackageManager mPackageManager;

    private List<ResolveInfo> mThemes;

    private String[] mEntries;

    private String[] mValues;

    private Drawable[] mThemePreview;

    private Resources mThemeResources;

    private String mThemePackageName;

    private String mThemeName;

    private ThemesAdapter mAdapter;

    private ThemeUtils mTheme;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     */
    public ThemeFragment() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
            final Bundle savedInstanceState) {
        // The View for the fragment's UI
        final ViewGroup rootView = (ViewGroup)inflater.inflate(R.layout.grid_base, null);
        // Initialize the grid
        mGridView = (GridView)rootView.findViewById(R.id.grid_base);
        // Release any reference to the recycled Views
        mGridView.setRecyclerListener(new RecycleHolder());
        // Set the new theme
        mGridView.setOnItemClickListener(this);
        // Listen for ContextMenus to be created
        mGridView.setOnCreateContextMenuListener(this);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            // Limit the columns to one in portrait mode
            mGridView.setNumColumns(1);
        } else {
            // And two for landscape
            mGridView.setNumColumns(2);
        }
        return rootView;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final Intent apolloThemeIntent = new Intent("com.andrew.apollo.THEMES");
        apolloThemeIntent.addCategory("android.intent.category.DEFAULT");

        mPackageManager = getActivity().getPackageManager();
        mThemes = mPackageManager.queryIntentActivities(apolloThemeIntent, 0);
        mEntries = new String[mThemes.size() + 1];
        mValues = new String[mThemes.size() + 1];
        mThemePreview = new Drawable[mThemes.size() + 1];

        // Default items
        mEntries[0] = getString(R.string.app_name);
        // mValues[0] = ThemeUtils.APOLLO_PACKAGE;
        mThemePreview[0] = getResources().getDrawable(R.drawable.theme_preview);

        for (int i = 0; i < mThemes.size(); i++) {
            mThemePackageName = mThemes.get(i).activityInfo.packageName.toString();
            mThemeName = mThemes.get(i).loadLabel(mPackageManager).toString();
            mEntries[i + 1] = mThemeName;
            mValues[i + 1] = mThemePackageName;

            // Theme resources
            try {
                mThemeResources = mPackageManager.getResourcesForApplication(mThemePackageName
                        .toString());
            } catch (final NameNotFoundException ignored) {
            }

            // Theme preview
            final int previewId = mThemeResources.getIdentifier("theme_preview", "drawable", //$NON-NLS-2$
                    mThemePackageName.toString());
            if (previewId != 0) {
                mThemePreview[i + 1] = mThemeResources.getDrawable(previewId);
            }
        }

        // Initialize the Adapter
        mAdapter = new ThemesAdapter(getActivity(), R.layout.fragment_themes_base);
        // Bind the data
        mGridView.setAdapter(mAdapter);

        // Get the theme utils
        mTheme = new ThemeUtils(getActivity());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
            final ContextMenuInfo menuInfo) {
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
        if (info.position > 0) {
            // Open to the theme's Play Store page
            menu.add(Menu.NONE, OPEN_IN_PLAY_STORE, Menu.NONE,
                    getString(R.string.context_menu_open_in_play_store));
        }
        super.onCreateContextMenu(menu, v, menuInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo)item.getMenuInfo();
        switch (item.getItemId()) {
            case OPEN_IN_PLAY_STORE:
                ThemeUtils.openAppPage(getActivity(), mValues[info.position]);
                return true;
            default:
                break;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
            final long id) {
        mTheme.setThemePackageName(mValues[position]);
        AppMsg.makeText(getActivity(),
                getString(R.string.theme_set, mEntries[position]), AppMsg.STYLE_CONFIRM)
                .show();
    }

    /**
     * Populates the {@link GridView} with the available themes
     */
    private class ThemesAdapter extends ArrayAdapter<ResolveInfo> {

        /**
         * Number of views (ImageView and TextView)
         */
        private static final int VIEW_TYPE_COUNT = 2;

        /**
         * The resource ID of the layout to inflate
         */
        private final int mLayoutID;

        /**
         * Used to cache the theme info
         */
        private DataHolder[] mData;

        /**
         * Constructor of <code>ThemesAdapter</code>
         * 
         * @param context The {@link Context} to use.
         * @param layoutID The resource ID of the view to inflate.
         */
        public ThemesAdapter(final Context context, final int layoutID) {
            super(context, 0);
            // Get the layout ID
            mLayoutID = layoutID;
            // Build the cache
            buildCache();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount() {
            return mEntries.length;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {

            /* Recycle ViewHolder's items */
            MusicHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(mLayoutID, parent, false);
                holder = new MusicHolder(convertView);
                convertView.setTag(holder);
            } else {
                holder = (MusicHolder)convertView.getTag();
            }

            // Retrieve the data holder
            final DataHolder dataHolder = mData[position];

            // Set the theme preview
            holder.mImage.get().setImageDrawable(dataHolder.mPreview);
            // Set the theme name
            holder.mLineOne.get().setText(dataHolder.mName);
            return convertView;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasStableIds() {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getViewTypeCount() {
            return VIEW_TYPE_COUNT;
        }

        /**
         * Method used to cache the data used to populate the list or grid. The
         * idea is to cache everything before
         * {@code #getView(int, View, ViewGroup)} is called.
         */
        private void buildCache() {
            mData = new DataHolder[getCount()];
            for (int i = 0; i < getCount(); i++) {
                // Build the data holder
                mData[i] = new DataHolder();
                // Theme names (line one)
                mData[i].mName = mEntries[i];
                // Theme preview
                mData[i].mPreview = mThemePreview[i];
            }
        }

    }

    /**
     * @param view The {@link View} used to initialize content
     */
    public final static class DataHolder {

        public String mName;

        public Drawable mPreview;

        /**
         * Constructor of <code>DataHolder</code>
         */
        public DataHolder() {
            super();
        }
    }
}
