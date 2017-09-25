package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractFragment;

public class TransferDetailTrackersFragment extends AbstractFragment {

    public TransferDetailTrackersFragment() {
        super(R.layout.fragment_transfer_detail_trackers);
        setHasOptionsMenu(true);
    }

    public static TransferDetailTrackersFragment newInstance(String text) {

        TransferDetailTrackersFragment f = new TransferDetailTrackersFragment();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
