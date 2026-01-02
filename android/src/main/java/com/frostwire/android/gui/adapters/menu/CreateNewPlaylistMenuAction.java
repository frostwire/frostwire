/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.adapters.menu;

import static com.frostwire.android.util.SystemUtils.HandlerThreadName.MISC;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.andrew.apollo.ui.fragments.PlaylistFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractActivity;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
import java.util.Objects;

/**
 * Created by gubatron on 12/18/14.
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 *
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class CreateNewPlaylistMenuAction extends MenuAction {

    private final long[] fileDescriptors;

    public CreateNewPlaylistMenuAction(Context context, long[] fileDescriptors) {
        super(context, R.drawable.contextmenu_icon_playlist_add_dark, R.string.new_empty_playlist, UIUtils.getAppIconPrimaryColor(context));
        this.fileDescriptors = fileDescriptors;
    }

    @Override
    public void onClick(Context context) {
        CreateNewPlaylistDialog.newInstance(fileDescriptors).
                show(getFragmentManager());
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
            final Context ctx = getActivity();

            SystemUtils.postToHandler(MISC, () -> {
                long playlistId = MusicUtils.createPlaylist(ctx, text.toString());
                MusicUtils.refresh();

                if (fileDescriptors != null) {
                    MusicUtils.addToPlaylist(ctx, fileDescriptors, playlistId);
                }

                SystemUtils.postToUIThread(() -> {
                    if (ctx instanceof AbstractActivity) {
                        PlaylistFragment f = ((AbstractActivity) ctx).findFragment(PlaylistFragment.class);
                        if (f != null) {
                            f.restartLoader(true);
                            f.refresh();
                        }
                    }
                });
            });
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
                    final String playlistName = getPlaylistName();
                    WeakReference<FragmentActivity> fragmentActivityWeakRef = Ref.weak(requireActivity());
                    SystemUtils.postToHandler(MISC, () -> {
                        if (!Ref.alive(fragmentActivityWeakRef)) {
                            return;
                        }
                        FragmentActivity fragmentActivity = fragmentActivityWeakRef.get();
                        final long playlistId = MusicUtils.getIdForPlaylist(fragmentActivity, playlistName);

                        SystemUtils.postToUIThread(() -> {
                            if (playlistId != -1) {
                                updatePlaylistName(playlistName + "+");
                            } else {
                                onClickCreatePlaylistButton(playlistName);
                                dismiss();
                            }
                        });
                    });
                }
            }
        }
    }
}
