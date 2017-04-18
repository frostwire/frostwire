/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.fragments;

import android.app.Activity;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.AsyncTaskLoader;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.res.Resources;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.SearchView;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Filter;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.Peer;
import com.frostwire.android.gui.adapters.menu.AddToPlaylistMenuAction;
import com.frostwire.android.gui.adapters.menu.CopyToClipboardMenuAction;
import com.frostwire.android.gui.adapters.menu.DeleteFileMenuAction;
import com.frostwire.android.gui.adapters.menu.FileListAdapter;
import com.frostwire.android.gui.adapters.menu.OpenMenuAction;
import com.frostwire.android.gui.adapters.menu.RenameFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.android.gui.adapters.menu.SendFileMenuAction;
import com.frostwire.android.gui.adapters.menu.SetAsRingtoneMenuAction;
import com.frostwire.android.gui.adapters.menu.SetAsWallpaperMenuAction;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.android.gui.views.FileTypeRadioButtonSelectorFactory;
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
public class BrowsePeerFragment extends AbstractFragment implements LoaderCallbacks<Object>, MainFragment {

    private static final Logger LOG = Logger.getLogger(BrowsePeerFragment.class);
    private static final int LOADER_FILES_ID = 0;

    private final BroadcastReceiver broadcastReceiver;

    private SwipeRefreshLayout swipeRefresh;
    private ListView list;
    private FileListAdapter adapter;
    private MenuItem checkBoxMenuItem;
    private RelativeLayout selectAllCheckboxContainer;
    private CheckBox selectAllCheckbox;
    private RadioGroup browseTypeRadioGroup;
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

    private final SparseArray<Byte> toTheRightOf = new SparseArray<>(6);
    private final SparseArray<Byte> toTheLeftOf = new SparseArray<>(6);
    private final SparseArray<RadioButton> radioButtonFileTypeMap;

    /**
     * This implements the toolbar's action mode view and its menu
     */
    private final MyFilesActionModeCallback selectionModeCallback;

