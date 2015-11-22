/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2015, FrostWire(R). All rights reserved.
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

package com.frostwire.android.gui.activities;

import android.app.ActionBar;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import com.frostwire.android.R;
import com.frostwire.android.gui.StoragePicker;
import com.frostwire.android.gui.views.AbstractActivity;

/**
 * @author gubatron
 * @author aldenml
 */
public final class StoragePickerActivity extends AbstractActivity {

    public StoragePickerActivity() {
        super(R.layout.activity_storage_picker);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        StoragePicker.show(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == StoragePicker.SELECT_FOLDER_REQUEST_CODE) {
            String path = StoragePicker.handle(this, requestCode, resultCode, data);
            System.out.println(path);
            finish();
        }
    }
}
