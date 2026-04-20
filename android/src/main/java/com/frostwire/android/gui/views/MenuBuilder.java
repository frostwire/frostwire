/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
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

package com.frostwire.android.gui.views;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Context;
import android.content.ContextWrapper;

import com.frostwire.util.Logger;

/**
 * @author gubatron
 * @author aldenml
 */
@SuppressWarnings("deprecation")
public class MenuBuilder implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    private static final Logger LOG = Logger.getLogger(MenuBuilder.class);

    private final MenuAdapter adapter;

    private AlertDialog dialog;

    public MenuBuilder(MenuAdapter adapter) {
        this.adapter = adapter;
    }

    public AlertDialog show() {
        createDialog();
        if (!canShowDialog(adapter.getContext())) {
            LOG.warn("Skipping menu dialog show because the host activity is no longer valid");
            return dialog;
        }
        dialog.show();
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

        //builder.setTitle(adapter.getTabTitle());
        builder.setAdapter(adapter, this);
        //noinspection deprecation — setInverseBackgroundForced has no replacement
        builder.setInverseBackgroundForced(true);

        dialog = builder.create();
        dialog.setOnCancelListener(this);
        dialog.setCanceledOnTouchOutside(true);

        return dialog;
    }

    private static boolean canShowDialog(Context context) {
        Activity activity = findActivity(context);
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        int depth = 0;
        while (current instanceof ContextWrapper && !(current instanceof Activity) && depth < 10) {
            current = ((ContextWrapper) current).getBaseContext();
            depth++;
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    private void cleanup() {
        if (dialog != null) {
            dialog.dismiss();
        }
    }
}
