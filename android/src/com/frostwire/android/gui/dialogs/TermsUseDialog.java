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

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.ClickAdapter;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author gubatron
 * @author aldenml
 */
public class TermsUseDialog extends AbstractDialog {

    public static final String TAG = "terms_use_dialog";
    private Button buttonNo;
    private Button buttonYes;

    public TermsUseDialog() {
        super(TAG, R.layout.dialog_default_scroll);
    }

    @Override
    public void onPause() {
        super.onPause();
        dismiss();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        super.onCancel(dialog);
        exit();
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
        dlg.setCanceledOnTouchOutside(true);
        dlg.setContentView(R.layout.dialog_default_scroll);

        Context ctx = getActivity();

        TextView defaultDialogTitle = findView(dlg, R.id.dialog_default_scroll_title);
        defaultDialogTitle.setText(R.string.tos_title);

        TextView defaultDialogText = findView(dlg, R.id.dialog_default_scroll_text);
        defaultDialogText.setText(readText(ctx));

        ButtonListener yesButtonListener = new ButtonListener(this, BUTTON_POSITIVE);
        ButtonListener noButtonListener = new ButtonListener(this, BUTTON_NEGATIVE);


        buttonYes = findView(dlg, R.id.dialog_default_scroll_button_yes);
        buttonYes.setText(R.string.tos_accept);
        buttonYes.setOnClickListener(yesButtonListener);

        buttonNo = findView(dlg, R.id.dialog_default_scroll_button_no);
        buttonNo.setText(R.string.tos_refuse);
        buttonNo.setOnClickListener(noButtonListener);
    }

    private static String readText(Context context) {
        InputStream in = context.getResources().openRawResource(R.raw.tos);
        try {
            return IOUtils.toString(in);
        } catch (IOException e) {
            throw new RuntimeException("Missing TOS resource", e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    private static void exit() {
        System.exit(1); // drastic action
    }

    private static final class ButtonListener extends ClickAdapter<TermsUseDialog> {
        private int which;

        public ButtonListener(TermsUseDialog owner, int which) {
            super(owner);
            this.which = which;
        }

        @Override
        public void onClick(TermsUseDialog owner, View v) {
            super.onClick(owner, v);
            if (this.which == BUTTON_POSITIVE) {
                ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_TOS_ACCEPTED, true);
                owner.performDialogClick(which);
            } else {
                exit();
            }
        }


    }
}
