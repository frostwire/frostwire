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
import com.frostwire.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 *         Created on 3/23/17.
 */

public final class KeywordFilterDrawerView extends LinearLayout implements KeywordTagView.KeywordTagViewListener {
    private static Logger LOG = Logger.getLogger(KeywordFilterDrawerView.class);
    private KeywordFiltersPipelineListener pipelineListener;
    private Map<KeywordDetector.Feature, Entry<String, Integer>[]> histograms;
    private static Map<KeywordDetector.Feature, Integer> featureContainerIds = new HashMap<>();

    static {
        featureContainerIds.put(KeywordDetector.Feature.SEARCH_SOURCE, R.id.view_drawer_search_filters_search_sources);
        featureContainerIds.put(KeywordDetector.Feature.FILE_EXTENSION, R.id.view_drawer_search_filters_file_extensions);
        featureContainerIds.put(KeywordDetector.Feature.FILE_NAME, R.id.view_drawer_search_filters_file_names);
    }

    public KeywordFilterDrawerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        histograms = new HashMap<>();
    }

    public void setKeywordFiltersPipelineListener(KeywordFiltersPipelineListener listener) {
        this.pipelineListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_drawer_search_filters, this);
        TextView clearAllTextView = (TextView) findViewById(R.id.view_drawer_search_filters_clear_all);
        clearAllTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAppliedFilters();
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
        findViewById(R.id.view_drawer_search_filters_search_sources_textview).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTagsContainer(R.id.view_drawer_search_filters_search_sources);
            }
        });
        findViewById(R.id.view_drawer_search_filters_file_extensions_textview).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTagsContainer(R.id.view_drawer_search_filters_file_extensions);
            }
        });
        findViewById(R.id.view_drawer_search_filters_file_names_textview).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTagsContainer(R.id.view_drawer_search_filters_file_names);
            }
        });
    }

    private void toggleTagsContainer(int containerId) {
        View v = findViewById(containerId);
        v.setVisibility(v.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private boolean onKeywordEntered(TextView v) {
        String keyword = v.getText().toString().trim().toLowerCase();
        if (keyword.length() == 0) {
            return true;
        }
        KeywordFilter keywordFilter = new KeywordFilter(true, keyword); // idea remember if user changed a filter to exclusive
        v.setText("");
        v.clearFocus();
        if (pipelineListener != null) {
            pipelineListener.onAddKeywordFilter(keywordFilter);
            updateAppliedKeywordFilters(pipelineListener.getKeywordFiltersPipeline());
        }
        UIUtils.hideKeyboardFromActivity((Activity) getContext());
        return true;
    }

    private void clearAppliedFilters() {
        FlowLayout flowLayout = (FlowLayout) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();
        updateAppliedKeywordFilters(Collections.EMPTY_LIST);
    }

    public void updateData(List<KeywordFilter> keywordFiltersPipeline, KeywordDetector.Feature feature, Entry<String, Integer>[] histogram) {
        if (keywordFiltersPipeline != null) {
            updateAppliedKeywordFilters(keywordFiltersPipeline);
        }
        if (feature != null && histogram != null && histogram.length > 0) {
            Entry<String, Integer>[] filteredHistogram = highPassFilter(histogram, feature.filterThreshold);
            updateSuggestedKeywordFilters(feature, filteredHistogram);
        }
    }

    private Entry<String, Integer>[] highPassFilter(Entry<String, Integer>[] histogram, float threshold) {
        int high = 0;
        int totalCount = 0;
        for (Entry<String, Integer> entry : histogram) {
            int count = entry.getValue();
            totalCount += count;
            if (count > high) {
                high = count;
            }
        }
        List<Entry<String, Integer>> filteredValues = new LinkedList<>();
        for (Entry<String, Integer> entry : histogram) {
            float rate = (float) entry.getValue()/(high + totalCount);
            if (entry.getValue() > 1 && rate >= threshold) {
                filteredValues.add(entry);
                LOG.info("<<< highPassFilter(total= " + totalCount + ", high=" + high + ", high+total=" + (high + totalCount) + ", rate=" + rate + "): <" + entry.getKey() + ":" + entry.getValue() + "> is IN");
            } else {
                LOG.info("<<< highPassFilter(total= " + totalCount + ", high=" + high + ", high+total=" + (high + totalCount) + ", rate=" + rate + "): <" + entry.getKey() + ":" + entry.getValue() + "> is OUT");
            }
        }
        // sort'em!
        filteredValues.sort(new Comparator<Entry<String, Integer>>() {
            @Override
            public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
                if (o1.getValue() == o2.getValue()) {
                    return 0;
                }
                return (o2.getValue() > o1.getValue()) ? 1 : -1;
            }
        });
        return filteredValues.toArray(new Entry[0]);
    }

    private void updateSuggestedKeywordFilters(KeywordDetector.Feature feature, Entry<String, Integer>[] histogram) {
        histograms.put(feature, histogram);
        Integer containerId = featureContainerIds.get(feature);
        FlowLayout container = (FlowLayout) findViewById(containerId);
        container.removeAllViews();
        for (Entry<String, Integer> entry : histogram) {
            KeywordTagView keywordTagView = new KeywordTagView(getContext(), new KeywordFilter(true, entry.getKey()), entry.getValue(), false, this);
            container.addView(keywordTagView);
        }
    }

    private void updateAppliedKeywordFilters(List<KeywordFilter> keywordFiltersPipeline) {
        FlowLayout flowLayout = (FlowLayout) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();

        if (keywordFiltersPipeline.size() > 0) {
            for (KeywordFilter filter : keywordFiltersPipeline) {
                KeywordTagView keywordTagView = new KeywordTagView(getContext(), filter, -1, true, this);
                flowLayout.addView(keywordTagView);
            }
        }

        if (pipelineListener != null) {
            pipelineListener.onPipelineUpdate(keywordFiltersPipeline);
        }
    }

    /**
     * KeywordTagViewListener.onDismissed
     */
    @Override
    public void onKeywordTagViewDismissed(KeywordTagView view) {
        FlowLayout flowLayout = (FlowLayout) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeView(view);

        if (pipelineListener != null) {
            // this will update the keywordFiltersPipeline
            pipelineListener.onRemoveKeywordFilter(view.getKeywordFilter());
            updateAppliedKeywordFilters(pipelineListener.getKeywordFiltersPipeline());
        }
    }

    @Override
    public void onKeywordTagViewTouched(KeywordTagView view) {
        if (pipelineListener == null) {
            return;
        }
        // if it's a dismissible one it's one of the applied filters
        List<KeywordFilter> keywordFiltersPipeline = pipelineListener.getKeywordFiltersPipeline();
        KeywordFilter keywordFilter = view.getKeywordFilter();
        if (view.isDismissible()) {
            int oldIndex = keywordFiltersPipeline.indexOf(keywordFilter);
            keywordFilter = view.toogleFilterInclusionMode();
            keywordFiltersPipeline.add(oldIndex, keywordFilter);
            keywordFiltersPipeline.remove(oldIndex + 1);
        } else {
            // attempt to add to pipeline
            if (!keywordFiltersPipeline.contains(keywordFilter)) {
                pipelineListener.onAddKeywordFilter(keywordFilter);
            }
        }
        updateAppliedKeywordFilters(keywordFiltersPipeline);
    }

    public interface KeywordFiltersPipelineListener {
        void onPipelineUpdate(List<KeywordFilter> pipeline);

        void onAddKeywordFilter(KeywordFilter keywordFilter);

        void onRemoveKeywordFilter(KeywordFilter keywordFilter);

        List<KeywordFilter> getKeywordFiltersPipeline();
    }
}
