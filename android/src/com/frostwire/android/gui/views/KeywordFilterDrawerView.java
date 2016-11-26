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

import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.adapters.SearchResultListAdapter;
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
    }

    private void clearAll() {
        FlowLayout flowLayout = (FlowLayout) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();
    }

    public void updateData(List<KeywordFilter> keywordFiltersPipeline, KeywordDetector.Feature feature, Map.Entry<String, Integer>[] histogram) {
        this.keywordFiltersPipeline = keywordFiltersPipeline;
        histograms.put(feature, histogram);
        updateAppliedKeywordFilters(keywordFiltersPipeline);
        updateSuggestedKeywordFilters(feature, histogram);
    }

    private void updateSuggestedKeywordFilters(KeywordDetector.Feature feature, Map.Entry<String, Integer>[] histogram) {
        Integer containerId = featureContainerIds.get(feature);
        FlowLayout container = (FlowLayout) findViewById(containerId);
        container.removeAllViews();
        if (container.getChildCount() == 0) {
            for(Map.Entry<String, Integer> entry : histogram) {
                KeywordTagView keywordTagView = new KeywordTagView(getContext(), new KeywordFilter(true, entry.getKey()), entry.getValue(), false, this);
                container.addView(keywordTagView);
            }
            container.invalidate();
        }
    }

    private void updateAppliedKeywordFilters(List<KeywordFilter> keywordFiltersPipeline) {
        FlowLayout flowLayout = (FlowLayout) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();
        if (flowLayout.getChildCount() == 0) {
            for (KeywordFilter filter : keywordFiltersPipeline) {
                int keywordCount = getKeywordCount(filter.getKeyword());
                flowLayout.addView(new KeywordTagView(getContext(), filter, keywordCount, true, this));
            }
        }
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
        // if it's a dismissable one it's one of the applied filters
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
    }
}
