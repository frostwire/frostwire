package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractFragment;

public class TransferDetailDetailsFragment extends AbstractFragment {

    public TransferDetailDetailsFragment() {
        super(R.layout.fragment_transfer_detail_details);
        setHasOptionsMenu(true);
    }

    public static TransferDetailDetailsFragment newInstance(String text) {

        TransferDetailDetailsFragment f = new TransferDetailDetailsFragment();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
