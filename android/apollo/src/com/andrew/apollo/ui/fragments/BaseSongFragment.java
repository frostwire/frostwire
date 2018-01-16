package com.andrew.apollo.ui.fragments;

import android.view.View;
import android.widget.AdapterView;

import com.andrew.apollo.adapters.SongAdapter;
import com.andrew.apollo.model.Song;
import com.andrew.apollo.ui.fragments.profile.ApolloFragment;
import com.andrew.apollo.utils.MusicUtils;
import com.andrew.apollo.utils.NavUtils;
import com.andrew.apollo.utils.PreferenceUtils;

/**
 * Created by votaguz on 1/15/18.
 */

public abstract class BaseSongFragment extends ApolloFragment<SongAdapter, Song> {
    public BaseSongFragment(int groupId, int loaderId) {
        super(groupId, loaderId);
    }


    @Override
    public void onItemClick(final AdapterView<?> parent, final View view,
                            final int position, final long id) {
        mItem = mAdapter.getItem(position);
        NavUtils.openAudioPlayer(getActivity());
    }

    protected boolean isSimpleLayout() {
        return PreferenceUtils.getInstance(getActivity()).isSimpleLayout(getLayoutTypeName());
    }
}

