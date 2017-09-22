package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.frostwire.android.R;

public class TransfersFragmentDetailPieces extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_transfer_detail_pieces, container, false);
        return v;
    }

    public static TransfersFragmentDetailPieces newInstance(String text) {

        TransfersFragmentDetailPieces f = new TransfersFragmentDetailPieces();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
