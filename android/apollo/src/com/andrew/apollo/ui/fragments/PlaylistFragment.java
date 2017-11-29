/*
 * Copyright (C) 2012 Andrew Neal
 * Modified by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2013-2017, FrostWire(R). All rights reserved.
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

package com.andrew.apollo.ui.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.Loader;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.TextView;

import com.andrew.apollo.Config;
import com.andrew.apollo.adapters.PlaylistAdapter;
import com.andrew.apollo.loaders.PlaylistLoader;
import com.andrew.apollo.menu.FragmentMenuItems;
import com.andrew.apollo.menu.RenamePlaylist;
import com.andrew.apollo.model.Playlist;
import com.andrew.apollo.ui.activities.ProfileActivity;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.PreferenceUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog;

import java.util.List;

import static com.andrew.apollo.loaders.PlaylistLoader.FAVORITE_PLAYLIST_ID;
import static com.andrew.apollo.loaders.PlaylistLoader.LAST_ADDED_PLAYLIST_ID;
import static com.andrew.apollo.loaders.PlaylistLoader.NEW_PLAYLIST_ID;

/**
 * This class is used to display all of the playlists on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 * @author Marcelina Knitter (@marcelinkaaa)
 */
public class PlaylistFragment extends ApolloFragment<PlaylistAdapter, Playlist> {

    public PlaylistFragment() {
        super(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, Fragments.PLAYLIST_FRAGMENT_LOADER_ID);
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v,
                                    final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        // Get the position of the selected item
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        final int mPosition = info.position;

        menu.clear();
        mItem = mAdapter.getItem(mPosition);

        // Play the playlist
        menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE, R.string.context_menu_play_selection)
                .setIcon(R.drawable.contextmenu_icon_play);

