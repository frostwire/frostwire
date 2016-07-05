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

package com.frostwire.android.gui.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractDialog;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public class YesNoDialog extends AbstractDialog {
    public static final byte FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK = 0x1;
    private static final String ID_KEY = "id";
    private static final String TITLE_KEY = "title";
    private static final String MESSAGE_KEY = "message";
    private static final String YES_NO_DIALOG_FLAGS = "yesnodialog_flags";
    private String id;

    public YesNoDialog() {
        super(R.layout.dialog_default);
    }

    public static YesNoDialog newInstance(String id, int titleId, int messageId) {
        return newInstance(id, titleId, messageId, (byte) 0x0);
    }

    public static YesNoDialog newInstance(String id, int titleId, int messageId, byte dialogFlags) {
        YesNoDialog f = new YesNoDialog();

        Bundle args = new Bundle();
        args.putString(ID_KEY, id);
        args.putInt(TITLE_KEY, titleId);
        args.putInt(MESSAGE_KEY, messageId);
        args.putByte(YES_NO_DIALOG_FLAGS, dialogFlags);
        f.setArguments(args);

        return f;
    }

    @Override
    protected void initComponents(final Dialog dlg, Bundle savedInstanceState) {
        Bundle args = getArguments();

        id = args.getString(ID_KEY);

        int titleId = args.getInt(TITLE_KEY);
        int messageId = args.getInt(MESSAGE_KEY);
        final byte flags = args.getByte(YES_NO_DIALOG_FLAGS);

        dlg.setContentView(R.layout.dialog_default);

        TextView defaultDialogTitle = findView(dlg, R.id.dialog_default_title);
        defaultDialogTitle.setText(titleId);

        TextView defaultDialogText = findView(dlg, R.id.dialog_default_text);
        defaultDialogText.setText(messageId);

        Button buttonYes = findView(dlg, R.id.dialog_default_button_yes);
        buttonYes.setText(android.R.string.yes);
        buttonYes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((flags & FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK) == FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK) {
                    dlg.dismiss();
                }
                performDialogClick(id, Dialog.BUTTON_POSITIVE);
            }
        });

        Button buttonNo = findView(dlg, R.id.dialog_default_button_no);
        buttonNo.setText(android.R.string.no);
        buttonNo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dlg.dismiss();
            }
        });
    }

    @Override
    protected void performDialogClick(String tag, int which) {
        super.performDialogClick(id, which);
    }
}
