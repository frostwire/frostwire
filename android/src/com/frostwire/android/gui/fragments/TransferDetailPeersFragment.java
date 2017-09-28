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
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractFragment;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */

public class TransferDetailPeersFragment extends AbstractFragment {

    public TransferDetailPeersFragment() {
        super(R.layout.fragment_transfer_detail_peers);
        setHasOptionsMenu(true);
    }

    public static TransferDetailPeersFragment newInstance(String text) {

        TransferDetailPeersFragment f = new TransferDetailPeersFragment();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
