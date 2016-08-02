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
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.util.Logger;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * A simple base class for the playlist dialogs.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
abstract class BasePlaylistDialog extends DialogFragment {

    @SuppressWarnings("unused")
    private static final Logger LOGGER = Logger.getLogger(BasePlaylistDialog.class);

    /* The actual dialog */
    private AlertDialog mPlaylistDialog;

    /* Used to make new playlist names */
    EditText mPlaylist;

    /* The dialog save button */
    private Button mSaveButton;

    /* The dialog prompt */
    String mPrompt;

    /* The default edit text text */
    String mDefaultname;

    private WeakReference<ApolloFragment> apolloFragmentRef;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mPlaylistDialog = new AlertDialog.Builder(getActivity()).create();
        LayoutInflater inflater = LayoutInflater.from(getContext());
        View view = inflater.inflate(R.layout.dialog_default_input, null);
        mPlaylistDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        mPlaylistDialog.setView(view);

        mPrompt = getString(R.string.create_playlist_prompt);

        TextView dialogTitle = (TextView) view.findViewById(R.id.dialog_default_input_title);
        dialogTitle.setText(mPrompt);

        // Initialize the edit text
        mPlaylist = (EditText) view.findViewById(R.id.dialog_default_input_text);
        // To show the "done" button on the soft keyboard
        mPlaylist.setSingleLine(true);
        // All caps
        mPlaylist.setInputType(mPlaylist.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);

        // Set the save button action
        Button noButton = (Button) view.findViewById(R.id.dialog_default_input_button_no);
        noButton.setText(R.string.cancel);
        Button yesButton = (Button) view.findViewById(R.id.dialog_default_input_button_yes);
        yesButton.setText(R.string.save);

        noButton.setOnClickListener(new NegativeButtonOnClickListener(this, mPlaylistDialog));
        yesButton.setOnClickListener(new PositiveButtonOnClickListener(this, mPlaylistDialog, apolloFragmentRef));

        mPlaylist.post(new Runnable() {

            @Override
            public void run() {
                // Open up the soft keyboard
                openKeyboard();
                // Request focus to the edit text
                mPlaylist.requestFocus();
                // Select the playlist name
                mPlaylist.selectAll();
            }
        });

        initObjects(savedInstanceState);
        mPlaylist.setText(mDefaultname);
        mPlaylist.setSelection(mDefaultname.length());
        mPlaylist.addTextChangedListener(mTextWatcher);
        mPlaylistDialog.show();
        return mPlaylistDialog;
    }

    private static class PositiveButtonOnClickListener implements View.OnClickListener {

        private final Dialog dialog;
        private final WeakReference<ApolloFragment> apolloFragmentRef;
        private final BasePlaylistDialog basePlaylistDialog;

        public PositiveButtonOnClickListener(BasePlaylistDialog basePlaylistDialog, Dialog dialog, WeakReference<ApolloFragment> apolloFragmentRef) {
            this.basePlaylistDialog = basePlaylistDialog;
            this.dialog = dialog;
            this.apolloFragmentRef = apolloFragmentRef;
        }

        @Override
        public void onClick(View v) {
            basePlaylistDialog.onSaveClick();
            MusicUtils.refresh();
            dialog.dismiss();
            if (Ref.alive(apolloFragmentRef)) {
                basePlaylistDialog.getApolloFragment().refresh();
            }
        }
    }

    private static class NegativeButtonOnClickListener implements View.OnClickListener {

        private final Dialog dialog;
        private final BasePlaylistDialog basePlaylistDialog;

        public NegativeButtonOnClickListener(BasePlaylistDialog basePlaylistDialog, Dialog dialog) {
            this.basePlaylistDialog = basePlaylistDialog;
            this.dialog = dialog;
        }

        @Override
        public void onClick(View v) {
            basePlaylistDialog.closeKeyboard();
            MusicUtils.refresh();
            dialog.dismiss();
        }
    }

    /**
     * Opens the soft keyboard
     */
    private void openKeyboard() {
        final InputMethodManager mInputMethodManager = (InputMethodManager)getActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        mInputMethodManager.toggleSoftInputFromWindow(mPlaylist.getApplicationWindowToken(),
                InputMethodManager.SHOW_FORCED, 0);
    }

    /**
     * Closes the soft keyboard
     */
    void closeKeyboard() {
        final InputMethodManager mInputMethodManager = (InputMethodManager)getActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        mInputMethodManager.hideSoftInputFromWindow(mPlaylist.getWindowToken(), 0);
    }

    private ApolloFragment getApolloFragment() {
        if (Ref.alive(apolloFragmentRef)) {
            return apolloFragmentRef.get();
        }
        return null;
    }

    void updateApolloFragmentReference(ApolloFragment frag) {
        if (apolloFragmentRef == null) {
            if (frag != null) {
                apolloFragmentRef = Ref.weak(frag);
            }
        } else {
            Ref.free(apolloFragmentRef);

            if (frag != null) {
                apolloFragmentRef = Ref.weak(frag);
            } else {
                apolloFragmentRef = null;
            }
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
            if (MusicUtils.getIdForPlaylist(getActivity(), playlistName) >= 0) {
                mSaveButton.setText(R.string.overwrite);
            } else {
                mSaveButton.setText(R.string.save);
            }
        }
    }

    /**
     * Simple {@link TextWatcher}
     */
    private final TextWatcher mTextWatcher = new TextWatcher() {

        /**
         * {@inheritDoc}
         */
        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before,
                final int count) {
            onTextChangedListener();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void afterTextChanged(final Editable s) {
            /* Nothing to do */
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count,
                final int after) {
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
