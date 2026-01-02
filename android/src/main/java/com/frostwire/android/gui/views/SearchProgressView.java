/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *  *            Marcelina Knitter (@marcelinkaaa)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.views;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;

/**
 * @author gubatron
 * @author aldenml
 */
public class SearchProgressView extends LinearLayout {

    private ProgressBar progressbar;
    private Button buttonCancel;
    private TextView textNoResults;
    private TextView textTryOtherKeywordsOrFilters;
    private TextView textTryFrostWirePlus;
    private TextView textNoDataConnection;

    private boolean progressEnabled;
    private CurrentQueryReporter currentQueryReporter;
    private boolean isDataUp = true;

    public SearchProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.progressEnabled = true;
    }

    public void setDataUp(boolean value) {
        isDataUp = value;
    }

    public void setProgressEnabled(boolean enabled) {
        if (this.progressEnabled != enabled) {
            this.progressEnabled = enabled;
            if (enabled) {
                startProgress();
            } else {
                stopProgress();
            }
        }
    }

    public void setCancelOnClickListener(OnClickListener l) {
        buttonCancel.setOnClickListener(l);
    }

    private void showRetryViews() {
        if (textTryOtherKeywordsOrFilters != null && isDataUp) {
            textTryOtherKeywordsOrFilters.setVisibility(View.VISIBLE);
        }
        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && textTryFrostWirePlus != null && isDataUp) {
            textTryFrostWirePlus.setVisibility(View.VISIBLE);
        }
        if (!isDataUp) {
            textNoDataConnection.setVisibility(VISIBLE);
        }
    }

    private void hideRetryViews() {
        if (textTryOtherKeywordsOrFilters != null) {
            textTryOtherKeywordsOrFilters.setVisibility(View.GONE);
        }
        if (textTryFrostWirePlus != null) {
            textTryFrostWirePlus.setVisibility(View.GONE);
        }
        if (isDataUp && textNoDataConnection != null) {
            textNoDataConnection.setVisibility(GONE);
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        View.inflate(getContext(), R.layout.view_search_progress, this);

        if (isInEditMode()) {
            return;
        }

        progressbar = findViewById(R.id.view_search_progress_progressbar);
        buttonCancel = findViewById(R.id.view_search_progress_button_cancel);
        textNoResults = findViewById(R.id.view_search_progress_text_no_results_feedback);
        textTryOtherKeywordsOrFilters = findViewById(R.id.view_search_progress_try_other_keywords_or_filters);
        textNoDataConnection = findViewById(R.id.view_search_progress_no_data_connection);
        textTryFrostWirePlus = findViewById(R.id.view_search_progress_try_frostwire_plus);
        textTryFrostWirePlus.setPaintFlags(textTryFrostWirePlus.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION && textTryFrostWirePlus != null) {
            initTryFrostWirePlusListener();
        }
    }

    private void initTryFrostWirePlusListener() {
        textTryFrostWirePlus.setOnClickListener(v -> UIUtils.openURL(getContext(), Constants.FROSTWIRE_MORE_RESULTS));
    }

    private void startProgress() {
        progressbar.setVisibility(View.VISIBLE);
        buttonCancel.setText(android.R.string.cancel);
        textNoResults.setVisibility(View.GONE);
        hideRetryViews();
    }

    private void stopProgress() {
        progressbar.setVisibility(View.GONE);
        buttonCancel.setText(R.string.retry);
        textNoResults.setVisibility(View.VISIBLE);

        if (currentQueryReporter.getCurrentQuery() != null) {
            showRetryViews();
        } else {
            hideRetryViews();
        }
    }

    @Override
    public void setOnTouchListener(OnTouchListener l) {
        final int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            getChildAt(i).setOnTouchListener(l);
        }
        super.setOnTouchListener(l);
    }

    public void setCurrentQueryReporter(CurrentQueryReporter currentQueryReporter) {
        this.currentQueryReporter = currentQueryReporter;
    }

    public interface CurrentQueryReporter {
        String getCurrentQuery();
    }
}
