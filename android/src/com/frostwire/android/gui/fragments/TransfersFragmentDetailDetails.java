package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.frostwire.android.R;

public class TransfersFragmentDetailDetails extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_transfer_detail_details, container, false);
        return v;
    }

    public static TransfersFragmentDetailDetails newInstance(String text) {

        TransfersFragmentDetailDetails f = new TransfersFragmentDetailDetails();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
