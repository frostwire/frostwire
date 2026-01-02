/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
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

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 4/6/18.
 */

public abstract class AbstractDeleteFilesMenuAction extends MenuAction {
    private final AbstractDialog.OnDialogClickListener onDialogClickListener;

    AbstractDeleteFilesMenuAction(final Context context, final int imageId, final int textId, AbstractDialog.OnDialogClickListener onDialogClickListener) {
        super(context, imageId, textId, UIUtils.getAppIconPrimaryColor(context));
        this.onDialogClickListener = onDialogClickListener;
    }

    protected abstract void onDeleteClicked();

    @Override
    public void onClick(Context context) {
        showDeleteFilesDialog();
    }

    private AbstractDialog.OnDialogClickListener getOnDialogClickListener() {
        return onDialogClickListener;
    }

    private void showDeleteFilesDialog() {
        DeleteFileMenuActionDialog.newInstance(this, onDialogClickListener)
                .show(getFragmentManager());
    }

    @SuppressWarnings("WeakerAccess")
    public static class DeleteFileMenuActionDialog extends AbstractDialog {
        private static AbstractDeleteFilesMenuAction action;

        public static DeleteFileMenuActionDialog newInstance(AbstractDeleteFilesMenuAction action, AbstractDialog.OnDialogClickListener onDialogClickListener) {
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
                DeleteFileMenuActionDialog.action.onDeleteClicked();
            }
            newDeleteFilesDialog.dismiss();
            if (DeleteFileMenuActionDialog.action.getOnDialogClickListener() != null) {
                DeleteFileMenuActionDialog.action.getOnDialogClickListener().onDialogClick(null, delete ? 1 : 0);
            }
        }
    }
}
