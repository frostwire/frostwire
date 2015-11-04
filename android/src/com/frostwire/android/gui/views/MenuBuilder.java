/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2012, FrostWire(TM). All rights reserved.
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

package com.frostwire.android.gui.views;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;

/**
 * @author gubatron
 * @author aldenml
 */
public class MenuBuilder implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {

    private final MenuAdapter adapter;

    private AlertDialog dialog;

    public MenuBuilder(MenuAdapter adapter) {
        this.adapter = adapter;
    }

    public AlertDialog show() {
        createDialog().show();
        return dialog;
    }

    public void onClick(DialogInterface dialog, int which) {
        MenuAction item = adapter.getItem(which);
        item.onClick();
        cleanup();
    }

    public void onCancel(DialogInterface dialog) {
        cleanup();
    }

    private Dialog createDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(adapter.getContext());

        //builder.setTitle(adapter.getTitle());
        builder.setAdapter(adapter, this);
        builder.setInverseBackgroundForced(true);

        dialog = builder.create();
        dialog.setOnCancelListener(this);
        dialog.setCanceledOnTouchOutside(true);

        return dialog;
    }

    private void cleanup() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }
}
