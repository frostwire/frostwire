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

import android.content.Intent;
import android.os.Bundle;

import com.frostwire.android.R;
import com.frostwire.android.core.FWFileDescriptor;
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
        Intent intent = getIntent();
        Bundle fileDescriptorBundle = intent.getBundleExtra(ImageViewerFragment.EXTRA_FILE_DESCRIPTOR_BUNDLE);

        if (fileDescriptorBundle != null && !fileDescriptorBundle.isEmpty()) {
            FWFileDescriptor fd = new FWFileDescriptor(fileDescriptorBundle);
            int position = intent.getIntExtra(ImageViewerFragment.EXTRA_ADAPTER_FILE_OFFSET, -1);
            ImageViewerFragment imageViewerFragment = findFragment(R.id.fragment_image_viewer);
            imageViewerFragment.updateData(fd, position);
            fileDescriptorBundle.clear();
        }
    }
}
