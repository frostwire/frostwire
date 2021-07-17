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
import com.frostwire.android.gui.views.AbstractDialog;

import java.util.Collections;

import static com.frostwire.android.util.Asyncs.async;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class DeleteSingleFileMenuAction extends AbstractDeleteFilesMenuAction {

    private final FWFileDescriptor FWFileDescriptor;
    private final byte fileType;

    public DeleteSingleFileMenuAction(Context context, byte fileType, FWFileDescriptor file, AbstractDialog.OnDialogClickListener clickListener) {
        super(context, R.drawable.contextmenu_icon_trash, R.string.delete_file_menu_action, clickListener);
        this.fileType = fileType;
        this.FWFileDescriptor = file;
    }

    protected void onDeleteClicked() {
        if (getContext() != null && fileType != (byte) -1) {
            async(getContext(), Librarian.instance()::deleteFiles, fileType, Collections.singletonList(FWFileDescriptor));
        }
    }
}
