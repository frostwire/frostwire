package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractFragment;

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
