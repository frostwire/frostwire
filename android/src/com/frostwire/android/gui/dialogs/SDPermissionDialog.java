/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.ClickAdapter;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SDPermissionDialog extends AbstractDialog {

    public static final String TAG = getSuggestedTAG(SDPermissionDialog.class);

    public SDPermissionDialog() {
        super(R.layout.dialog_default_checkbox);
    }

    public static boolean visible;

    public static SDPermissionDialog newInstance() {
        return new SDPermissionDialog();
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {

        TextView dialogTitle = findView(dlg, R.id.dialog_default_checkbox_title);
        dialogTitle.setText(R.string.confirm_download);

        TextView textQuestion = findView(dlg, R.id.dialog_default_checkbox_text);
        textQuestion.setText(R.string.sd_permission_no_accessible_path);

        DialogListener yes = new DialogListener(this, true);

        Button buttonYes = findView(dlg, R.id.dialog_default_checkbox_button_yes);
        buttonYes.setText(android.R.string.yes);
        buttonYes.setOnClickListener(yes);

        View buttonNo = findView(dlg, R.id.dialog_default_checkbox_button_no);
        buttonNo.setVisibility(View.GONE);

        View checkShow = findView(dlg, R.id.dialog_default_checkbox_show);
        checkShow.setVisibility(View.GONE);

        visible = true;
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        visible = false;
        super.onDismiss(dialog);
    }

    private static final class DialogListener extends ClickAdapter<SDPermissionDialog> {

        private final boolean positive;

        DialogListener(SDPermissionDialog owner, boolean positive) {
            super(owner);
            this.positive = positive;
        }

        @Override
        public void onClick(SDPermissionDialog owner, View v) {
            owner.performDialogClick(positive ? Dialog.BUTTON_POSITIVE : Dialog.BUTTON_NEGATIVE);
            owner.dismiss();
            visible = false;
        }
    }
}
