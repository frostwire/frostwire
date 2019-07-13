/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.views.preference;

import android.content.Context;
import android.os.Bundle;
import android.os.Environment;
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
import com.frostwire.android.gui.dialogs.YesNoDialog;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractAdapter;
import com.frostwire.android.gui.views.AbstractAdapter.OnItemClickAdapter;
import com.frostwire.android.gui.views.AbstractDialog.OnDialogClickListener;
import com.frostwire.android.gui.views.AbstractPreferenceFragment.PreferenceDialogFragment;
import com.frostwire.android.util.SystemUtils;
import com.frostwire.bittorrent.BTEngine;
import com.frostwire.platform.Platforms;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.preference.DialogPreference;

/**
 * @author gubatron
 * @author aldenml
 */
public final class KitKatStoragePreference extends DialogPreference {

    private static final String CONFIRM_DIALOG_TAG = "KitKatStoragePreference.DIALOG";

    public KitKatStoragePreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.dialog_preference_kitkat_storage);
        setPositiveButtonText(null);
    }

    public KitKatStoragePreference(Context context) {
        this(context, null);
    }

    public static final class KitKatStoragePreferenceDialog
            extends PreferenceDialogFragment
            implements OnDialogClickListener {

        private String selectedPath;

        @Override
        public void onDialogClosed(boolean positiveResult) {
        }

        @Override
        protected void onBindDialogView(View view) {
            super.onBindDialogView(view);

            ListView list = view.findViewById(R.id.dialog_preference_kitkat_storage_list);

            list.setAdapter(new StoragesAdapter(getActivity()));
            list.setOnItemClickListener(new OnItemClickAdapter<StorageMount>() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, AbstractAdapter<StorageMount> adapter, int position, long id) {
                    selectedPath = adapter.getItem(position).path;

                    //if you select the one that was there before dismiss the dialog.
                    if (ConfigurationManager.instance().getStoragePath().equals(selectedPath)) {
                        dismiss();
                    } else {
                        //if you select the SD Card option, show the confirmation dialog, with ok button disabled.
                        //will be enabled after user clicks on checkbox.
                        YesNoDialog dlg = YesNoDialog.newInstance(
                                CONFIRM_DIALOG_TAG,
                                R.string.storage_setting_confirm_dialog_title,
                                R.string.storage_setting_confirm_dialog_text,
                                YesNoDialog.FLAG_DISMISS_ON_OK_BEFORE_PERFORM_DIALOG_CLICK
                        );
                        dlg.setTargetFragment(KitKatStoragePreferenceDialog.this, 0);
                        dlg.show(getFragmentManager(), CONFIRM_DIALOG_TAG);
                    }
                }
            });


            TextView warningText = view.findViewById(R.id.dialog_preference_kitkat_storage_warning);
            warningText.setVisibility(list.getCount() == 1 ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onDialogClick(String tag, int which) {
            ConfigurationManager.instance().setStoragePath(selectedPath);
            BTEngine.ctx.dataDir = Platforms.data();
            BTEngine.ctx.torrentsDir = Platforms.torrents();
            dismiss();
        }

        public static KitKatStoragePreferenceDialog newInstance(String key) {
            KitKatStoragePreferenceDialog f = new KitKatStoragePreferenceDialog();
            Bundle b = new Bundle(1);
            b.putString(ARG_KEY, key);
            f.setArguments(b);
            return f;
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

    private static final class StoragesAdapter extends AbstractAdapter<StorageMount> {

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

            for (StorageMount mount : getSecondaryExternals(context)) {
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

        private List<StorageMount> getSecondaryExternals(Context context) {
            List<StorageMount> mounts = new ArrayList<>();

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
