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

package com.andrew.apollo.ui.fragments;

import static com.andrew.apollo.loaders.PlaylistLoader.FAVORITE_PLAYLIST_ID;
import static com.andrew.apollo.loaders.PlaylistLoader.LAST_ADDED_PLAYLIST_ID;
import static com.andrew.apollo.loaders.PlaylistLoader.NEW_PLAYLIST_ID;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.loader.content.Loader;

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
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.util.Logger;

import java.util.Arrays;
import java.util.List;

/**
 * This class is used to display all of the playlists on a user's device.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 * @author Angel Leon (@gubatron)
 * @author Alden Torres (@aldenml)
 * @author Marcelina Knitter (@marcelinkaaa)
 */
public class PlaylistFragment extends ApolloFragment<PlaylistAdapter, Playlist> {

    private boolean playlistLoaderFinished;

    public PlaylistFragment() {
        super(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, Fragments.PLAYLIST_FRAGMENT_LOADER_ID);
    }

    @Override
    public void onCreateContextMenu(final ContextMenu menu, final View v, final ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        // Get the position of the selected item
        final AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
        final int mPosition = info.position;

        menu.clear();
        mItem = mAdapter.getItem(mPosition);

        // Initialize the tint color
        int tintColor = UIUtils.getAppIconPrimaryColor(getActivity());

        // Play the playlist
        MenuItem playSelectionItem = menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.PLAY_SELECTION, Menu.NONE, R.string.context_menu_play_selection);
        Drawable playIcon = ContextCompat.getDrawable(getActivity(), R.drawable.contextmenu_icon_play);
        if (playIcon != null) {
            playIcon = DrawableCompat.wrap(playIcon);
            DrawableCompat.setTint(playIcon, tintColor);
            playSelectionItem.setIcon(playIcon);
        }

        // Add the playlist to the queue
        MenuItem addToQueueItem = menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.ADD_TO_QUEUE, Menu.NONE, R.string.add_to_queue);
        Drawable queueIcon = ContextCompat.getDrawable(getActivity(), R.drawable.contextmenu_icon_queue_add);
        if (queueIcon != null) {
            queueIcon = DrawableCompat.wrap(queueIcon);
            DrawableCompat.setTint(queueIcon, tintColor);
            addToQueueItem.setIcon(queueIcon);
        }

        // Delete and rename (user made playlists)
        long pId = mItem.mPlaylistId;
        if (pId != NEW_PLAYLIST_ID && pId != FAVORITE_PLAYLIST_ID && pId != LAST_ADDED_PLAYLIST_ID) {
            // Rename playlist
            MenuItem renameItem = menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.RENAME_PLAYLIST, Menu.NONE, R.string.context_menu_rename_playlist);
            Drawable renameIcon = ContextCompat.getDrawable(getActivity(), R.drawable.contextmenu_icon_rename);
            if (renameIcon != null) {
                renameIcon = DrawableCompat.wrap(renameIcon);
                DrawableCompat.setTint(renameIcon, tintColor);
                renameItem.setIcon(renameIcon);
            }

            // Delete playlist
            MenuItem deleteItem = menu.add(Fragments.PLAYLIST_FRAGMENT_GROUP_ID, FragmentMenuItems.DELETE, Menu.NONE, R.string.context_menu_delete);
            Drawable deleteIcon = ContextCompat.getDrawable(getActivity(), R.drawable.contextmenu_icon_trash);
            if (deleteIcon != null) {
                deleteIcon = DrawableCompat.wrap(deleteIcon);
                DrawableCompat.setTint(deleteIcon, tintColor);
                deleteItem.setIcon(deleteIcon);
            }
        }
    }

    private static final Logger LOG = Logger.getLogger(PlaylistFragment.class);

    @Override
    public void onResume() {
        super.onResume();
        if (isAdded()) {
            MusicUtils.refresh();
        }

        if (isAdded()) {
            MusicUtils.refresh();
            restartLoader(false);
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
                    PlaylistFragmentDeleteDialog playlistFragmentDeleteDialog = PlaylistFragmentDeleteDialog.newInstance(mItem.mPlaylistName, mItem.mPlaylistId);
                    playlistFragmentDeleteDialog.show(((AppCompatActivity) getActivity()).getSupportFragmentManager());
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
    public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
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

    @NonNull
    @Override
    public Loader<List<Playlist>> onCreateLoader(final int id, final Bundle args) {
        return new PlaylistLoader(getActivity());
    }

    @Override
    protected boolean isSimpleLayout() {
        return true;
    }

    private void deleteSelectedPlaylist(long playlistId) {
        AppCompatActivity activity = (AppCompatActivity) getActivity();
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
            AbstractActivity activity = (AbstractActivity) getActivity();
            PlaylistFragment f = activity.findFragment(PlaylistFragment.class);
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
