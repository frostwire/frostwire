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

package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractTransferDetailFragment;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailStatusFragment extends AbstractTransferDetailFragment {

    private TextView completedTextView;

    public TransferDetailStatusFragment() {
        super(R.layout.fragment_transfer_detail_status);
    }

    @Override
    protected void initComponents(View rootView, Bundle savedInstanceState) {
        super.initComponents(rootView, savedInstanceState);
        completedTextView = findView(rootView, R.id.fragment_transfer_detail_status_completion);
        completedTextView.setText("");
    }

    @Override
    public void onTime() {
        super.onTime();
        completedTextView.setText(uiBittorrentDownload.getProgress() + "%");
    }
}
