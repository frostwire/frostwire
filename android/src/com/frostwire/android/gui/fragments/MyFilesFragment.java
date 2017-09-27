/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.fragments;

import android.app.Activity;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.design.widget.TabLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.SearchView;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.GridView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.andrew.apollo.MusicPlaybackService;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.AndroidPlatform;
import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Finger;
import com.frostwire.android.gui.Peer;
import com.frostwire.android.gui.adapters.menu.AddToPlaylistMenuAction;
import com.frostwire.android.gui.adapters.menu.CopyMagnetMenuAction;
import com.frostwire.android.gui.adapters.menu.DeleteFileMenuAction;
import com.frostwire.android.gui.adapters.menu.FileInformationAction;
import com.frostwire.android.gui.adapters.menu.FileListAdapter;
import com.frostwire.android.gui.adapters.menu.OpenMenuAction;
import com.frostwire.android.gui.adapters.menu.RenameFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.android.gui.adapters.menu.SendFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SetAsRingtoneMenuAction;
import com.frostwire.android.gui.adapters.menu.SetAsWallpaperMenuAction;
import com.frostwire.android.gui.util.ComposedOnScrollListener;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.SwipeLayout;
import com.frostwire.util.Logger;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public class MyFilesFragment extends AbstractFragment implements LoaderCallbacks<Object>, MainFragment {

    private static final Logger LOG = Logger.getLogger(MyFilesFragment.class);
    private static final int LOADER_FILES_ID = 0;
    private static final String EXTRA_LAST_FILE_TYPE_CLICKED = "com.frostwire.android.extra.byte.EXTRA_LAST_FILE_TYPE_CLICKED";

    private final BroadcastReceiver broadcastReceiver;

    private SwipeRefreshLayout swipeRefresh;
    private GridView list;
    private FileListAdapter adapter;
    private MenuItem checkBoxMenuItem;
    private RelativeLayout selectAllCheckboxContainer;
    private CheckBox selectAllCheckbox;
    private TabLayout tabLayout;
    private CompoundButton.OnCheckedChangeListener selectAllCheckboxListener;
    private boolean selectAllModeOn;
    private Peer peer;
    private View header;
    private long lastAdapterRefresh;
    private String previousFilter;
    private SparseArray<Set<FileListAdapter.FileDescriptorItem>> checkedItemsMap;

    // given the byte:fileType as the index, this array will match the corresponding UXAction code.
    // no if's necessary, random access -> O(1)
    private final int[] browseUXActions = {
            UXAction.LIBRARY_BROWSE_FILE_TYPE_AUDIO,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_PICTURES,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_VIDEOS,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_DOCUMENTS,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_APPLICATIONS,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_RINGTONES,
            UXAction.LIBRARY_BROWSE_FILE_TYPE_TORRENTS
    };

    private final byte[] tabPositionToFileType = new byte[] {
            Constants.FILE_TYPE_AUDIO,
            Constants.FILE_TYPE_RINGTONES,
            Constants.FILE_TYPE_VIDEOS,
            Constants.FILE_TYPE_PICTURES,
            Constants.FILE_TYPE_DOCUMENTS,
            Constants.FILE_TYPE_TORRENTS };

    private final int[] fileTypeToTabPosition = new int[] {
            0,  // 0x0 Audio @ 0
            3,  // 0x1 Picture @ 3
            2,  // 0x2 Video @ 2
            4,  // 0x3 Documents @ 4
            -1, // 0x4 Application @ N/A on My Files
            1,  // 0x5 Ringtones @ 1
            5   // 0x6 Torrents @ 5
    };

    /**
     * This implements the toolbar's action mode view and its menu
     */
    private final MyFilesActionModeCallback selectionModeCallback;
    private byte lastFileType = Constants.FILE_TYPE_AUDIO;

    public MyFilesFragment() {
        super(R.layout.fragment_my_files);
        broadcastReceiver = new LocalBroadcastReceiver();
        setHasOptionsMenu(true);
        this.peer = new Peer();
        checkedItemsMap = new SparseArray<>();
        selectionModeCallback = new MyFilesActionModeCallback();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (savedInstanceState != null) {
            lastFileType = savedInstanceState.getByte(EXTRA_LAST_FILE_TYPE_CLICKED);
            clickFileTypeTab(lastFileType);
        }
        setRetainInstance(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putByte(EXTRA_LAST_FILE_TYPE_CLICKED, lastFileType);
        super.onSaveInstanceState(outState);
    }

    @Override
    public Loader<Object> onCreateLoader(int id, Bundle args) {
        if (id == LOADER_FILES_ID && args != null) {
            return createLoaderFiles(args.getByte("fileType"));
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Object> loader, Object data) {
        if (data == null) {
            LOG.warn("Something wrong, data is null");
            return;
        }
        if (loader.getId() == LOADER_FILES_ID) {
            updateFiles((Object[]) data);
        }
        updateHeader();
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
    }

    @Override
    public void onLoaderReset(Loader<Object> loader) {
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.fragment_my_files_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
        initToolbarSearchFilter(menu);
        initToolbarCheckbox(menu);
    }

    private void initToolbarCheckbox(Menu menu) {
        checkBoxMenuItem = menu.findItem(R.id.fragment_my_files_menu_checkbox);
        checkBoxMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                onToolbarMenuSelectModeCheckboxClick();
                return true;
            }
        });
        refreshCheckBoxMenuItemVisibility();
        selectAllCheckbox.setOnCheckedChangeListener(selectAllCheckboxListener);
    }

    private void initToolbarSearchFilter(Menu menu) {
        final SearchView searchView = (SearchView) menu.findItem(R.id.fragment_my_files_menu_filter).getActionView();
        searchView.setFocusable(true);
        if (isAdded()) {
            searchView.setQueryHint(getResources().getString(R.string.filter_ellipsis));
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performFilter(query);
                UIUtils.hideKeyboardFromActivity(getActivity());
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                performFilter(newText);
                return false;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.fragment_my_files_menu_filter:
                return true;
            case R.id.fragment_my_files_menu_checkbox:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        initBroadcastReceiver();
        if (adapter != null) {
            restorePreviouslyChecked();
            clickFileTypeTab(adapter.getFileType());
        }
        updateHeader();
    }

    private void restorePreviouslyChecked() {
        if (adapter != null) {
            Set<FileListAdapter.FileDescriptorItem> previouslyChecked = checkedItemsMap.get(adapter.getFileType());
            if (previouslyChecked != null && !previouslyChecked.isEmpty()) {
                adapter.setChecked(previouslyChecked);
            }
        }
    }

    private void savePreviouslyCheckedFileDescriptors() {
        if (adapter != null) {
            final Set<FileListAdapter.FileDescriptorItem> checked = adapter.getChecked();
            if (checked != null && !checked.isEmpty()) {
                Set<FileListAdapter.FileDescriptorItem> checkedCopy = new HashSet<>();
                for (FileListAdapter.FileDescriptorItem fileDescriptorItem : checked) {
                    checkedCopy.add(fileDescriptorItem);
                }
                checkedItemsMap.put(adapter.getFileType(), checkedCopy);
            }
        }
    }

    private void initBroadcastReceiver() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.ACTION_REFRESH_FINGER);
        filter.addAction(Constants.ACTION_MEDIA_PLAYER_PLAY);
        filter.addAction(Constants.ACTION_MEDIA_PLAYER_PAUSED);
        filter.addAction(Constants.ACTION_MEDIA_PLAYER_STOPPED);
        filter.addAction(Constants.ACTION_FILE_ADDED_OR_REMOVED);
        filter.addAction(MusicPlaybackService.META_CHANGED);
        filter.addAction(MusicPlaybackService.PLAYSTATE_CHANGED);
        filter.addAction(MusicPlaybackService.SIMPLE_PLAYSTATE_STOPPED);
        getActivity().registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        savePreviouslyCheckedFileDescriptors();
        MusicUtils.stopSimplePlayer();
        getActivity().unregisterReceiver(broadcastReceiver);
    }

    @Override
    public View getHeader(Activity activity) {
        LayoutInflater inflater = LayoutInflater.from(activity);
        header = inflater.inflate(R.layout.view_my_files_header, null, false);
        updateHeader();
        return header;
    }

    @Override
    public void onShow() {
    }

    @Override
    protected void initComponents(View v, Bundle savedInstanceState) {
        findView(v, R.id.fragment_my_files_select_all_container).setVisibility(View.GONE);
        findView(v, R.id.progressContainer).setVisibility(View.GONE);
        selectAllCheckboxListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onSelectAllChecked(isChecked);
            }
        };
        selectAllCheckbox = findView(v, R.id.fragment_my_files_select_all_checkbox);
        selectAllCheckboxContainer = findView(v, R.id.fragment_my_files_select_all_container);
        swipeRefresh = findView(v, R.id.fragment_my_files_swipe_refresh);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                long now = SystemClock.elapsedRealtime();
                if ((now - lastAdapterRefresh) > 5000) {
                    refreshSelection();
                } else {
                    swipeRefresh.setRefreshing(false);
                }
            }
        });
        list = findView(v, R.id.fragment_my_files_gridview);
        list.setOnScrollListener(new ComposedOnScrollListener.FastScrollDisabledWhenIdleOnScrollListener());

        SwipeLayout swipe = findView(v, R.id.fragment_my_files_swipe);
        swipe.setOnSwipeListener(new SwipeLayout.OnSwipeListener() {
            @Override
            public void onSwipeLeft() {
                switchToThe(true);
            }

            @Override
            public void onSwipeRight() {
                switchToThe(false);
            }
        });

        tabLayout = findView(v, R.id.fragment_my_files_tab_layout_file_type);
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                tabClicked(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                tabClicked(tab.getPosition());
            }
        });
    }

    private void tabClicked(int tabPosition) {
        byte fileType = tabPositionToFileType[tabPosition];
        lastFileType = fileType;
        if (adapter != null) {
            saveListViewVisiblePosition(adapter.getFileType());
            adapter.clear();
            adapter.clearChecked();
        }
        reloadFiles(fileType);
        refreshCheckBoxMenuItemVisibility();
        if (selectAllCheckbox != null) {
            selectAllModeOn = false;
            selectAllCheckbox.setChecked(false);
            selectAllCheckboxContainer.setVisibility(View.GONE);
        }
        logBrowseAction(fileType);
    }

    private void logBrowseAction(byte fileType) {
        try {
            UXStats.instance().log(browseUXActions[fileType]);
        } catch (Throwable ignored) {
        }
    }

    private void reloadFiles(byte fileType) {
        try {
            if (isAdded()) {
                LoaderManager loaderManager = getLoaderManager();
                loaderManager.destroyLoader(LOADER_FILES_ID);
                Bundle bundle = new Bundle();
                bundle.putByte("fileType", fileType);
                loaderManager.restartLoader(LOADER_FILES_ID, bundle, this);
            }
        } catch (Throwable t) {
            LOG.error(t.getMessage(), t);
        }
    }

    private Loader<Object> createLoaderFiles(final byte fileType) {
        AsyncTaskLoader<Object> loader = new AsyncTaskLoader<Object>(getActivity()) {
            @Override
            public Object loadInBackground() {
                try {
                    return new Object[]{fileType, peer.browse(fileType)};
                } catch (Throwable e) {
                    LOG.error("Error performing finger", e);
                }
                return null;
            }
        };
        try {
            loader.forceLoad();
        } catch (Throwable t) {
            LOG.warn("createLoaderFiles(fileType="+fileType+") loader.forceLoad() failed. Continuing.", t);
        }
        return loader;
    }

    private void updateHeader() {
        if (peer == null) {
            LOG.warn("Something wrong. peer is null");
            return;
        }
        peer.finger(new Finger.FingerCallback() {
            @Override
            public void onFinger(final Finger finger) {
                if (isAdded()) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (header != null) {
                                byte fileType = adapter != null ? adapter.getFileType() : Constants.FILE_TYPE_AUDIO;
                                int numTotal = 0;
                                switch (fileType) {
                                    case Constants.FILE_TYPE_TORRENTS:
                                        numTotal = finger.numTotalTorrentFiles;
                                        break;
                                    case Constants.FILE_TYPE_AUDIO:
                                        numTotal = finger.numTotalAudioFiles;
                                        break;
                                    case Constants.FILE_TYPE_DOCUMENTS:
                                        numTotal = finger.numTotalDocumentFiles;
                                        break;
                                    case Constants.FILE_TYPE_PICTURES:
                                        numTotal = finger.numTotalPictureFiles;
                                        break;
                                    case Constants.FILE_TYPE_RINGTONES:
                                        numTotal = finger.numTotalRingtoneFiles;
                                        break;
                                    case Constants.FILE_TYPE_VIDEOS:
                                        numTotal = finger.numTotalVideoFiles;
                                        break;
                                }
                                
                                if (isAdded()) {
                                    String fileTypeStr = getString(R.string.my_filetype, UIUtils.getFileTypeAsString(getResources(), fileType));
                                    TextView title = header.findViewById(R.id.view_my_files_header_text_title);
                                    title.setText(fileTypeStr);
                                }
                                TextView total = header.findViewById(R.id.view_my_files_header_text_total);
                                total.setText("(" + String.valueOf(numTotal) + ")");
                            }

                            if (adapter == null) {
                                clickFileTypeTab(lastFileType);
                            }
                            refreshCheckBoxMenuItemVisibility();
                            MusicUtils.stopSimplePlayer();
                            restoreListViewScrollPosition();
                        }
                    });
                }
            }
        });
    }

    private void clickFileTypeTab(byte lastFileType) {
        tabLayout.getTabAt(fileTypeToTabPosition[lastFileType]).select();
    }

    private void restoreListViewScrollPosition() {
        if (adapter != null) {
            int savedListViewVisiblePosition = getSavedListViewVisiblePosition(adapter.getFileType());
            savedListViewVisiblePosition = (savedListViewVisiblePosition > 0) ? savedListViewVisiblePosition + 1 : 0;
            list.setSelection(savedListViewVisiblePosition);
        }
    }

    private void updateFiles(Object[] data) {
        if (data == null || data.length < 2 || data[1] == null) {
            LOG.warn("Something wrong, data is null");
            return;
        }
        try {
            byte fileType = (Byte) data[0];
            List<FileDescriptor> items = (List<FileDescriptor>) data[1];
            adapter = new FileListAdapter(getActivity(), items, fileType, selectAllModeOn) {
                @Override
                protected void onLocalPlay() {
                    if (adapter != null) {
                        saveListViewVisiblePosition(adapter.getFileType());
                    }
                }

                @Override
                protected void onItemChecked(View v, boolean isChecked) {
                    super.onItemChecked(v, isChecked);
                    autoCheckUnCheckSelectAllCheckbox();
                    selectionModeCallback.onItemChecked(getActivity(), adapter.getCheckedCount());
                }

                @Override
                protected boolean onItemLongClicked(View v) {
                    return onFileItemLongClicked(v);
                }

                @Override
                protected void onItemClicked(View v) {
                    onFileItemClicked(v);
                }
            };
            adapter.setCheckboxesVisibility(selectAllModeOn);
            list.setNumColumns(adapter.getNumColumns());
            restorePreviouslyChecked();
            if (previousFilter != null) {
                performFilter(previousFilter);
            } else {
                updateAdapter();
            }
        } catch (Throwable e) {
            LOG.error("Error updating files in list", e);
        }
    }

    private void onToolbarMenuSelectModeCheckboxClick() {
        selectAllModeOn = !selectAllModeOn;
        enableSelectAllMode(selectAllModeOn, selectAllModeOn);
        if (selectAllModeOn) {
            selectionModeCallback.onItemChecked(getActivity(), adapter.getCheckedCount());
        }
    }

    private void enableSelectAllMode(boolean selectAll, boolean autoCheckAll) {
        selectAllModeOn = selectAll;
        selectAllCheckboxContainer.setVisibility(selectAllModeOn && adapter.getCount() > 0 ? View.VISIBLE : View.GONE);
        adapter.setSelectAllMode(selectAllModeOn);
        adapter.setCheckboxesVisibility(selectAllModeOn);
        adapter.setShowMenuOnClick(!selectAll);
        selectAllCheckbox.setChecked(autoCheckAll);
        swipeRefresh.setEnabled(!selectAll);
        if (selectAllModeOn) {
            startActionMode(selectionModeCallback);
        } else {
            adapter.clearChecked();
        }
        tabLayout.setVisibility(!selectAllModeOn ? View.VISIBLE : View.GONE);
    }

    private void autoCheckUnCheckSelectAllCheckbox() {
        selectAllCheckbox.setOnCheckedChangeListener(null);
        if (selectAllModeOn) {
            boolean allChecked = adapter.getCheckedCount() == adapter.getCount();
            selectAllCheckbox.setChecked(allChecked);
            selectAllCheckbox.setText(allChecked ? R.string.deselect_all : R.string.select_all);
        }
        selectAllCheckbox.setOnCheckedChangeListener(selectAllCheckboxListener);
    }

    private void onSelectAllChecked(boolean isChecked) {
        selectAllCheckbox.setText(isChecked ? R.string.deselect_all : R.string.select_all);
        if (isChecked) {
            adapter.checkAll();
        } else {
            adapter.clearChecked();
        }
        selectionModeCallback.onItemChecked(getActivity(), isChecked ? adapter.getCount() : 0);
    }

    private void onFileItemClicked(View v) {
        if (adapter == null || adapter.getFileType() == Constants.FILE_TYPE_RINGTONES) {
            return;
        }
        if (selectAllModeOn) {
            int position = adapter.getViewPosition(v);
            if (position == -1) {
                return;
            }
            Set<FileListAdapter.FileDescriptorItem> checked = adapter.getChecked();
            boolean wasChecked = checked.contains(v.getTag());
            adapter.setChecked(position, !wasChecked);
            adapter.notifyDataSetInvalidated();
            autoCheckUnCheckSelectAllCheckbox();
            selectionModeCallback.onItemChecked(getActivity(), adapter.getCheckedCount());
        }

    }

    private boolean onFileItemLongClicked(View v) {
        if (adapter == null || adapter.getFileType() == Constants.FILE_TYPE_RINGTONES) {
            return false;
        }
        int position = adapter.getViewPosition(v);
        if (position == -1) {
            return false;
        }
        enableSelectAllMode(!selectAllModeOn, false);
        onSelectAllChecked(false);
        adapter.setChecked(position, selectAllModeOn);
        if (selectAllModeOn) {
            selectionModeCallback.onItemChecked(getActivity(), 1);
        } else {
            selectionModeCallback.onDestroyActionMode(null);
        }
        return true;
    }

    private void updateAdapter() {
        list.setAdapter(adapter);
        refreshCheckBoxMenuItemVisibility();
        restoreListViewScrollPosition();
    }

    private void performFilter(String filterString) {
        this.previousFilter = filterString;
        if (adapter != null && filterString != null) {
            adapter.getFilter().filter(filterString, new Filter.FilterListener() {
                @Override
                public void onFilterComplete(int i) {
                    updateAdapter();
                }
            });
        }
    }

    private void saveListViewVisiblePosition(byte fileType) {
        int firstVisiblePosition = list.getFirstVisiblePosition();
        ConfigurationManager.instance().setInt(Constants.MY_FILES_FRAGMENT_LISTVIEW_FIRST_VISIBLE_POSITION + fileType, firstVisiblePosition);
    }

    private int getSavedListViewVisiblePosition(byte fileType) {
        //will return 0 if not found.
        return ConfigurationManager.instance().getInt(Constants.MY_FILES_FRAGMENT_LISTVIEW_FIRST_VISIBLE_POSITION + fileType);
    }

    private void switchToThe(boolean right) {
        int currentTabPosition = tabLayout.getSelectedTabPosition();
        int nextTabPosition = (right ? ++currentTabPosition : --currentTabPosition ) % 6;
        if (nextTabPosition == -1) {
            nextTabPosition = 5;
        }
        tabLayout.getTabAt(nextTabPosition).select();
    }

    private void refreshCheckBoxMenuItemVisibility() {
        if (checkBoxMenuItem != null) {
            checkBoxMenuItem.setVisible(lastFileType != Constants.FILE_TYPE_RINGTONES && list.getCount() > 0);
        }
    }

    private class MyFilesActionModeCallback implements android.support.v7.view.ActionMode.Callback {
        private ActionMode mode;
        private Menu menu;
        private int numChecked;

        public void onItemChecked(Context context, int numChecked) {
            this.numChecked = numChecked;
            if (mode != null) {
                mode.setTitle(numChecked + " " + context.getString(R.string.selected));
                mode.invalidate();
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            this.mode = mode;
            this.menu = menu;
            mode.getMenuInflater().inflate(R.menu.fragment_my_files_action_mode_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            if (numChecked == 0) {
                hideAllMenuActions();
            } else if (numChecked > 0) {
                FileListAdapter.FileDescriptorItem[] fileDescriptorItems =
                        adapter.getChecked().toArray(new FileListAdapter.FileDescriptorItem[0]);
                if (fileDescriptorItems.length > 0) {
                    updateMenuActionsVisibility(fileDescriptorItems[0]);
                }
            }
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Activity context = getActivity();
            FileListAdapter.FileDescriptorItem[] fileDescriptorItems =
                    adapter.getChecked().toArray(new FileListAdapter.FileDescriptorItem[0]);
            if (fileDescriptorItems.length == 0) {
                return false;
            }
            List<FileDescriptor> fileDescriptors = new ArrayList<>(fileDescriptorItems.length);
            for (FileListAdapter.FileDescriptorItem fileDescriptorItem : fileDescriptorItems) {
                fileDescriptors.add(fileDescriptorItem.fd);
            }
            FileListAdapter.FileDescriptorItem fileDescriptorItem = fileDescriptorItems[0];
            final FileDescriptor fd = fileDescriptorItem.fd;
            switch (item.getItemId()) {
                case R.id.fragment_my_files_action_mode_menu_delete:
                    new DeleteFileMenuAction(context, adapter, fileDescriptors).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_seed:
                    new SeedAction(context, fd, null).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_open:
                    new OpenMenuAction(context, fd).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_file_information:
                    new FileInformationAction(context, fd).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_use_as_ringtone:
                    new SetAsRingtoneMenuAction(context, fd).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_use_as_wallpaper:
                    new SetAsWallpaperMenuAction(context, fd).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_copy_magnet:
                    new CopyMagnetMenuAction(context,
                            R.drawable.contextmenu_icon_magnet,
                            R.string.transfers_context_menu_copy_magnet,
                            R.string.transfers_context_menu_copy_magnet_copied,
                            fd.filePath).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_copy_info_hash:
                    new CopyMagnetMenuAction(context,
                            R.drawable.contextmenu_icon_copy,
                            R.string.transfers_context_menu_copy_infohash,
                            R.string.transfers_context_menu_copy_infohash_copied,
                            fd.filePath, false).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_rename:
                    new RenameFileMenuAction(context, adapter, fd).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_add_to_playlist:
                    new AddToPlaylistMenuAction(context, fileDescriptors).onClick();
                    break;
                case R.id.fragment_my_files_action_mode_menu_share:
                    new SendFileMenuAction(context, fd).onClick();
                    break;
            }
            enableSelectAllMode(false, false);
            mode.finish();
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            enableSelectAllMode(false, false);
            this.mode.finish();
        }

        private void hideAllMenuActions() {
            if (menu != null && menu.size() > 0) {
                for (int i = 0; i < menu.size(); i++) {
                    menu.getItem(i).setVisible(false);
                }
            }
        }

        private void updateMenuActionsVisibility(FileListAdapter.FileDescriptorItem selectedFileDescriptor) {
            List<Integer> actionsToHide = new ArrayList<>();
            FileDescriptor fd = selectedFileDescriptor.fd;
            boolean canOpenFile = fd.mime != null && (fd.mime.contains("audio") || fd.mime.contains("bittorrent") || fd.filePath != null);
            if (numChecked > 1) {
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_seed);
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_open);
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_file_information);
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_use_as_ringtone);
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_use_as_wallpaper);
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_rename);
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_copy_magnet);
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_copy_info_hash);
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_share);
            } else {
                if (numChecked == 1) {
                    if (!canOpenFile) {
                        actionsToHide.add(R.id.fragment_my_files_action_mode_menu_open);
                    }
                    if (fd.fileType != Constants.FILE_TYPE_AUDIO) {
                        actionsToHide.add(R.id.fragment_my_files_action_mode_menu_use_as_ringtone);
                    }
                    if (fd.fileType != Constants.FILE_TYPE_PICTURES) {
                        actionsToHide.add(R.id.fragment_my_files_action_mode_menu_use_as_wallpaper);
                    }
                    if (fd.fileType == Constants.FILE_TYPE_APPLICATIONS) {
                        actionsToHide.add(R.id.fragment_my_files_action_mode_menu_rename);
                        actionsToHide.add(R.id.fragment_my_files_action_mode_menu_share);
                        actionsToHide.add(R.id.fragment_my_files_action_mode_menu_delete);
                    }
                    if (fd.mime != null && !fd.mime.equals(Constants.MIME_TYPE_BITTORRENT)) {
                        actionsToHide.add(R.id.fragment_my_files_action_mode_menu_copy_magnet);
                        actionsToHide.add(R.id.fragment_my_files_action_mode_menu_copy_info_hash);
                    }
                }
            }
            if (fd.filePath != null && AndroidPlatform.saf(new File(fd.filePath))) {
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_seed);
            }
            if (fd.fileType != Constants.FILE_TYPE_AUDIO) {
                actionsToHide.add(R.id.fragment_my_files_action_mode_menu_add_to_playlist);
            }
            if (menu != null && menu.size() > 0) {
                for (int i = 0; i < menu.size(); i++) {
                    MenuItem item = menu.getItem(i);
                    item.setVisible(!actionsToHide.contains(item.getItemId()));
                }
            }
        }
    }

    private final class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (context instanceof Activity) {
                ((Activity) context).invalidateOptionsMenu();
            }
            if (action.equals(Constants.ACTION_MEDIA_PLAYER_PLAY) ||
                    action.equals(Constants.ACTION_MEDIA_PLAYER_STOPPED) ||
                    action.equals(Constants.ACTION_MEDIA_PLAYER_PAUSED) ||
                    action.equals(MusicPlaybackService.PLAYSTATE_CHANGED) ||
                    action.equals(MusicPlaybackService.META_CHANGED) ||
                    action.equals(MusicPlaybackService.SIMPLE_PLAYSTATE_STOPPED)
                    ) {
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
            if (action.equals(Constants.ACTION_FILE_ADDED_OR_REMOVED)) {
                if (intent.hasExtra(Constants.EXTRA_REFRESH_FILE_TYPE)) {
                    reloadFiles(intent.getByteExtra(Constants.EXTRA_REFRESH_FILE_TYPE, Constants.FILE_TYPE_AUDIO));
                } else {
                    // reload everything
                    reloadFiles(Constants.FILE_TYPE_APPLICATIONS);
                    reloadFiles(Constants.FILE_TYPE_RINGTONES);
                    reloadFiles(Constants.FILE_TYPE_TORRENTS);
                    reloadFiles(Constants.FILE_TYPE_PICTURES);
                    reloadFiles(Constants.FILE_TYPE_VIDEOS);
                    reloadFiles(Constants.FILE_TYPE_AUDIO);
                }
            }
        }
    }

    private void refreshSelection() {
        if (adapter != null) {
            lastAdapterRefresh = SystemClock.elapsedRealtime();
            clickFileTypeTab(adapter.getFileType());
        }
    }
}
