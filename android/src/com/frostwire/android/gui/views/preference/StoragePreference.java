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

package com.frostwire.android.gui.views.preference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.os.Bundle;
import android.os.Environment;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.SystemPaths;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractAdapter;
import com.frostwire.android.gui.views.AbstractAdapter.OnItemClickAdapter;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.uxstats.UXAction;
import com.frostwire.uxstats.UXStats;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public class StoragePreference extends DialogPreference {

    private AlertDialog confirmDlg;
    private String selectedPath;

    public StoragePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.dialog_preference_storage);
        setPositiveButtonText(null);
    }

    public StoragePreference(Context context) {
        this(context, null);
    }

    @Override
    public void showDialog(Bundle state) {
        super.showDialog(state);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        ListView list = (ListView) view.findViewById(R.id.dialog_preference_storage_list);

        list.setAdapter(new StoragesAdapter(getContext()));
        list.setOnItemClickListener(new OnItemClickAdapter<StorageMount>() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, AbstractAdapter<StorageMount> adapter, int position, long id) {
                selectedPath = adapter.getItem(position).path;

                //if you select the one that was there before dismiss the dialog.
                if (ConfigurationManager.instance().getStoragePath().equals(selectedPath)) {
                    dismissPreferenceDialog();
                } else {
                    //if you select the SD Card option, show the confirmation dialog, with ok button disabled.
                    //will be enabled after user clicks on checkbox.
                    confirmDlg = createConfirmDialog(getContext());
                    confirmDlg.show();
                    confirmDlg.getButton(Dialog.BUTTON_POSITIVE).setEnabled(false);
                }
            }
        });
        

        TextView warningText = (TextView) view.findViewById(R.id.dialog_preference_storage_warning);
        warningText.setVisibility(list.getCount() == 1 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onActivityDestroy() {
        dismissConfirmDialog();
        super.onActivityDestroy();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (dialog != null && dialog.equals(confirmDlg)) {
            if (which == Dialog.BUTTON_POSITIVE && selectedPath != null) {
                ConfigurationManager.instance().setStoragePath(selectedPath);
                BTEngine.ctx.dataDir = new File(selectedPath).getAbsoluteFile();
                BTEngine.ctx.torrentsDir = SystemPaths.getTorrents();
                dismissPreferenceDialog();
                uxLogSelection();
            }
        } else {
            super.onClick(dialog, which);
        }
    }

    private void uxLogSelection() {
        try {
            File file = new File(selectedPath);
            boolean isInternalMemory = SystemUtils.isPrimaryExternalPath(file);
            UXStats.instance().log(isInternalMemory ? UXAction.SETTINGS_SET_STORAGE_INTERNAL_MEMORY : UXAction.SETTINGS_SET_STORAGE_SD_CARD);
        } catch (Throwable t) {
            //we tried.
        }
    }

    private AlertDialog createConfirmDialog(Context context) {
        OnMultiChoiceClickListener checkListener = new OnMultiChoiceClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                if (confirmDlg != null && confirmDlg.isShowing()) {
                    confirmDlg.getButton(Dialog.BUTTON_POSITIVE).setEnabled(isChecked);
                }
            }
        };
        return new AlertDialog.Builder(context).
                setMultiChoiceItems(new String[] { context.getString(R.string.storage_setting_confirm_dialog_text) }, new boolean[] { false }, checkListener).
                setTitle(R.string.storage_setting_confirm_dialog_title).
                setPositiveButton(android.R.string.ok, this).
                setNegativeButton(android.R.string.cancel, this).
                create();
    }

    private void dismissConfirmDialog() {
        if (confirmDlg != null && confirmDlg.isShowing()) {
            confirmDlg.dismiss();
        }
    }

    private void dismissPreferenceDialog() {
        if (getDialog() != null) {
            getDialog().dismiss();
        }
    }

    private static final class StorageMount {

        public StorageMount(String label, String description, String path, boolean primary) {
            this.label = label;
            this.description = description;
            this.path = path;
            this.primary = primary;
        }

        public final String label;
        public final String description;
        public final String path;
        public final boolean primary;
    }

    private final class StoragesAdapter extends AbstractAdapter<StorageMount> {

        public StoragesAdapter(Context context) {
            super(context, R.layout.view_preference_storage_list_item);

            addItems(context);
        }

        @Override
        protected void setupView(View view, ViewGroup parent, StorageMount item) {
            ImageView icon = findView(view, R.id.view_preference_storage_list_item_icon);
            TextView label = findView(view, R.id.view_preference_storage_list_item_label);
            TextView description = findView(view, R.id.view_preference_storage_list_item_description);
            RadioButton radio = findView(view, R.id.view_preference_storage_list_item_radio);

            icon.setImageResource(item.primary ? R.drawable.internal_memory_notification_dark_bg : R.drawable.sd_card_notification_dark_bg);
            label.setText(item.label);
            description.setText(item.description);

            radio.setChecked(ConfigurationManager.instance().getStoragePath().equals(item.path));
        }

        private void addItems(Context context) {
            StorageMount primary = getPrimaryExternal(context);
            if (primary != null) {
                add(primary);
            }

            for (StorageMount mount : getSecondayExternals(context)) {
                add(mount);
            }
        }

        private StorageMount getPrimaryExternal(Context context) {
            StorageMount mount = null;

            if (SystemUtils.isPrimaryExternalStorageMounted()) {
                File dir = Environment.getExternalStorageDirectory();

                String label = context.getString(R.string.device_storage);
                String description = UIUtils.getBytesInHuman(SystemUtils.getAvailableStorageSize(dir)) + " " + context.getString(R.string.available);
                String path = dir.getAbsolutePath();

                mount = new StorageMount(label, description, path, true);
            }

            return mount;
        }

        private List<StorageMount> getSecondayExternals(Context context) {
            List<StorageMount> mounts = new ArrayList<StorageMount>();

            // "Returns null if external storage is not currently mounted".
            File externalFilesDir = context.getExternalFilesDir(null);

            if (externalFilesDir != null) {
                String primaryPath = externalFilesDir.getParent();

                int i = 0;

                for (File f : SystemUtils.getExternalFilesDirs(context)) {
                    if (!f.getAbsolutePath().startsWith(primaryPath) &&
                            SystemUtils.isSecondaryExternalStorageMounted(f)) {

                        String label = context.getString(R.string.sdcard_storage) + " " + (++i);
                        String description = UIUtils.getBytesInHuman(SystemUtils.getAvailableStorageSize(f)) + " " + context.getString(R.string.available);
                        String path = f.getAbsolutePath();

                        StorageMount mount = new StorageMount(label, description, path, false);

                        mounts.add(mount);
                    }
                }
            }

            return mounts;
        }
    }
}
