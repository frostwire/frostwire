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

import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.ClickAdapter;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class YesNoDialog extends AbstractDialog {

    private static final String TAG = "yesno_dialog";

    private static final String ID_KEY = "id";
    private static final String TITLE_KEY = "title";
    private static final String MESSAGE_KEY = "message";

    private String id;

    public YesNoDialog() {
        super(TAG, 0);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();

        id = args.getString(ID_KEY);

        int titleId = args.getInt(TITLE_KEY);
        int messageId = args.getInt(MESSAGE_KEY);

        Context ctx = getActivity();

        ButtonListener bListener = new ButtonListener(this);

        return new AlertDialog.Builder(ctx).setMessage(messageId).setTitle(titleId).setPositiveButton(android.R.string.yes, bListener).setNegativeButton(android.R.string.no, bListener).create();
    }

    public static YesNoDialog newInstance(String id, int titleId, int messageId) {
        YesNoDialog f = new YesNoDialog();

        Bundle args = new Bundle();
        args.putString(ID_KEY, id);
        args.putInt(TITLE_KEY, titleId);
        args.putInt(MESSAGE_KEY, messageId);
        f.setArguments(args);

        return f;
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
    }

    @Override
    protected void performDialogClick(String tag, int which) {
        super.performDialogClick(id, which);
    }

    private static final class ButtonListener extends ClickAdapter<YesNoDialog> {

        public ButtonListener(YesNoDialog owner) {
            super(owner);
        }

        @Override
        public void onClick(YesNoDialog owner, DialogInterface dialog, int which) {
            owner.performDialogClick(which);
        }
    }
}