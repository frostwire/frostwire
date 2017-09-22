package com.frostwire.android;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.frostwire.android.gui.views.AbstractFragment;

public final class TransferDetailDetailsFragment extends AbstractFragment {

    public TransferDetailDetailsFragment() {
        super(R.layout.fragment_transfer_detail_details_main);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transfer_detail_details, container, false);
    }
}
