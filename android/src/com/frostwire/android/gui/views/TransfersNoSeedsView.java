/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml), Marcelinkaaa (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.gui.adapters.menu.SeedAction;
import com.frostwire.util.Ref;

import java.lang.ref.WeakReference;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 8/31/17.
 */

public class TransfersNoSeedsView extends LinearLayout {

    private TextView topTextView;
    private TextView midBoldTextView;
    private TextView bottomTextView;
    private Button button;
    private Mode mode;

    public enum Mode {
        INACTIVE,
        SEEDING_DISABLED,
        SEED_ALL_FINISHED
    }

    public TransfersNoSeedsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_transfers_no_seeds, this);
        if (isInEditMode()) {
            return;
        }
        topTextView = findViewById(R.id.view_transfers_no_seeds_top_textview);
        midBoldTextView = findViewById(R.id.view_transfers_no_seeds_mid_bold_textview);
        bottomTextView = findViewById(R.id.view_transfers_no_seeds_bottom_textview);
        button = findViewById(R.id.view_transfers_no_seeds_button);
        button.setOnClickListener(new OnButtonClickListener(this));
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        bottomTextView.setText(R.string.the_more_you_give_the_more_you_receive);
        switch (mode) {
            case INACTIVE:
                setVisibility(View.GONE);
                break;
            case SEEDING_DISABLED:
                topTextView.setText(R.string.seeding_is_currently_disabled);
                midBoldTextView.setText(R.string.seeding_keeps_the_network_healthy);
                button.setText(R.string.enable_seeding);
                setVisibility(VISIBLE);
                break;
            case SEED_ALL_FINISHED:
                topTextView.setText(R.string.nothing_is_currently_seeding);
                midBoldTextView.setText(R.string.would_you_like_to_seed_your_finished_torrent_transfers);
                button.setText(R.string.seed_all_finished);
                setVisibility(VISIBLE);
                break;
        }
    }

    private void onEnableSeeding() {
        ConfigurationManager.instance().setSeedFinishedTorrents(true);
        setMode(Mode.INACTIVE);
    }

    private void onSeedAllFinishedTransfers() {
        new SeedAction(getContext()).onClick();
        setMode(Mode.INACTIVE);
    }

    final private static class OnButtonClickListener implements OnClickListener {

        private final WeakReference<TransfersNoSeedsView> noSeedsViewRef;

        OnButtonClickListener(TransfersNoSeedsView noSeedsView) {
            noSeedsViewRef = Ref.weak(noSeedsView);
        }

        @Override
        public void onClick(View view) {
            if (Ref.alive(noSeedsViewRef)) {
                TransfersNoSeedsView transfersNoSeedsView = noSeedsViewRef.get();
                Mode mode = transfersNoSeedsView.mode;
                if (mode == Mode.SEED_ALL_FINISHED) {
                    transfersNoSeedsView.onSeedAllFinishedTransfers();
                } else if (mode == Mode.SEEDING_DISABLED) {
                    transfersNoSeedsView.onEnableSeeding();
                }
            }
        }
    }
}
