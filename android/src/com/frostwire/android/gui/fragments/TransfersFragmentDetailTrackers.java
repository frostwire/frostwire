package com.frostwire.android.gui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.frostwire.android.R;

public class TransfersFragmentDetailTrackers extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_transfer_detail_trackers, container, false);
        return v;
    }

    public static TransfersFragmentDetailTrackers newInstance(String text) {

        TransfersFragmentDetailTrackers f = new TransfersFragmentDetailTrackers();
        Bundle b = new Bundle();
        b.putString("msg", text);

        f.setArguments(b);

        return f;
    }
}
