/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.AsyncTask;

import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.adapters.FileListAdapter;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author gubatron
 * @author aldenml
 *
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
        UIUtils.showYesNoDialog(context, R.string.are_you_sure_delete_files, R.string.application_label, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                deleteFiles();
            }
        });
    }

    public void deleteFiles() {
        int size = files.size();
        for (int i = 0; i < size; i++) {
            FileDescriptor fd = files.get(i);
            adapter.deleteItem(fd);
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Librarian.instance().deleteFiles(adapter.getFileType(), new ArrayList<FileDescriptor>(files), getContext());
                return null;
            }
        }.execute();
    }
}
