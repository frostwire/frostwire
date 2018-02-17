/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.services.Engine;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;
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
    public void onClick(Context context) {
        showDeleteFilesDialog();
    }

    private void showDeleteFilesDialog() {
        DeleteFileMenuActionDialog.newInstance(this, onDialogClickListener).show(((Activity) getContext()).getFragmentManager());
    }

    private void deleteFiles() {
        Engine.instance().getThreadPool().execute(new DeleteFilesRunnable(getContext(), adapter, files));
    }

    private static class DeleteFilesRunnable implements Runnable {

        private final WeakReference<Context> contextRef;
        private final WeakReference<FileListAdapter> adapterRef;
        private final List<FileDescriptor> files;

        DeleteFilesRunnable(Context context, FileListAdapter adapter, List<FileDescriptor> files) {
            contextRef = Ref.weak(context);
            this.adapterRef = Ref.weak(adapter);
            this.files = files;
        }

        @Override
        public void run() {
            if (!Ref.alive(contextRef) || !Ref.alive(adapterRef)) {
                return;
            }
            FileListAdapter fileListAdapter = adapterRef.get();
            byte fileType = (fileListAdapter != null) ? fileListAdapter.getFileType() : files.get(0).fileType;
            Librarian.instance().deleteFiles(contextRef.get(), fileType, new ArrayList<>(files));

            int size = files.size();
            if (fileListAdapter != null) {
                for (int i = 0; i < size; i++) {
                    FileDescriptor fd = files.get(i);
                    fileListAdapter.deleteItem(fd); // only notifies if in main thread
                }
            }

            // we make just one notify call at the end
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                if (Ref.alive(adapterRef)) {
                    adapterRef.get().notifyDataSetChanged();
                }
            });
        }
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
            TextView title = dlg.findViewById(R.id.dialog_default_title);
            title.setText(R.string.delete_files);

            TextView text = dlg.findViewById(R.id.dialog_default_text);
            text.setText(R.string.are_you_sure_delete_files);

            Button noButton = dlg.findViewById(R.id.dialog_default_button_no);
            noButton.setText(R.string.cancel);
            Button yesButton = dlg.findViewById(R.id.dialog_default_button_yes);
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
