/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
import com.andrew.apollo.utils.MusicUtils;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.logging.Logger;

/**
 * Created by gubatron on 12/18/14.
 * Adapter in control of the List View shown when we're browsing the files of
 * one peer.
 *
 * @author gubatron
 * @author aldenml
 */
public class CreateNewPlaylistMenuAction extends MenuAction {
    private static Logger LOG = Logger.getLogger(CreateNewPlaylistMenuAction.class);
    private final long[] fileDescriptors;

    public CreateNewPlaylistMenuAction(Context context, long[] fileDescriptors) {
        super(context, R.drawable.contextmenu_icon_playlist_add_dark, R.string.new_empty_playlist);
        this.fileDescriptors = fileDescriptors;
    }

    @Override
    protected void onClick(Context context) {
        showCreateNewPlaylistDialog();
    }

    private void showCreateNewPlaylistDialog() {
        CreateNewPlaylistDialog.newInstance(this).
                show(((Activity) getContext()).getFragmentManager());
    }

    private void onClickCreatePlaylistButton(CharSequence text) {
        long playlistId = MusicUtils.createPlaylist(getContext(), text.toString());
        MusicUtils.refresh();

        if (fileDescriptors != null) {
            MusicUtils.addToPlaylist(getContext(), fileDescriptors, playlistId);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public static class CreateNewPlaylistDialog extends AbstractDialog {
        private Dialog dlg;
        private static CreateNewPlaylistMenuAction menuAction;

        public static CreateNewPlaylistDialog newInstance(CreateNewPlaylistMenuAction action) {
            menuAction = action;
            return new CreateNewPlaylistDialog();
        }

        public CreateNewPlaylistDialog() {
            super();
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            this.dlg = dlg;
            TextView title = findView(dlg, R.id.dialog_default_input_title);
            title.setText(R.string.new_empty_playlist);

            EditText playlistInput = findView(dlg, R.id.dialog_default_input_text);

            if (savedInstanceState != null && savedInstanceState.getString("playlistName")!=null) {
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

            yesButton.setOnClickListener(new DialogButtonClickListener(this, true));
            noButton.setOnClickListener(new DialogButtonClickListener(this, false));
        }

        String getPlaylistName() {
            EditText playlistInput = findView(dlg, R.id.dialog_default_input_text);
            return playlistInput.getText().toString();
        }

        void updatePlaylistName(String playlistName) {
            EditText playlistInput = findView(dlg, R.id.dialog_default_input_text);
            playlistInput.setText(playlistName);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            outState.putString("playlistName", getPlaylistName());
            super.onSaveInstanceState(outState);
        }
    }

    private static class DialogButtonClickListener implements View.OnClickListener {
        private final CreateNewPlaylistDialog dialog;
        private final boolean positive;

        DialogButtonClickListener(CreateNewPlaylistDialog dialog, boolean positive) {
            this.dialog = dialog;
            this.positive = positive;
        }

        @Override
        public void onClick(View view) {
            if (!positive) {
                dialog.dismiss();
            } else {
                String playlistName = dialog.getPlaylistName();
                if (MusicUtils.getIdForPlaylist(dialog.getActivity(), playlistName) != -1) {
                    playlistName += "+";
                    dialog.updatePlaylistName(playlistName);
                } else {
                    CreateNewPlaylistDialog.menuAction.onClickCreatePlaylistButton(playlistName);
                    dialog.dismiss();
                }
            }
        }
    }
}