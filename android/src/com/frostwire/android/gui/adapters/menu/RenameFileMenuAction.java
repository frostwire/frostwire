/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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

import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.Librarian;
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

    private final AbstractDialog.OnDialogClickListener dialogClickListener;

    public RenameFileMenuAction(Context context, FWFileDescriptor fd) {
        this(context, fd, null);
    }

    public RenameFileMenuAction(Context context, FWFileDescriptor fd, AbstractDialog.OnDialogClickListener clickListener) {
        super(context, R.drawable.contextmenu_icon_rename, R.string.rename, UIUtils.getAppIconPrimaryColor(context));
        this.fd = fd;
        this.dialogClickListener = clickListener;
    }

    @Override
    public void onClick(Context context) {
        showRenameFileDialog();
    }

    private void showRenameFileDialog() {
        RenameFileMenuActionDialog.renameAction = this;
        RenameFileMenuActionDialog.newInstance(getFilePath(), dialogClickListener)
                .show(getFragmentManager());
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
