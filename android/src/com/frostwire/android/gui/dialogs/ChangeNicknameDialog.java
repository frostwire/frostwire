/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.EditText;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.ClickAdapter;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class ChangeNicknameDialog extends AbstractDialog {

    public static final String TAG = "change_nickname_dialog";

    private EditText editNickname;

    public ChangeNicknameDialog() {
        super(TAG, 0);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Context ctx = getActivity();

        ButtonListener ok = new ButtonListener(this);

        editNickname = new EditText(ctx);
        editNickname.setSingleLine();
        editNickname.setText(ConfigurationManager.instance().getNickname());

        return new AlertDialog.Builder(ctx).setTitle(R.string.change_my_nickname).setIcon(R.drawable.app_icon).setView(editNickname).setPositiveButton(android.R.string.ok, ok).create();
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
    }

    private static final class ButtonListener extends ClickAdapter<ChangeNicknameDialog> {

        public ButtonListener(ChangeNicknameDialog dlg) {
            super(dlg);
        }

        @Override
        public void onClick(ChangeNicknameDialog dlg, DialogInterface dialog, int which) {
            String newNickname = dlg.editNickname.getText().toString().trim();

            if (newNickname.length() > 0) {
                ConfigurationManager.instance().setNickname(newNickname);
                dlg.performDialogClick(which);
            }
        }
    }
}