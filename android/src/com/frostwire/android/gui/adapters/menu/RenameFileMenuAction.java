/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
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

import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.adapters.FileListAdapter;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;

import org.apache.commons.io.FilenameUtils;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class RenameFileMenuAction extends MenuAction {
    private final FWFileDescriptor fd;
    private final FileListAdapter adapter;
    private final AbstractDialog.OnDialogClickListener dialogClickListener;

    public RenameFileMenuAction(Context context, FileListAdapter adapter, FWFileDescriptor fd) {
        this(context, adapter, fd, null);
    }

    public RenameFileMenuAction(Context context, FileListAdapter adapter, FWFileDescriptor fd, AbstractDialog.OnDialogClickListener clickListener) {
        super(context, R.drawable.contextmenu_icon_rename, R.string.rename);
        this.adapter = adapter;
        this.fd = fd;
        this.dialogClickListener = clickListener;
    }

    @Override
    public void onClick(Context context) {
        showRenameFileDialog();
    }

    private void showRenameFileDialog() {
        RenameFileMenuActionDialog.renameAction = this;
        RenameFileMenuActionDialog.newInstance(getFilePath(), dialogClickListener).show(((Activity) getContext()).getFragmentManager());
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
        Context context = getContext();
        if (context == null) {
            return;
        }
        if (isValidFileName(newFileName)) {
            fd.filePath = Librarian.instance().renameFile(context, fd, newFileName);
            fd.title = FilenameUtils.getBaseName(newFileName);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        } else {
            UIUtils.showLongMessage(context, R.string.invalid_filename);
        }
    }

    public String getFilePath() {
        return fd.filePath;
    }

    @SuppressWarnings("WeakerAccess")
    public static class RenameFileMenuActionDialog extends AbstractDialog {
        private static RenameFileMenuAction renameAction;
        private String filePath;

        public static RenameFileMenuActionDialog newInstance(String filePath, AbstractDialog.OnDialogClickListener onDialogClickListener) {
            RenameFileMenuActionDialog dlg = new RenameFileMenuActionDialog();
            if (onDialogClickListener != null) {
                dlg.setOnDialogClickListener(onDialogClickListener);
            }
            dlg.filePath = filePath; // bad design having non empty constructors
            // no time to refactor now
            return dlg;
        }

        public RenameFileMenuActionDialog() {
            super(R.layout.dialog_default_input);
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            if (filePath != null) {
                outState.putString("filePath", filePath);
            }
            super.onSaveInstanceState(outState);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            if (savedInstanceState == null && filePath != null) {
                savedInstanceState = new Bundle();
                savedInstanceState.putString("filePath", filePath);
            } else if (savedInstanceState != null && filePath == null) {
                filePath = savedInstanceState.getString("filePath");
            }
            TextView title = findView(dlg, R.id.dialog_default_input_title);
            title.setText(R.string.rename);
            String name = FilenameUtils.getBaseName(filePath);
            EditText input = findView(dlg, R.id.dialog_default_input_text);
            input.setText(name);
            input.selectAll();
            Button yesButton = findView(dlg, R.id.dialog_default_input_button_yes);
            yesButton.setText(android.R.string.ok);
            Button noButton = findView(dlg, R.id.dialog_default_input_button_no);
            noButton.setText(R.string.cancel);
            yesButton.setOnClickListener(new ButtonOnClickListener(this, true));
            noButton.setOnClickListener(new ButtonOnClickListener(this, false));
        }

        public String rename() {
            EditText input = findView(getDialog(), R.id.dialog_default_input_text);
            String newFileName = input.getText().toString();
            if (RenameFileMenuActionDialog.renameAction != null) {
                RenameFileMenuActionDialog.renameAction.renameFile(newFileName);
            }
            dismiss();
            return newFileName;
        }
    }

    private static class ButtonOnClickListener implements View.OnClickListener {
        final private RenameFileMenuActionDialog newRenameFileDialog;
        final private boolean rename;

        ButtonOnClickListener(RenameFileMenuActionDialog newRenameFileDialog,
                              boolean rename) {
            this.newRenameFileDialog = newRenameFileDialog;
            this.rename = rename;
        }

        @Override
        public void onClick(View view) {
            String newFileName = null;
            if (!rename) {
                newRenameFileDialog.dismiss();
            } else {
                newFileName = newRenameFileDialog.rename();
            }
            if (newRenameFileDialog.getOnDialogClickListener() != null) {
                newRenameFileDialog.getOnDialogClickListener().onDialogClick(newFileName, rename ? 1 : 0);
            }
        }
    }
}