        // Add the playlist to the queue
        menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE, R.string.add_to_queue)
                .setIcon(R.drawable.contextmenu_icon_queue_add);

        // Delete and rename (user made playlists)
        long pId = mItem.mPlaylistId;
        if (pId != NEW_PLAYLIST_ID && pId != FAVORITE_PLAYLIST_ID && pId != LAST_ADDED_PLAYLIST_ID) {
            menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.RENAME_PLAYLIST, Menu.NONE, R.string.context_menu_rename_playlist).setIcon(R.drawable.contextmenu_icon_rename);
            menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE, R.string.context_menu_delete).setIcon(R.drawable.contextmenu_icon_trash);
        }
    }

    @Override
    public boolean onContextItemSelected(final android.view.MenuItem item) {
        if (item.getGroupId() == Fragments.PLAYLIST_FRAGMENT_GROUP_ID) {
            final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
            switch (item.getItemId()) {
                case FragmentMenuItems.PLAY_SELECTION:
                    if (info.position == 0) {
                        MusicUtils.playFavorites(getActivity());
                    } else if (info.position == 1) {
                        MusicUtils.playLastAdded(getActivity());
                    } else {
                        MusicUtils.playPlaylist(getActivity(), mItem.mPlaylistId);
                    }
                    return true;
                case FragmentMenuItems.ADD_TO_QUEUE:
                    long[] list;
                    if (info.position == 0) {
                        list = MusicUtils.getSongListForFavorites(getActivity());
                    } else if (info.position == 1) {
                        list = MusicUtils.getSongListForLastAdded(getActivity());
                    } else {
                        list = MusicUtils.getSongListForPlaylist(getActivity(), mItem.mPlaylistId);
                    }
                    MusicUtils.addToQueue(getActivity(), list);
                    return true;
                case FragmentMenuItems.RENAME_PLAYLIST:
                    RenamePlaylist.getInstance(mItem.mPlaylistId).show(getFragmentManager(), "RenameDialog");
                    return true;
                case FragmentMenuItems.DELETE:
                    PlaylistFragmentDeleteDialog playlistFragmentDeleteDialog =
                            PlaylistFragmentDeleteDialog.newInstance(mItem.mPlaylistName, mItem.mPlaylistId);
                    playlistFragmentDeleteDialog.show(getActivity().getFragmentManager());
                    return true;
                default:
                    break;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected PlaylistAdapter createAdapter() {
        return new PlaylistAdapter(getActivity(), R.layout.list_item_simple);
    }

    @Override
    protected String getLayoutTypeName() {
        return PreferenceUtils.SIMPLE_LAYOUT;
    }

    @Override
    public void onItemClick(final AdapterView<?> parent, final View view, final int position,
                            final long id) {
        final Bundle bundle = new Bundle();
        mItem = mAdapter.getItem(position);
        String playlistName = "<no name>";
        // New Playlist list
        if (position == 0) {
            playlistName = getString(R.string.new_empty_playlist);
            bundle.putString(Config.MIME_TYPE, getString(R.string.new_empty_playlist));
            // Favorites list
        } else if (position == 1) {
            playlistName = getString(R.string.playlist_favorites);
            bundle.putString(Config.MIME_TYPE, getString(R.string.playlist_favorites));
            // Last Added list
        } else if (position == 2) {
            playlistName = getString(R.string.playlist_last_added);
            bundle.putString(Config.MIME_TYPE, getString(R.string.playlist_last_added));
        } else {
            // User created
            if (mItem != null) {
                playlistName = mItem.mPlaylistName;
                bundle.putString(Config.MIME_TYPE, MediaStore.Audio.Playlists.CONTENT_TYPE);
                bundle.putLong(Config.ID, mItem.mPlaylistId);
            }
        }

        bundle.putString(Config.NAME, playlistName);

        // Create the intent to launch the profile activity
        final Intent intent = new Intent(getActivity(), ProfileActivity.class);
        intent.putExtras(bundle);
        startActivity(intent);
    }

    @Override
    public Loader<List<Playlist>> onCreateLoader(final int id, final Bundle args) {
        return new PlaylistLoader(getActivity());
    }

    @Override
    protected boolean isSimpleLayout() {
        return true;
    }

    private void deleteSelectedPlaylist(long playlistId) {
        Activity activity = getActivity();
        if (activity != null && activity.getContentResolver() != null) {
            int deleted = MusicUtils.deletePlaylist(activity, playlistId);
            if (deleted > 0) {
                MusicUtils.refresh();
                restartLoader(true);
                refresh();
            }
        }
    }

    public static class PlaylistFragmentDeleteDialog extends AbstractDialog {
        private String playlistName;
        private long playlistId;

        public static PlaylistFragmentDeleteDialog newInstance(String playlistName, long playlistId) {
            PlaylistFragmentDeleteDialog f = new PlaylistFragmentDeleteDialog();

            Bundle args = new Bundle();
            args.putString("playlist_name", playlistName);
            args.putLong("playlist_id", playlistId);
            f.setArguments(args);

            return f;
        }

        public PlaylistFragmentDeleteDialog() {
            super(R.layout.dialog_default);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            Bundle args = getArguments();
            this.playlistName = args.getString("playlist_name");
            this.playlistId = args.getLong("playlist_id");

            if (playlistName != null && savedInstanceState != null) {
                savedInstanceState.putString("playlistName", playlistName);
                savedInstanceState.putLong("playlistId", playlistId);
            } else if (savedInstanceState != null && savedInstanceState.getString("playlistName") != null) {
                playlistName = savedInstanceState.getString("playlistName");
                playlistId = savedInstanceState.getLong("playlistId");
            }
            TextView dialogTitle = findView(dlg, R.id.dialog_default_title);
            dialogTitle.setText(getString(R.string.delete_dialog_title, playlistName));
            TextView text = findView(dlg, R.id.dialog_default_text);
            text.setText(getString(R.string.are_you_sure_delete_playlist, playlistName));
            Button buttonYes = findView(dlg, R.id.dialog_default_button_yes);
            buttonYes.setText(R.string.delete);
            buttonYes.setOnClickListener(new ButtonOnClickListener(this, true));
            Button btnNegative = findView(dlg, R.id.dialog_default_button_no);
            btnNegative.setText(R.string.cancel);
            btnNegative.setOnClickListener(new ButtonOnClickListener(this, false));
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            if (playlistName != null) {
                outState.putString("playlistName", playlistName);
                outState.putLong("playlistId", playlistId);
            }
            super.onSaveInstanceState(outState);
        }

        void onDelete() {
            dismiss();
            AbstractActivity act = (AbstractActivity) getActivity();
            PlaylistFragment f = act.findFragment(PlaylistFragment.class);
            if (f != null) {
                f.deleteSelectedPlaylist(playlistId);
            }
        }
    }

    private static class ButtonOnClickListener implements View.OnClickListener {

        private final boolean delete;
        private final PlaylistFragmentDeleteDialog dialog;

        ButtonOnClickListener(PlaylistFragmentDeleteDialog dlg, boolean delete) {
            this.delete = delete;
            this.dialog = dlg;
        }

        @Override
        public void onClick(View v) {
            if (!delete) {
                dialog.dismiss();
            } else {
                dialog.onDelete();
            }
        }
    }
}
