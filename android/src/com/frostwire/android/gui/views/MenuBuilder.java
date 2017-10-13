/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

        //builder.setTitle(adapter.getTabTitle());
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
