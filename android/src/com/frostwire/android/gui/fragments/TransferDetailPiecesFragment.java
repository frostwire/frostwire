package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractFragment;

public class TransferDetailPiecesFragment extends AbstractFragment {

    public TransferDetailPiecesFragment() {
        super(R.layout.fragment_transfer_detail_pieces);
        setHasOptionsMenu(true);
    }

    public static TransferDetailPiecesFragment newInstance(String text) {

        TransferDetailPiecesFragment f = new TransferDetailPiecesFragment();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
