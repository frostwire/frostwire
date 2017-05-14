/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;

import java.util.ArrayList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class DeleteFileMenuAction extends MenuAction {

    private final FileListAdapter adapter;
    private final List<FileDescriptor> files;
    private final AbstractDialog.OnDialogClickListener onDialogClickListener;

    public DeleteFileMenuAction(Context context, FileListAdapter adapter, List<FileDescriptor> files) {
        this(context, adapter, files, null);
    }

    public DeleteFileMenuAction(Context context, FileListAdapter adapter, List<FileDescriptor> files, AbstractDialog.OnDialogClickListener clickListener) {
        super(context, R.drawable.contextmenu_icon_trash, files.size() > 1 ? R.string.delete_file_menu_action_count : R.string.delete_file_menu_action, files.size());
        this.adapter = adapter;
        this.files = files;
        this.onDialogClickListener = clickListener;
    }

    public AbstractDialog.OnDialogClickListener getOnDialogClickListener() {
        return onDialogClickListener;
    }

    @Override
    protected void onClick(Context context) {
        showDeleteFilesDialog();
    }

    private void showDeleteFilesDialog() {
        DeleteFileMenuActionDialog.newInstance(this, onDialogClickListener).show(((Activity) getContext()).getFragmentManager());
    }

    private void deleteFiles() {
        int size = files.size();
        if (adapter != null) {
            for (int i = 0; i < size; i++) {
                FileDescriptor fd = files.get(i);
                adapter.deleteItem(fd);
            }
        }

        Engine.instance().getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                byte fileType = (adapter != null) ? adapter.getFileType() : files.get(0).fileType;
                Librarian.instance().deleteFiles(fileType, new ArrayList<>(files), getContext());
            }
        });
    }

    @SuppressWarnings("WeakerAccess")
    public static class DeleteFileMenuActionDialog extends AbstractDialog {
        private static DeleteFileMenuAction action;

        public static DeleteFileMenuActionDialog newInstance(DeleteFileMenuAction action, AbstractDialog.OnDialogClickListener onDialogClickListener) {
            DeleteFileMenuActionDialog.action = action;
            DeleteFileMenuActionDialog deleteFileMenuActionDialog = new DeleteFileMenuActionDialog();
            if (onDialogClickListener != null) {
                deleteFileMenuActionDialog.setOnDialogClickListener(onDialogClickListener);
            }
            return deleteFileMenuActionDialog;
        }

        public DeleteFileMenuActionDialog() {
            super(R.layout.dialog_default);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            TextView title = (TextView) dlg.findViewById(R.id.dialog_default_title);
            title.setText(R.string.delete_files);

            TextView text = (TextView) dlg.findViewById(R.id.dialog_default_text);
            text.setText(R.string.are_you_sure_delete_files);

            Button noButton = (Button) dlg.findViewById(R.id.dialog_default_button_no);
            noButton.setText(R.string.cancel);
            Button yesButton = (Button) dlg.findViewById(R.id.dialog_default_button_yes);
            yesButton.setText(R.string.delete);

            noButton.setOnClickListener(new ButtonOnClickListener(dlg, false));
            yesButton.setOnClickListener(new ButtonOnClickListener(dlg, true));
        }
    }

    private static final class ButtonOnClickListener implements View.OnClickListener {

        private final Dialog newDeleteFilesDialog;
        private final boolean delete;

        ButtonOnClickListener(Dialog newDeleteFilesDialog, boolean delete) {
            this.newDeleteFilesDialog = newDeleteFilesDialog;
            this.delete = delete;
        }

        @Override
        public void onClick(View view) {
            if (delete) {
                DeleteFileMenuActionDialog.action.deleteFiles();
            }
            newDeleteFilesDialog.dismiss();
            if (DeleteFileMenuActionDialog.action.getOnDialogClickListener() != null) {
                DeleteFileMenuActionDialog.action.getOnDialogClickListener().onDialogClick(null, delete ? 1 : 0);
            }
        }
    }
}
