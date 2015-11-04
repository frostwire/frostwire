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

package com.andrew.apollo.menu;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import android.view.View;
import android.widget.ArrayAdapter;
import com.andrew.apollo.Config;
import com.andrew.apollo.model.Song;
import com.frostwire.android.R;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;

/**
 * Alert dialog used to delete tracks.
 * <p>
 * TODO: Remove albums from the recents list upon deletion.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class DeleteDialog extends DialogFragment {

    public interface DeleteDialogCallback {
        public void onDelete(long[] id);
    }

    /**
     * The item(s) to delete
     */
    private long[] mItemList;

    /**
     * The image cache
     */
    private ImageFetcher mFetcher;

    private DeleteDialogCallback onDeleteCallback;

    /**
     * Empty constructor as per the {@link Fragment} documentation
     * @param song
     * @param songAdapter
     */
    public DeleteDialog() {
    }

    /**
     * @param title The title of the artist, album, or song to delete
     * @param items The item(s) to delete
     * @param key The key used to remove items from the cache.
     * @return A new instance of the dialog
     */
    public static DeleteDialog newInstance(final String title, final long[] items, final String key) {
        final DeleteDialog frag = new DeleteDialog();
        final Bundle args = new Bundle();
        args.putString(Config.NAME, title);
        args.putLongArray("items", items);
        args.putString("cachekey", key);
        frag.setArguments(args);
        return frag;
    }

    public DeleteDialog setOnDeleteCallback(DeleteDialogCallback callback) {
        onDeleteCallback = callback;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        final String delete = getString(R.string.context_menu_delete);
        final Bundle arguments = getArguments();
        // Get the image cache key
        final String key = arguments.getString("cachekey");
        // Get the track(s) to delete
        mItemList = arguments.getLongArray("items");
        // Get the dialog title
        final String title = arguments.getString(Config.NAME);
        final String dialogTitle = getString(R.string.delete_dialog_title, title);
        // Initialize the image cache
        mFetcher = ApolloUtils.getImageFetcher(getActivity());
        // Build the dialog
        return new AlertDialog.Builder(getActivity()).setTitle(dialogTitle)
                .setMessage(R.string.cannot_be_undone)
                .setPositiveButton(delete, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        // Remove the items from the image cache
                        mFetcher.removeFromCache(key);
                        // Delete the selected item(s)
                        MusicUtils.deleteTracks(getActivity(), mItemList);

                        if (getActivity() instanceof DeleteDialogCallback) {
                            ((DeleteDialogCallback) getActivity()).onDelete(mItemList);
                        }

                        if (onDeleteCallback != null) {
                            onDeleteCallback.onDelete(mItemList);
                        }

                        dialog.dismiss();
                    }
                }).setNegativeButton(R.string.cancel, new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        dialog.dismiss();
                    }
                }).create();
    }
}
