/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(TM). All rights reserved.
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

import android.app.Dialog;
import android.content.Context;
import android.os.AsyncTask;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.adapters.FileListAdapter;
import com.frostwire.android.gui.views.MenuAction;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class DeleteFileMenuAction extends MenuAction {

    private final FileListAdapter adapter;
    private final List<FileDescriptor> files;

    public DeleteFileMenuAction(Context context, FileListAdapter adapter, List<FileDescriptor> files) {
        super(context, R.drawable.contextmenu_icon_trash, files.size() > 1 ? R.string.delete_file_menu_action_count : R.string.delete_file_menu_action, files.size());
        this.adapter = adapter;
        this.files = files;
    }

    @Override
    protected void onClick(Context context) {
        showDeleteFilesDialog();
    }

    private void showDeleteFilesDialog() {

        final Dialog newDeleteFilesDialog = new Dialog(getContext(), R.style.DefaultDialogTheme);

        newDeleteFilesDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        newDeleteFilesDialog.setContentView(R.layout.dialog_default);

        TextView title = (TextView) newDeleteFilesDialog.findViewById(R.id.dialog_default_title);
        title.setText(R.string.delete_files);

        TextView text = (TextView) newDeleteFilesDialog.findViewById(R.id.dialog_default_text);
        text.setText(R.string.are_you_sure_delete_files);

        Button buttonNo = (Button) newDeleteFilesDialog.findViewById(R.id.dialog_default_button_no);
        buttonNo.setText(R.string.cancel);
        buttonNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newDeleteFilesDialog.dismiss();
            }
        });

        Button buttonYes = (Button) newDeleteFilesDialog.findViewById(R.id.dialog_default_button_yes);
        buttonYes.setText(android.R.string.ok);
        buttonYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteFiles();
                newDeleteFilesDialog.dismiss();
            }
        });

        newDeleteFilesDialog.show();
    }


    private void deleteFiles() {
        int size = files.size();
        for (int i = 0; i < size; i++) {
            FileDescriptor fd = files.get(i);
            adapter.deleteItem(fd);
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Librarian.instance().deleteFiles(adapter.getFileType(), new ArrayList<>(files), getContext());
                return null;
            }
        }.execute();
    }
}