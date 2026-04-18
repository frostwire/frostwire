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

package com.andrew.apollo.menu;

import androidx.fragment.app.DialogFragment;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.andrew.apollo.Config;
import com.andrew.apollo.cache.ImageFetcher;
import com.andrew.apollo.provider.FavoritesStore;
import com.andrew.apollo.provider.RecentStore;
import com.andrew.apollo.utils.ApolloUtils;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.util.SystemUtils;

/**
 * Alert dialog used to delete tracks.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public final class DeleteDialog extends DialogFragment {

    public interface DeleteDialogCallback {
        void onDelete(long[] id);
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

    public DeleteDialog() {
    }

    /**
     * @param title The title of the artist, album, or song to delete
     * @param items The item(s) to delete
     * @param key   The key used to remove items from the cache.
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

    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {

        final Bundle arguments = getArguments();
        // Get the track(s) to delete
        mItemList = arguments.getLongArray("items");
        //   Initialize the image cache
        mFetcher = ApolloUtils.getImageFetcher(getActivity());

        final AlertDialog.Builder apolloDeleteFilesDialog = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        final View inflater2 = inflater.inflate(R.layout.dialog_default, null);
        apolloDeleteFilesDialog.setView(inflater2);

        final TextView dialogTitle = inflater2.findViewById(R.id.dialog_default_title);
        dialogTitle.setText(R.string.delete_files_title);

        TextView text = inflater2.findViewById(R.id.dialog_default_text);
        text.setText(R.string.are_you_sure_delete_files_text);

        Button noButton = inflater2.findViewById(R.id.dialog_default_button_no);
        noButton.setText(R.string.cancel);

        Button yesButton = inflater2.findViewById(R.id.dialog_default_button_yes);
        yesButton.setText(R.string.delete);

        noButton.setOnClickListener(new NegativeButtonOnClickListener());
        yesButton.setOnClickListener(new PositiveButtonOnClickListener());

        return apolloDeleteFilesDialog.create();
    }

    private class NegativeButtonOnClickListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            dismiss();
        }
    }

    private class PositiveButtonOnClickListener implements View.OnClickListener {

        private final Bundle arguments;
        private final String key;

        PositiveButtonOnClickListener() {
            arguments = getArguments();
            key = arguments.getString("cachekey");
        }

        @Override
        public void onClick(View view) {
            // Remove the items from the image cache
            if (mFetcher != null) {
                mFetcher.removeFromCache(key);
            }
            // Clean RecentStore and FavoritesStore synchronously before async deleteTracks
            final Activity activity = getActivity();
            if (activity != null) {
                RecentStore recentStore = RecentStore.getInstance(activity);
                FavoritesStore favoritesStore = FavoritesStore.getInstance(activity);
                for (long id : mItemList) {
                    if (recentStore != null) {
                        recentStore.removeItem(id);
                    }
                    if (favoritesStore != null) {
                        favoritesStore.removeItem(id);
                    }
                }
            }
            // Delete the selected item(s)
            final Activity activityRef = activity;
            final long[] deletedIds = mItemList;
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
                MusicUtils.deleteTracks(activityRef != null ? activityRef.getApplicationContext() : null, deletedIds);
                SystemUtils.postToUIThread(() -> {
                    if (activityRef instanceof DeleteDialogCallback) {
                        ((DeleteDialogCallback) activityRef).onDelete(deletedIds);
                    }

                    if (onDeleteCallback != null) {
                        onDeleteCallback.onDelete(deletedIds);
                    }
                });
            });

            dismiss();
        }
    }
}
