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

import android.content.Context;

import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
import com.frostwire.android.gui.Librarian;
import com.frostwire.android.gui.adapters.FileListAdapter;
import com.frostwire.android.gui.views.AbstractDialog;

import java.util.ArrayList;
import java.util.List;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class DeleteAdapterFilesMenuAction extends AbstractDeleteFilesMenuAction {

    private final FileListAdapter adapter;
    private final List<FWFileDescriptor> files;


    public DeleteAdapterFilesMenuAction(Context context, FileListAdapter adapter, List<FWFileDescriptor> files, AbstractDialog.OnDialogClickListener clickListener) {
        super(context, R.drawable.contextmenu_icon_trash, files.size() > 1 ? R.string.delete_file_menu_action_count : R.string.delete_file_menu_action, clickListener);
        this.adapter = adapter;
        this.files = files;
    }

    protected void onDeleteClicked() {
        if (adapter != null) {
            async(adapter, DeleteAdapterFilesMenuAction::deleteFilesTask, files,
                    DeleteAdapterFilesMenuAction::deleteFilesTaskPost);
        }
    }

    private static void deleteFilesTask(FileListAdapter fileListAdapter, List<FWFileDescriptor> files) {
        byte fileType = fileListAdapter.getFileType();
        Librarian.instance().deleteFiles(fileListAdapter.getContext(), fileType, new ArrayList<>(files));
        int size = files.size();
        for (int i = 0; i < size; i++) {
            try {
                FWFileDescriptor fd = files.get(i);
                fileListAdapter.deleteItem(fd); // only notifies if in main thread
            } catch (Throwable ignored) {
            }
        }
    }

    private static void deleteFilesTaskPost(FileListAdapter fileListAdapter, @SuppressWarnings("unused") List<FWFileDescriptor> files) {
        fileListAdapter.notifyDataSetChanged();
    }
}
