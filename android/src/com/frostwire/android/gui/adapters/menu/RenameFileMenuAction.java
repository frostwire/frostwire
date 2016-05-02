/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.adapters.FileListAdapter;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.MenuAction;

import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class RenameFileMenuAction extends MenuAction {

    private final FileDescriptor fd;
    private final FileListAdapter adapter;
    EditText input;

    public RenameFileMenuAction(Context context, FileListAdapter adapter, FileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_rename, R.string.rename);

        this.adapter = adapter;
        this.fd = fd;
    }

    @Override
    protected void onClick(Context context) {
        showRenameFileDialog();
    }

    private void showRenameFileDialog() {

        final Dialog newRenameFileDialog = new Dialog(getContext(), R.style.DefaultDialogTheme);
        newRenameFileDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        newRenameFileDialog.setContentView(R.layout.dialog_default_input);

        TextView title = (TextView) newRenameFileDialog.findViewById(R.id.dialog_default_input_title);
        title.setText(R.string.rename);

        String filePath = fd.filePath;

        String name = FilenameUtils.getBaseName(filePath);
        final String ext = FilenameUtils.getExtension(filePath);

        input = (EditText) newRenameFileDialog.findViewById(R.id.dialog_default_input_text);
        input.setText(name);
        input.selectAll();

        Button noButton = (Button) newRenameFileDialog.findViewById(R.id.dialog_default_input_button_no);
        noButton.setText(R.string.cancel);
        Button yesButton = (Button) newRenameFileDialog.findViewById(R.id.dialog_default_input_button_yes);
        yesButton.setText(android.R.string.ok);

        noButton.setOnClickListener(new NegativeButtonOnClickListener(this, newRenameFileDialog));
        yesButton.setOnClickListener(new PositiveButtonOnClickListener(this, newRenameFileDialog));

        newRenameFileDialog.show();
    }

    private boolean isValidFileName(String newFileName) {
        final String[] reservedChars = {"|", "\\", "?", "*", "<", "\"", ":", ">"};
        for (String c : reservedChars) {
            if (newFileName.contains(c)) {
                return false;
            }
        }
        return true;
    }

    private void renameFile(String newFileName) {
        fd.filePath = Librarian.instance().renameFile(fd, newFileName);
        fd.title = FilenameUtils.getBaseName(newFileName);
    }

    private class NegativeButtonOnClickListener implements View.OnClickListener {
        final private Dialog newRenameFileDialog;
        final private RenameFileMenuAction renameFileMenuAction;

        public NegativeButtonOnClickListener(RenameFileMenuAction renameFileMenuAction, Dialog newRenameFileDialog) {
            this.newRenameFileDialog = newRenameFileDialog;
            this.renameFileMenuAction = renameFileMenuAction;
        }

        @Override
        public void onClick(View view) {
            newRenameFileDialog.dismiss();
        }
    }

    private class PositiveButtonOnClickListener implements View.OnClickListener {
        final private Dialog newRenameFileDialog;
        final private RenameFileMenuAction renameFileMenuAction;

        public PositiveButtonOnClickListener(RenameFileMenuAction renameFileMenuAction, Dialog newRenameFileDialog) {
            this.newRenameFileDialog = newRenameFileDialog;
            this.renameFileMenuAction = renameFileMenuAction;
        }

        @Override
        public void onClick(View view) {
            String newFileName = input.getText().toString();
            if (isValidFileName(newFileName)) {
                renameFile(newFileName);
                adapter.notifyDataSetChanged();
                newRenameFileDialog.dismiss();
            } else {
                UIUtils.showLongMessage(getContext(), R.string.invalid_filename);
            }
        }
    }
}