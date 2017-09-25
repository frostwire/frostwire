package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractFragment;

public class TransferDetailFilesFragment extends AbstractFragment {

    public TransferDetailFilesFragment() {
        super(R.layout.fragment_transfer_detail_files);
        setHasOptionsMenu(true);
    }

    public static TransferDetailFilesFragment newInstance(String text) {

        TransferDetailFilesFragment f = new TransferDetailFilesFragment();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
