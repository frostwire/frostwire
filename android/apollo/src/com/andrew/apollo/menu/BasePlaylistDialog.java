/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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

import android.app.AlertDialog;
import android.app.Dialog;

import androidx.fragment.app.DialogFragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.andrew.apollo.ui.fragments.PlaylistFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Logger;

import java.util.concurrent.CountDownLatch;

/**
 * A simple base class for the playlist dialogs.
 *
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
abstract class BasePlaylistDialog extends DialogFragment {

    /* The actual dialog */
    private AlertDialog mPlaylistDialog;

    /* Used to make new playlist names */ EditText mPlaylist;

    /* The dialog save button */
    private Button mSaveButton;

    /* The dialog prompt */ String mPrompt;

    /* The default edit text text */ String mDefaultname;

    /* Cached activity reference to handle fragment detachment */
    private androidx.fragment.app.FragmentActivity mCachedActivity;

    private final static Logger LOG = Logger.getLogger(BasePlaylistDialog.class);

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mCachedActivity = getActivity();
        mPlaylistDialog = new AlertDialog.Builder(mCachedActivity).create();
        LayoutInflater inflater = LayoutInflater.from(mCachedActivity);
        View view = inflater.inflate(R.layout.dialog_default_input, null);
        mPlaylistDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mPlaylistDialog.setView(view);

        mPrompt = getString(R.string.create_playlist_prompt);

        TextView dialogTitle = view.findViewById(R.id.dialog_default_input_title);
        dialogTitle.setText(mPrompt);

        // Initialize the edit text
        mPlaylist = view.findViewById(R.id.dialog_default_input_text);
        // To show the "done" button on the soft keyboard
        mPlaylist.setSingleLine(true);
        // All caps
        mPlaylist.setInputType(mPlaylist.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        // Set the save button action
        Button noButton = view.findViewById(R.id.dialog_default_input_button_no);
        noButton.setText(R.string.cancel);
        Button yesButton = view.findViewById(R.id.dialog_default_input_button_yes);
        yesButton.setText(R.string.save);

        noButton.setOnClickListener(new NegativeButtonOnClickListener(mPlaylistDialog));
        yesButton.setOnClickListener(new PositiveButtonOnClickListener(this, mPlaylistDialog));

        mPlaylist.post(new Runnable() {

            @Override
            public void run() {
                // Request focus to the edit text
                mPlaylist.requestFocus();
                // Select the playlist name
                mPlaylist.selectAll();
            }
        });


        final CountDownLatch latch = new CountDownLatch(1);
        SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
            initObjects(savedInstanceState);
            mPlaylist.setText(mDefaultname);
            mPlaylist.setSelection(mDefaultname.length());
            mPlaylist.addTextChangedListener(mTextWatcher);
            mPlaylist.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    mPlaylistDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
                mPlaylistDialog.show();
            });
            latch.countDown();
        });

        try {
            LOG.info("BasePlaylistDialog.onCreateDialog() waiting for latch...");
            latch.await();
            LOG.info("BasePlaylistDialog.onCreateDialog() done waiting for latch...");
        } catch (InterruptedException e) {
            LOG.error("BasePlaylistDialog.onCreateDialog() interrupted", e);
        }
        return mPlaylistDialog;
    }

    private static class PositiveButtonOnClickListener implements View.OnClickListener {

        private final Dialog dialog;
        private final BasePlaylistDialog basePlaylistDialog;

        PositiveButtonOnClickListener(BasePlaylistDialog basePlaylistDialog, Dialog dialog) {
            this.basePlaylistDialog = basePlaylistDialog;
            this.dialog = dialog;
        }

        @Override
        public void onClick(View v) {
            basePlaylistDialog.onSaveClick();
            MusicUtils.refresh();
            dialog.dismiss();

            // refresh the PlaylistFragment
            AbstractActivity act = (AbstractActivity) basePlaylistDialog.getActivity();
            PlaylistFragment f = act.findFragment(PlaylistFragment.class);
            if (f != null) {
                f.refresh();
            }
        }
    }

    private static class NegativeButtonOnClickListener implements View.OnClickListener {

        private final Dialog dialog;

        NegativeButtonOnClickListener(Dialog dialog) {
            this.dialog = dialog;
        }

        @Override
        public void onClick(View v) {
            MusicUtils.refresh();
            dialog.dismiss();
        }
    }

    private void onTextChangedListener() {
        mSaveButton = mPlaylistDialog.getButton(Dialog.BUTTON_POSITIVE);
        if (mSaveButton == null) {
            return;
        }
        final String playlistName = mPlaylist.getText().toString();
        enableSaveButton(playlistName);
    }

    private void enableSaveButton(final String playlistName) {
        if (playlistName.trim().length() == 0) {
            mSaveButton.setEnabled(false);
        } else {
            mSaveButton.setEnabled(true);
            // Check if activity is still available (fragment not detached)
            if (mCachedActivity == null) {
                LOG.warn("enableSaveButton: Activity is null, fragment may have been detached");
                return;
            }
            SystemUtils.postToHandler(SystemUtils.HandlerThreadName.MISC, () -> {
                int saveButtonResourceTextId = MusicUtils.getIdForPlaylist(mCachedActivity, playlistName) >= 0 ?
                        R.string.overwrite :
                        R.string.save;
                SystemUtils.postToUIThread(() -> mSaveButton.setText(saveButtonResourceTextId));
            });
        }
    }

    /**
     * Simple {@link TextWatcher}
     */
    private final TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            onTextChangedListener();
        }

        @Override
        public void afterTextChanged(final Editable s) {
            /* Nothing to do */
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
            /* Nothing to do */
        }
    };

    /**
     * Initializes the prompt and default name
     */
    public abstract void initObjects(Bundle savedInstanceState);

    /**
     * Called when the save button of our {@link AlertDialog} is pressed
     */
    public abstract void onSaveClick();
}
