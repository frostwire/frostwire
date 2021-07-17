/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters.menu;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;

import org.apache.commons.io.FilenameUtils;

import java.util.Calendar;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 5/12/17.
 */
public final class FileInformationAction extends MenuAction {

    private final FWFileDescriptor fd;

    public FileInformationAction(Context context, FWFileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_file, R.string.file_information);
        this.fd = fd;
    }

    @Override
    public void onClick(Context context) {
        FileInformationDialog.newInstance(fd).show(((Activity) getContext()).getFragmentManager());
    }

    public static class FileInformationDialog extends AbstractDialog {
        private TextView fileNameTextView;
        private TextView fileSizeTextView;
        private TextView fileDateTextView;
        private TextView fileStoragePathTextView;
        private FWFileDescriptor FWFileDescriptor;

        public FileInformationDialog() {
            super(R.layout.dialog_file_information);
        }

        public static FileInformationDialog newInstance(FWFileDescriptor FWFileDescriptor) {
            FileInformationDialog dlg = new FileInformationDialog();
            dlg.FWFileDescriptor = FWFileDescriptor;
            return dlg;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            if (outState != null) {
                outState.putAll(FWFileDescriptor.toBundle());
            }
            super.onSaveInstanceState(outState);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            if (savedInstanceState == null) {
                savedInstanceState = new Bundle();
                savedInstanceState.putAll(FWFileDescriptor.toBundle());
            } else {
                FWFileDescriptor = new FWFileDescriptor(savedInstanceState);
            }

            fileNameTextView = findView(dlg, R.id.dialog_file_information_filename);
            fileSizeTextView = findView(dlg, R.id.dialog_file_information_filesize);
            fileDateTextView = findView(dlg, R.id.dialog_file_information_date_created);
            fileStoragePathTextView = findView(dlg, R.id.dialog_file_information_storage_path);
            updateFileMetadata(FWFileDescriptor);
            Button buttonOk = findView(dlg, R.id.dialog_file_information_button_ok);
            buttonOk.setOnClickListener(v -> onOkButtonClick());
        }

        private void updateFileMetadata(FWFileDescriptor fd) {
            fileNameTextView.setText(FilenameUtils.getName(fd.filePath));
            fileSizeTextView.setText(UIUtils.getBytesInHuman(fd.fileSize));
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(fd.dateAdded * 1000);
            int numMonth = cal.get(Calendar.MONTH) + 1;
            int numDay = cal.get(Calendar.DAY_OF_MONTH) + 1;
            String month = numMonth >= 10 ? String.valueOf(numMonth) : "0" + numMonth;
            String day = numDay >= 10 ? String.valueOf(numDay) : "0" + numDay;
            String date = cal.get(Calendar.YEAR) + "-" + month + "-" + day;
            fileDateTextView.setText(date);
            fileStoragePathTextView.setText(fd.filePath);
            fileStoragePathTextView.setClickable(true);
        }

        private void onOkButtonClick() {
            dismiss();
        }
    }
}
