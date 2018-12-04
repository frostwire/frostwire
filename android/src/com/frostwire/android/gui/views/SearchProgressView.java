/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private String stringTryOtherKeywords;
    private String stringTryChangingAppliedFilters;

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
        if (textTryOtherKeywordsOrFilters != null || !isDataUp) {
            textTryOtherKeywordsOrFilters.setVisibility(View.GONE);
        }
        if (textTryFrostWirePlus != null || !isDataUp) {
            textTryFrostWirePlus.setVisibility(View.GONE);
        }
        if (isDataUp) {
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
        stringTryOtherKeywords = getResources().getString(R.string.try_other_keywords);
        stringTryChangingAppliedFilters = getResources().getString(R.string.try_changing_applied_filters);

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
        buttonCancel.setText(R.string.retry_search);
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

    public void setKeywordFiltersApplied(boolean filtersApplied) {
        textTryOtherKeywordsOrFilters.setText(filtersApplied ? stringTryChangingAppliedFilters : stringTryOtherKeywords);
    }

    public interface CurrentQueryReporter {
        String getCurrentQuery();
    }
}
