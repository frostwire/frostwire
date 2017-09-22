package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.frostwire.android.R;

public class TransfersFragmentDetailPeers extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_transfer_detail_peers, container, false);
        return v;
    }

    public static TransfersFragmentDetailPeers newInstance(String text) {

        TransfersFragmentDetailPeers f = new TransfersFragmentDetailPeers();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