    public BrowsePeerFragment() {
        super(R.layout.fragment_browse_peer);
        broadcastReceiver = new LocalBroadcastReceiver();
        setHasOptionsMenu(true);
        this.peer = new Peer();
        toTheRightOf.put(Constants.FILE_TYPE_AUDIO, Constants.FILE_TYPE_RINGTONES);   //0x00 - Audio -> Ringtones
        toTheRightOf.put(Constants.FILE_TYPE_PICTURES, Constants.FILE_TYPE_DOCUMENTS); //0x01 - Pictures -> Documents
        toTheRightOf.put(Constants.FILE_TYPE_VIDEOS, Constants.FILE_TYPE_PICTURES);    //0x02 - Videos -> Pictures
        toTheRightOf.put(Constants.FILE_TYPE_DOCUMENTS, Constants.FILE_TYPE_TORRENTS); //0x03 - Documents -> Torrents
        toTheRightOf.put(Constants.FILE_TYPE_RINGTONES, Constants.FILE_TYPE_VIDEOS);   //0x05 - Ringtones -> Videos
        toTheRightOf.put(Constants.FILE_TYPE_TORRENTS, Constants.FILE_TYPE_AUDIO);     //0x06 - Torrents -> Audio
        toTheLeftOf.put(Constants.FILE_TYPE_AUDIO, Constants.FILE_TYPE_TORRENTS);     //0x00 - Audio <- Torrents
        toTheLeftOf.put(Constants.FILE_TYPE_PICTURES, Constants.FILE_TYPE_VIDEOS);    //0x01 - Pictures <- Video
        toTheLeftOf.put(Constants.FILE_TYPE_VIDEOS, Constants.FILE_TYPE_RINGTONES);   //0x02 - Videos <- Ringtones
        toTheLeftOf.put(Constants.FILE_TYPE_DOCUMENTS, Constants.FILE_TYPE_PICTURES); //0x03 - Documents <- Pictures
        toTheLeftOf.put(Constants.FILE_TYPE_RINGTONES, Constants.FILE_TYPE_AUDIO);    //0x05 - Ringtones <- Audio
        toTheLeftOf.put(Constants.FILE_TYPE_TORRENTS, Constants.FILE_TYPE_DOCUMENTS); //0x06 - Torrents <- Documents
        checkedItemsMap = new SparseArray<>();
        radioButtonFileTypeMap = new SparseArray<>();  // see initRadioButton(...)
        selectionModeCallback = new MyFilesActionModeCallback();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setRetainInstance(true);
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
        inflater.inflate(R.menu.fragment_browse_peer_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
        initToolbarSearchFilter(menu);
        initToolbarCheckbox(menu);
    }

    private void initToolbarCheckbox(Menu menu) {
        checkBoxMenuItem = menu.findItem(R.id.fragment_browse_peer_menu_checkbox);
        checkBoxMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                onToolbarMenuSelectModeCheckboxClick();
                return true;
            }
        });
        selectAllCheckbox.setOnCheckedChangeListener(selectAllCheckboxListener);
    }

    private void initToolbarSearchFilter(Menu menu) {
        final SearchView searchView = (SearchView) menu.findItem(R.id.fragment_browse_peer_menu_filter).getActionView();
        searchView.setQueryHint("Filter...");
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
                return true;
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.fragment_browse_peer_menu_filter:
                return true;
            case R.id.fragment_browse_peer_menu_checkbox:
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
            browseFilesButtonClick(adapter.getFileType());
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
        header = inflater.inflate(R.layout.view_browse_peer_header, null, false);
        updateHeader();
        return header;
    }

    @Override
    public void onShow() {
    }

    @Override
    protected void initComponents(View v) {
        findView(v, R.id.fragment_browse_peer_select_all_container).setVisibility(View.GONE);
        findView(v, R.id.progressContainer).setVisibility(View.GONE);

        selectAllCheckboxListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onSelectAllChecked(isChecked);
            }
        };

        selectAllCheckbox = findView(v, R.id.fragment_browse_peer_select_all_checkbox);
        selectAllCheckboxContainer = findView(v, R.id.fragment_browse_peer_select_all_container);
        swipeRefresh = findView(v, R.id.fragment_browse_peer_swipe_refresh);
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
        list = findView(v, R.id.fragment_browse_peer_list);
        SwipeLayout swipe = findView(v, R.id.fragment_browse_peer_swipe);
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

        browseTypeRadioGroup = findView(v, R.id.fragment_browse_peer_radiogroup_browse_type);
        initRadioButton(v, R.id.fragment_browse_peer_radio_audio, Constants.FILE_TYPE_AUDIO);
        initRadioButton(v, R.id.fragment_browse_peer_radio_ringtones, Constants.FILE_TYPE_RINGTONES);
        initRadioButton(v, R.id.fragment_browse_peer_radio_videos, Constants.FILE_TYPE_VIDEOS);
        initRadioButton(v, R.id.fragment_browse_peer_radio_pictures, Constants.FILE_TYPE_PICTURES);
        initRadioButton(v, R.id.fragment_browse_peer_radio_documents, Constants.FILE_TYPE_DOCUMENTS);
        initRadioButton(v, R.id.fragment_browse_peer_radio_torrents, Constants.FILE_TYPE_TORRENTS);
    }

    private RadioButton initRadioButton(View v, int viewId, final byte fileType) {
        RadioButton button = findView(v, viewId);
        button.setButtonDrawable(new StateListDrawable());
        Resources r = button.getResources();
        FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory =
                new FileTypeRadioButtonSelectorFactory(fileType,
                        r,
                        FileTypeRadioButtonSelectorFactory.RadioButtonContainerType.BROWSE);
        fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
        button.setClickable(true);
        RadioButtonListener rbListener = new RadioButtonListener(button, fileType, fileTypeRadioButtonSelectorFactory);
        button.setOnClickListener(rbListener);
        button.setOnCheckedChangeListener(rbListener);
        button.setChecked(fileType == Constants.FILE_TYPE_AUDIO);
        radioButtonFileTypeMap.put(fileType, button);
        return button;
    }

    private void browseFilesButtonClick(byte fileType) {
        if (adapter != null) {
            saveListViewVisiblePosition(adapter.getFileType());
            adapter.clear();
            adapter.clearChecked();
        }
        reloadFiles(fileType);
        if (checkBoxMenuItem != null) {
            checkBoxMenuItem.setVisible(fileType != Constants.FILE_TYPE_RINGTONES);
        }
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
        getLoaderManager().destroyLoader(LOADER_FILES_ID);
        Bundle bundle = new Bundle();
        bundle.putByte("fileType", fileType);
        getLoaderManager().restartLoader(LOADER_FILES_ID, bundle, this);
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
        loader.forceLoad();
        return loader;
    }

    private void updateHeader() {
        if (peer == null) {
            LOG.warn("Something wrong. peer is null");
            return;
        }
        Librarian.instance().invalidateCountCache();
        Finger finger = peer.finger();
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
            String fileTypeStr = getString(R.string.my_filetype, UIUtils.getFileTypeAsString(getResources(), fileType));
            TextView title = (TextView) header.findViewById(R.id.view_browse_peer_header_text_title);
            TextView total = (TextView) header.findViewById(R.id.view_browse_peer_header_text_total);
            title.setText(fileTypeStr);
            total.setText("(" + String.valueOf(numTotal) + ")");
        }
        if (adapter == null) {
            browseFilesButtonClick(Constants.FILE_TYPE_AUDIO);
        }
        MusicUtils.stopSimplePlayer();
        restoreListViewScrollPosition();
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
            @SuppressWarnings("unchecked")
            List<FileDescriptor> items = (List<FileDescriptor>) data[1];
            adapter = new FileListAdapter(getActivity(), items, fileType) {
                @Override
                protected void onLocalPlay() {
                    if (adapter != null) {
                        saveListViewVisiblePosition(adapter.getFileType());
                    }
                }

                @Override
                protected void onItemChecked(CompoundButton v, boolean isChecked) {
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
        adapter.setCheckboxesVisibility(selectAllModeOn);
        adapter.setShowMenuOnClick(!selectAll);
        selectAllCheckbox.setChecked(autoCheckAll);
        swipeRefresh.setEnabled(!selectAll);
        if (selectAllModeOn) {
            startActionMode(selectionModeCallback);
        } else {
            adapter.clearChecked();
        }
        browseTypeRadioGroup.setVisibility(!selectAllModeOn ? View.VISIBLE : View.GONE);
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
        ConfigurationManager.instance().setInt(Constants.BROWSE_PEER_FRAGMENT_LISTVIEW_FIRST_VISIBLE_POSITION + fileType, firstVisiblePosition);
    }

    private int getSavedListViewVisiblePosition(byte fileType) {
        //will return 0 if not found.
        return ConfigurationManager.instance().getInt(Constants.BROWSE_PEER_FRAGMENT_LISTVIEW_FIRST_VISIBLE_POSITION + fileType);
    }

    private RadioButton getRadioButton(byte fileType) {
        return radioButtonFileTypeMap.get(fileType);
    }

    private void switchToThe(boolean right) {
        if (adapter == null) {
            return;
        }
        final byte currentFileType = adapter.getFileType();
        final byte nextFileType = (right) ? toTheRightOf.get(currentFileType) : toTheLeftOf.get(currentFileType);
        changeSelectedRadioButton(currentFileType, nextFileType);
    }

    private void changeSelectedRadioButton(byte currentFileType, byte nextFileType) {
        // browseFilesButtonClick(currentFileType) isn't enough, it won't update the radio button background.
        RadioButton currentButton = getRadioButton(currentFileType);
        RadioButton nextButton = getRadioButton(nextFileType);
        if (nextButton != null) {
            currentButton.setChecked(false);
            nextButton.setChecked(true);
            nextButton.callOnClick();
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
            mode.getMenuInflater().inflate(R.menu.fragment_browse_peer_action_mode_menu, menu);
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
                case R.id.fragment_browse_peer_action_mode_menu_delete:
                    new DeleteFileMenuAction(context, adapter, fileDescriptors).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_seed:
                    new SeedAction(context, fd, null).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_open:
                    new OpenMenuAction(context, fd.filePath, fd.mime, fd.fileType).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_use_as_ringtone:
                    new SetAsRingtoneMenuAction(context, fd).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_use_as_wallpaper:
                    new SetAsWallpaperMenuAction(context, fd).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_copy_magnet:
                    new CopyToClipboardMenuAction(context,
                            R.drawable.contextmenu_icon_magnet,
                            R.string.transfers_context_menu_copy_magnet,
                            R.string.transfers_context_menu_copy_magnet_copied,
                            FileListAdapter.readInfoFromTorrent(fd.filePath, true)
                    ).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_copy_info_hash:
                    new CopyToClipboardMenuAction(context,
                            R.drawable.contextmenu_icon_copy,
                            R.string.transfers_context_menu_copy_infohash,
                            R.string.transfers_context_menu_copy_infohash_copied,
                            FileListAdapter.readInfoFromTorrent(fd.filePath, false)
                    ).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_rename:
                    new RenameFileMenuAction(context, adapter, fd).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_add_to_playlist:
                    new AddToPlaylistMenuAction(context, fileDescriptors).onClick();
                    break;
                case R.id.fragment_browse_peer_action_mode_menu_share:
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
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_seed);
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_open);
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_use_as_ringtone);
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_use_as_wallpaper);
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_rename);
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_copy_magnet);
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_copy_info_hash);
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_share);
            } else {
                if (numChecked == 1) {
                    if (!canOpenFile) {
                        actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_open);
                    }
                    if (fd.fileType != Constants.FILE_TYPE_AUDIO) {
                        actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_use_as_ringtone);
                    }
                    if (fd.fileType != Constants.FILE_TYPE_PICTURES) {
                        actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_use_as_wallpaper);
                    }
                    if (fd.fileType == Constants.FILE_TYPE_APPLICATIONS) {
                        actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_rename);
                        actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_share);
                        actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_delete);
                    }
                    if (fd.mime != null && !fd.mime.equals(Constants.MIME_TYPE_BITTORRENT)) {
                        actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_copy_magnet);
                        actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_copy_info_hash);
                    }
                }
            }
            if (fd.filePath != null && AndroidPlatform.saf(new File(fd.filePath))) {
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_seed);
            }
            if (fd.fileType != Constants.FILE_TYPE_AUDIO) {
                actionsToHide.add(R.id.fragment_browse_peer_action_mode_menu_add_to_playlist);
            }
            if (menu != null && menu.size() > 0) {
                for (int i = 0; i < menu.size(); i++) {
                    MenuItem item = menu.getItem(i);
                    item.setVisible(!actionsToHide.contains(item.getItemId()));
                }
            }
        }
    }

    private final class RadioButtonListener implements OnClickListener, CompoundButton.OnCheckedChangeListener {
        private final RadioButton button;
        private final byte fileType;
        private final FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory;

        RadioButtonListener(RadioButton button,
                            byte fileType,
                            FileTypeRadioButtonSelectorFactory fileTypeRadioButtonSelectorFactory) {
            this.button = button;
            this.fileType = fileType;
            this.fileTypeRadioButtonSelectorFactory = fileTypeRadioButtonSelectorFactory;
        }

        @Override
        public void onClick(View v) {
            if (button.isChecked()) {
                browseFilesButtonClick(fileType);
            }
            fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
        }

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            fileTypeRadioButtonSelectorFactory.updateButtonBackground(button);
        }
    }


    private final class LocalBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
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
            browseFilesButtonClick(adapter.getFileType());
        }
    }
}
