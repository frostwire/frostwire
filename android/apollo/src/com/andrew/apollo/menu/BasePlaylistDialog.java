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
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import com.frostwire.android.R;
import com.andrew.apollo.utils.MusicUtils;

/**
 * A simple base class for the playlist dialogs.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public abstract class BasePlaylistDialog extends DialogFragment {

    /* The actual dialog */
    protected AlertDialog mPlaylistDialog;

    /* Used to make new playlist names */
    protected EditText mPlaylist;

    /* The dialog save button */
    protected Button mSaveButton;

    /* The dialog prompt */
    protected String mPrompt;

    /* The default edit text text */
    protected String mDefaultname;

    /**
     * {@inheritDoc}
     */
    @Override
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        // Initialize the alert dialog
        mPlaylistDialog = new AlertDialog.Builder(getActivity()).create();
        // Initialize the edit text
        mPlaylist = new EditText(getActivity());
        // To show the "done" button on the soft keyboard
        mPlaylist.setSingleLine(true);
        // All caps
        mPlaylist.setInputType(mPlaylist.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        // Set the save button action
        mPlaylistDialog.setButton(Dialog.BUTTON_POSITIVE, getString(R.string.save),
                new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        onSaveClick();
                        MusicUtils.refresh();
                        dialog.dismiss();
                    }
                });
        // Set the cancel button action
        mPlaylistDialog.setButton(Dialog.BUTTON_NEGATIVE, getString(R.string.cancel),
                new OnClickListener() {

                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        closeKeyboard();
                        MusicUtils.refresh();
                        dialog.dismiss();
                    }
                });

        mPlaylist.post(new Runnable() {

            @Override
            public void run() {
                // Open up the soft keyboard
                openKeyboard();
                // Request focus to the edit text
                mPlaylist.requestFocus();
                // Select the playlist name
                mPlaylist.selectAll();
            };
        });

        initObjects(savedInstanceState);
        mPlaylistDialog.setTitle(mPrompt);
        mPlaylistDialog.setView(mPlaylist);
        mPlaylist.setText(mDefaultname);
        mPlaylist.setSelection(mDefaultname.length());
        mPlaylist.addTextChangedListener(mTextWatcher);
        mPlaylistDialog.show();
        return mPlaylistDialog;
    }

    /**
     * Opens the soft keyboard
     */
    protected void openKeyboard() {
        final InputMethodManager mInputMethodManager = (InputMethodManager)getActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        mInputMethodManager.toggleSoftInputFromWindow(mPlaylist.getApplicationWindowToken(),
                InputMethodManager.SHOW_FORCED, 0);
    }

    /**
     * Closes the soft keyboard
     */
    protected void closeKeyboard() {
        final InputMethodManager mInputMethodManager = (InputMethodManager)getActivity()
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        mInputMethodManager.hideSoftInputFromWindow(mPlaylist.getWindowToken(), 0);
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

    /**
     * Called in our {@link TextWatcher} during a text change
     */
    public abstract void onTextChangedListener();

}
