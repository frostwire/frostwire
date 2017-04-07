/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 * Marcelina Knitter (@marcelinkaaa)
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

import android.app.Activity;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.search.KeywordDetector;
import com.frostwire.search.KeywordFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 * Created on 3/23/17.
 */

public final class KeywordFilterDrawerView extends LinearLayout implements KeywordTagView.KeywordTagViewListener {
    private KeywordFiltersPipelineListener listener;
    private List<KeywordFilter> keywordFiltersPipeline;
    private Map<KeywordDetector.Feature, Map.Entry<String, Integer>[]> histograms;
    private static Map<KeywordDetector.Feature, Integer> featureContainerIds = new HashMap<>();

    static {
        featureContainerIds.put(KeywordDetector.Feature.SEARCH_SOURCE, R.id.view_drawer_search_filters_search_sources);
        featureContainerIds.put(KeywordDetector.Feature.FILE_EXTENSION, R.id.view_drawer_search_filters_file_extensions);
        featureContainerIds.put(KeywordDetector.Feature.FILE_NAME, R.id.view_drawer_search_filters_file_names);
    }

    public KeywordFilterDrawerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        keywordFiltersPipeline = new ArrayList<>();
        histograms = new HashMap<>();
    }

    public void setKeywordFiltersPipelineListener(KeywordFiltersPipelineListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_drawer_search_filters, this);

        TextView clearAllTextView = (TextView) findViewById(R.id.view_drawer_search_filters_clear_all);
        clearAllTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAll();
            }
        });

        EditText keywordEditText = (EditText) findViewById(R.id.view_drawer_search_filters_keyword_edittext);
        keywordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    actionId = EditorInfo.IME_ACTION_DONE;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    return onKeywordEntered(v);
                }
                return false;
            }
        });
    }

    private boolean onKeywordEntered(TextView v) {
        String keyword = v.getText().toString().trim().toLowerCase();
        if (keyword.length() == 0) {
            return true;
        }
        KeywordFilter keywordFilter = new KeywordFilter(true, keyword); // idea remember if user changed a filter to exclusive
        v.setText("");
        v.clearFocus();
        if (listener != null) {
            listener.onAddKeywordFilter(keywordFilter);
        }
        UIUtils.hideKeyboardFromActivity((Activity) getContext());
        return true;
    }

    private void clearAll() {
        FlowLayout flowLayout = (FlowLayout) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();

        flowLayout = (FlowLayout) findViewById(featureContainerIds.get(KeywordDetector.Feature.SEARCH_SOURCE));
        flowLayout.removeAllViews();

        if (listener != null) {
            listener.onPipelineUpdate(new ArrayList<KeywordFilter>());
        }
    }

    public void updateData(List<KeywordFilter> keywordFiltersPipeline, KeywordDetector.Feature feature, Map.Entry<String, Integer>[] histogram) {
        if (keywordFiltersPipeline != null) {
            updateAppliedKeywordFilters(keywordFiltersPipeline);
        }
        if (feature != null && histogram != null) {
            updateSuggestedKeywordFilters(feature, histogram);
        }
    }

    private void updateSuggestedKeywordFilters(KeywordDetector.Feature feature, Map.Entry<String, Integer>[] histogram) {
        histograms.put(feature, histogram);
        Integer containerId = featureContainerIds.get(feature);
        FlowLayout container = (FlowLayout) findViewById(containerId);
        container.removeAllViews();
        for(Map.Entry<String, Integer> entry : histogram) {
            KeywordTagView keywordTagView = new KeywordTagView(getContext(), new KeywordFilter(true, entry.getKey()), entry.getValue(), false, this);
            container.addView(keywordTagView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            keywordTagView.postInvalidate();
        }
    }

    private void updateAppliedKeywordFilters(List<KeywordFilter> keywordFiltersPipeline) {
        FlowLayout flowLayout = (FlowLayout) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();
        for (KeywordFilter filter : keywordFiltersPipeline) {
            int keywordCount = getKeywordCount(filter.getKeyword());
            KeywordTagView keywordTagView = new KeywordTagView(getContext(), filter, keywordCount, true, this);
            flowLayout.addView(keywordTagView, 50, 30);
        }
        this.keywordFiltersPipeline = keywordFiltersPipeline;
    }

    private int getKeywordCount(String keyword) {
        // TODO: Gotta do this better later
        for (KeywordDetector.Feature feature : histograms.keySet()) {
            Map.Entry<String, Integer>[] entries = histograms.get(feature);
            for (Map.Entry<String, Integer> entry : entries) {
                if (entry.getKey().equals(keyword)) {
                    return entry.getValue();
                }
            }
        }
        return 0;
    }

    /** KeywordTagViewListener.onDismissed */
    @Override
    public void onKeywordTagViewDismissed(KeywordTagView view) {
        FlowLayout flowLayout = (FlowLayout) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeView(view);
        keywordFiltersPipeline.remove(view.getKeywordFilter());
        if (listener != null) {
            try {
                listener.onPipelineUpdate(keywordFiltersPipeline);
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    public void onKeywordTagViewTouched(KeywordTagView view) {
        // if it's a dismissible one it's one of the applied filters
        KeywordFilter keywordFilter = view.getKeywordFilter();
        if (view.isDismissable()) {
            int oldIndex = keywordFiltersPipeline.indexOf(keywordFilter);
            keywordFilter = view.toogleFilterInclusionMode();
            keywordFiltersPipeline.add(oldIndex, keywordFilter);
            keywordFiltersPipeline.remove(oldIndex+1);
        } else {
            // attempt to add to pipeline
            if (!keywordFiltersPipeline.contains(keywordFilter)) {
                keywordFiltersPipeline.add(keywordFilter);
            }
        }

        updateAppliedKeywordFilters(keywordFiltersPipeline);
        if (listener != null) {
            listener.onPipelineUpdate(keywordFiltersPipeline);
        }
    }

    public interface KeywordFiltersPipelineListener {
        void onPipelineUpdate(List<KeywordFilter> pipeline);
        void onAddKeywordFilter(KeywordFilter keywordFilter);
        void onRemoveKeywordFilter(KeywordFilter keywordFilter);
        void clearPipeline();
    }
}
