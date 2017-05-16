/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.activities;

import android.os.Bundle;

import com.frostwire.android.R;
import com.frostwire.android.core.FileDescriptor;
import com.frostwire.android.gui.fragments.ImageViewerFragment;
import com.frostwire.android.gui.views.AbstractActivity;

/**
 * @author aldenml
 * @author gubatron
 * @author votaguz
 */
public final class ImageViewerActivity extends AbstractActivity {

    public ImageViewerActivity() {
        super(R.layout.activity_image_viewer);
    }

    @Override
    protected void initComponents(Bundle savedInstanceState) {
        Bundle extras = getIntent().getBundleExtra(ImageViewerFragment.EXTRA_FILE_DESCRIPTOR);
        if (extras != null) {
            ImageViewerFragment imageViewerFragment = findFragment(R.id.fragment_image_viewer);
            FileDescriptor fd = new FileDescriptor(extras);
            imageViewerFragment.updateData(fd);
        }
    }
}
