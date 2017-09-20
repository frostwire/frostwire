/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.adapters.menu;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.andrew.apollo.ui.fragments.PlaylistFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;

/**
 * Created by gubatron on 12/18/14.
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 *
 * @author gubatron
 * @author aldenml
 */
public class CreateNewPlaylistMenuAction extends MenuAction {

    private final long[] fileDescriptors;

    public CreateNewPlaylistMenuAction(Context context, long[] fileDescriptors) {
        super(context, R.drawable.contextmenu_icon_playlist_add_dark, R.string.new_empty_playlist);
        this.fileDescriptors = fileDescriptors;
    }

    @Override
    public void onClick(Context context) {
        CreateNewPlaylistDialog.newInstance(fileDescriptors).
                show(((Activity) getContext()).getFragmentManager());
    }

    public static class CreateNewPlaylistDialog extends AbstractDialog {

        private long[] fileDescriptors;

        private EditText playlistInput;

        public static CreateNewPlaylistDialog newInstance(long[] fileDescriptors) {
            CreateNewPlaylistDialog dlg = new CreateNewPlaylistDialog();

            Bundle args = new Bundle();
            args.putLongArray("file_descriptors", fileDescriptors);
            dlg.setArguments(args);

            return dlg;
        }

        public CreateNewPlaylistDialog() {
            super(R.layout.dialog_default_input);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            Bundle args = getArguments();
            this.fileDescriptors = args.getLongArray("file_descriptors");

            TextView title = findView(dlg, R.id.dialog_default_input_title);
            title.setText(R.string.new_empty_playlist);

            playlistInput = findView(dlg, R.id.dialog_default_input_text);

            if (savedInstanceState != null && savedInstanceState.getString("playlistName") != null) {
                playlistInput.setText(savedInstanceState.getString("playlistName"));
            } else {
                playlistInput.setText("");
            }
            playlistInput.setHint(R.string.create_playlist_prompt);
            playlistInput.selectAll();

            Button yesButton = findView(dlg, R.id.dialog_default_input_button_yes);
            yesButton.setText(android.R.string.ok);
            Button noButton = findView(dlg, R.id.dialog_default_input_button_no);
            noButton.setText(R.string.cancel);

            yesButton.setOnClickListener(new DialogButtonClickListener(true));
            noButton.setOnClickListener(new DialogButtonClickListener(false));
        }

        String getPlaylistName() {
            return playlistInput.getText().toString();
        }

        void updatePlaylistName(String playlistName) {
            playlistInput.setText(playlistName);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putString("playlistName", getPlaylistName());
            super.onSaveInstanceState(outState);
        }

        private void onClickCreatePlaylistButton(CharSequence text) {
            Context ctx = getActivity();

            long playlistId = MusicUtils.createPlaylist(ctx, text.toString());
            MusicUtils.refresh();

            if (fileDescriptors != null) {
                MusicUtils.addToPlaylist(ctx, fileDescriptors, playlistId);
            }

            if (ctx instanceof AbstractActivity) {
                PlaylistFragment f = ((AbstractActivity) ctx).findFragment(PlaylistFragment.class);
                if (f != null) {
                    f.restartLoader(true);
                    f.refresh();
                }
            }
        }

        private final class DialogButtonClickListener implements View.OnClickListener {

            private final boolean positive;

            DialogButtonClickListener(boolean positive) {
                this.positive = positive;
            }

            @Override
            public void onClick(View view) {
                if (!positive) {
                    dismiss();
                } else {
                    String playlistName = getPlaylistName();
                    if (MusicUtils.getIdForPlaylist(getActivity(), playlistName) != -1) {
                        playlistName += "+";
                        updatePlaylistName(playlistName);
                    } else {
                        onClickCreatePlaylistButton(playlistName);
                        dismiss();
                    }
                }
            }
        }
    }
}
