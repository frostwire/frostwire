/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.adapters.menu;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.android.gui.views.MenuAction;

import org.apache.commons.io.FilenameUtils;

import java.util.Calendar;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 5/12/17.
 */


public final class FileInformationAction extends MenuAction {
    final FileDescriptor fd;
    public FileInformationAction(Context context, FileDescriptor fd) {
        super(context, R.drawable.contextmenu_icon_file, R.string.file_information);
        this.fd = fd;
    }

    @Override
    protected void onClick(Context context) {
        new FileInformationDialog(fd).show(((Activity) getContext()).getFragmentManager());
    }

    public static class FileInformationDialog extends AbstractDialog {
        private TextView fileNameTextView;
        private TextView fileSizeTextView;
        private TextView fileDateTextView;
        private TextView fileStoragePathTextView;
        private FileDescriptor fileDescriptor;

        public FileInformationDialog() {
            super(R.layout.dialog_file_information);
        }

        public FileInformationDialog(FileDescriptor fileDescriptor) {
            super(R.layout.dialog_file_information);
            this.fileDescriptor = fileDescriptor;
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            if (outState != null) {
                outState.putAll(fileDescriptor.toBundle());
            }
            super.onSaveInstanceState(outState);
        }

        @Override
        protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
            if (savedInstanceState == null) {
                savedInstanceState = new Bundle();
                savedInstanceState.putAll(fileDescriptor.toBundle());
            } else {
                fileDescriptor = new FileDescriptor(savedInstanceState);
            }

            fileNameTextView = findView(dlg, R.id.dialog_file_information_filename);
            fileSizeTextView = findView(dlg, R.id.dialog_file_information_filesize);
            fileDateTextView = findView(dlg, R.id.dialog_file_information_date_created);
            fileStoragePathTextView = findView(dlg, R.id.dialog_file_information_storage_path);
            updateFileMetadata(fileDescriptor);
            Button buttonOk = findView(dlg, R.id.dialog_file_information_button_ok);
            buttonOk.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onOkButtonClick();
                }
            });
        }

        private void updateFileMetadata(FileDescriptor fd) {
            fileNameTextView.setText(FilenameUtils.getName(fd.filePath));
            fileSizeTextView.setText(UIUtils.getBytesInHuman(fd.fileSize));
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(fd.dateAdded*1000);
            int numMonth = cal.get(Calendar.MONTH) + 1;
            int numDay = cal.get(Calendar.DAY_OF_MONTH) + 1;
            String month = numMonth >= 10 ? String.valueOf(numMonth) : "0" + numMonth;
            String day = numDay >= 10 ? String.valueOf(numDay) : "0" + numDay;
            fileDateTextView.setText(cal.get(Calendar.YEAR) + "-" + month + "-" + day);
            fileStoragePathTextView.setText(fd.filePath);
            fileStoragePathTextView.setClickable(true);
        }

        private void onOkButtonClick() {
            dismiss();
        }
    }
}
