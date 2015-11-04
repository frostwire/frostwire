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

import org.apache.commons.io.FilenameUtils;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.widget.EditText;

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
public class RenameFileMenuAction extends MenuAction {

    private final FileDescriptor fd;
    private final FileListAdapter adapter;

    public RenameFileMenuAction(Context context, FileListAdapter adapter, FileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_rename, R.string.rename);

        this.adapter = adapter;
        this.fd = fd;
    }

    @Override
    protected void onClick(Context context) {
        String filePath = fd.filePath;

        String name = FilenameUtils.getBaseName(filePath);
        final String ext = FilenameUtils.getExtension(filePath);

        final EditText input = new EditText(context);
        input.setText(name);
        input.selectAll();

        UIUtils.showOkCancelDialog(context, input, R.string.rename, new OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String newFileName = input.getText().toString() + "." + ext;
                if (isValidFileName(newFileName)) {
                    renameFile(newFileName);
                    adapter.notifyDataSetChanged();
                } else {
                    // FIXME
                }
            }
        });
    }

    private boolean isValidFileName(String newFileName) {
        // FIXME
        return true;
    }

    private void renameFile(String newFileName) {
        String newPath = Librarian.instance().renameFile(fd, newFileName);

        fd.filePath = newPath;
        fd.title = FilenameUtils.getBaseName(newFileName);
    }
}
