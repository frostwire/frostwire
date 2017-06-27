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

import android.app.Activity;
import android.content.Context;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.search.KeywordDetector;
import com.frostwire.search.KeywordFilter;
import com.frostwire.util.Logger;

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
 */
public final class KeywordFilterDrawerView extends LinearLayout implements KeywordTagView.KeywordTagViewListener {

    private static Logger LOG = Logger.getLogger(KeywordFilterDrawerView.class);

    private KeywordFiltersPipelineListener pipelineListener;
    private Map<KeywordDetector.Feature, Entry<String, Integer>[]> histograms;
    private static Map<KeywordDetector.Feature, Integer> featureContainerIds = new HashMap<>();
    private TextView appliedTagsTipTextView;
    private TextView clearAppliedFiltersTextView;
    private KeywordFilterDrawerController keywordFilterDrawerController;
    private ScrollView scrollView;

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
        scrollView = (ScrollView) findViewById(R.id.view_drawer_search_filters_scrollview);
        appliedTagsTipTextView = (TextView) findViewById(R.id.view_drawer_search_filters_touch_tag_tips);
        appliedTagsTipTextView.setVisibility(View.GONE);
        clearAppliedFiltersTextView = (TextView) findViewById(R.id.view_drawer_search_filters_clear_all);
        clearAppliedFiltersTextView.setVisibility(View.GONE);

        findViewById(R.id.view_drawer_search_filters_exit_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onExitButtonClicked();
            }
        });


        clearAppliedFiltersTextView.setOnClickListener(new OnClickListener() {
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
                return actionId == EditorInfo.IME_ACTION_DONE && onKeywordEntered(v);
            }
        });

        // Init events for tag panel visibility toggling on sub-title and expand/minimize icons
        OnClickListener searchSourcesToggler = new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTagsContainer(R.id.view_drawer_search_filters_search_sources, R.id.view_drawer_search_filters_search_sources_expand_contract_imageview);
            }
        };
        findViewById(R.id.view_drawer_search_filters_search_sources_textview).setOnClickListener(searchSourcesToggler);
        findViewById(R.id.view_drawer_search_filters_search_sources_expand_contract_imageview).setOnClickListener(searchSourcesToggler);

        OnClickListener fileExtensionsToggler = new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTagsContainer(R.id.view_drawer_search_filters_file_extensions, R.id.view_drawer_search_filters_file_extensions_expand_contract_imageview);
            }
        };
        findViewById(R.id.view_drawer_search_filters_file_extensions_textview).setOnClickListener(fileExtensionsToggler);
        findViewById(R.id.view_drawer_search_filters_file_extensions_expand_contract_imageview).setOnClickListener(fileExtensionsToggler);

        OnClickListener fileNamesToggler = new OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTagsContainer(R.id.view_drawer_search_filters_file_names, R.id.view_drawer_search_filters_search_file_names_contract_imageview);
            }
        };
        findViewById(R.id.view_drawer_search_filters_file_names_textview).setOnClickListener(fileNamesToggler);
        findViewById(R.id.view_drawer_search_filters_search_file_names_contract_imageview).setOnClickListener(fileNamesToggler);
    }

    private void onExitButtonClicked() {
        if (keywordFilterDrawerController != null) {
            keywordFilterDrawerController.closeKeywordFilterDrawer();
        } else {
            LOG.warn("Check your logic, the keyword filter drawer controller has not been assigned");
        }
    }

    private void toggleTagsContainer(int containerId, int expandContractIconImageViewId) {
        View v = findViewById(containerId);
        v.setVisibility(v.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        ImageView expandContractIcon = (ImageView) findViewById(expandContractIconImageViewId);
        expandContractIcon.setImageResource(v.getVisibility() == View.VISIBLE ? R.drawable.filter_expand : R.drawable.filter_minimize);
    }


    private void resetTagsContainers() {
        resetTagsContainer(R.id.view_drawer_search_filters_search_sources, R.id.view_drawer_search_filters_search_sources_expand_contract_imageview);
        resetTagsContainer(R.id.view_drawer_search_filters_file_extensions, R.id.view_drawer_search_filters_file_extensions_expand_contract_imageview);
        resetTagsContainer(R.id.view_drawer_search_filters_file_names, R.id.view_drawer_search_filters_search_file_names_contract_imageview);
    }

    private void resetTagsContainer(int containerId, int expandContractIconImageViewId) {
        ViewGroup flowLayout = (ViewGroup) findViewById(containerId);
        flowLayout.removeAllViews();
        flowLayout.setVisibility(View.VISIBLE);
        ImageView expandContractIcon = (ImageView) findViewById(expandContractIconImageViewId);
        expandContractIcon.setImageResource(R.drawable.filter_expand);
    }

    private void showAllContainerTags(int containerId) {
        // in case it was "contracted"
        ViewGroup flowLayout = (ViewGroup) findViewById(containerId);
        flowLayout.setVisibility(View.VISIBLE);
        for (int i = 0; i < flowLayout.getChildCount(); i++) {
            flowLayout.getChildAt(i).setVisibility(View.VISIBLE);
        }
    }

    private boolean onKeywordEntered(TextView v) {
        String keyword = v.getText().toString().trim().toLowerCase();
        if (keyword.length() == 0) {
            return true;
        }
        KeywordFilter keywordFilter = new KeywordFilter(true, keyword, (KeywordDetector.Feature) null);
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
        ViewGroup flowLayout = (ViewGroup) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();
        updateAppliedKeywordFilters(Collections.EMPTY_LIST);
        showAllContainerTags(R.id.view_drawer_search_filters_search_sources);
        showAllContainerTags(R.id.view_drawer_search_filters_file_extensions);
        showAllContainerTags(R.id.view_drawer_search_filters_file_names);
        scrollView.scrollTo(0, 0);
    }

    public void updateData(List<KeywordFilter> keywordFiltersPipeline, KeywordDetector.Feature feature, Entry<String, Integer>[] histogram) {
        if (keywordFiltersPipeline != null) {
            updateAppliedKeywordFilters(keywordFiltersPipeline);
        }
        if (feature != null && histogram != null && histogram.length > 0) {
            Entry<String, Integer>[] filteredHistogram = highPassFilter(histogram, feature.filterThreshold);
            updateSuggestedKeywordFilters(feature, filteredHistogram);
        }
        invalidate();
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
            float rate = (float) entry.getValue() / (high + totalCount);
            if (entry.getValue() > 1 && rate >= threshold) {
                filteredValues.add(entry);
                //LOG.info("<<< highPassFilter(total= " + totalCount + ", high=" + high + ", high+total=" + (high + totalCount) + ", rate=" + rate + "): <" + entry.getKey() + ":" + entry.getValue() + "> is IN");
            } //else {
            //LOG.info("<<< highPassFilter(total= " + totalCount + ", high=" + high + ", high+total=" + (high + totalCount) + ", rate=" + rate + "): <" + entry.getKey() + ":" + entry.getValue() + "> is OUT");
            //}
        }
        // sort'em!
        Collections.sort(filteredValues, new Comparator<Entry<String, Integer>>() {
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
        ViewGroup container = (ViewGroup) findViewById(containerId);
        container.removeAllViews();
        boolean keywordsApplied = false;
        List<KeywordFilter> keywordFiltersPipeline = null;
        if (pipelineListener != null) {
            keywordFiltersPipeline = pipelineListener.getKeywordFiltersPipeline();
            keywordsApplied = keywordFiltersPipeline.size() > 0;
        }
        for (Entry<String, Integer> entry : histogram) {
            int visibility = (keywordsApplied && keywordInPipeline(entry.getKey(), keywordFiltersPipeline)) ? View.GONE : View.VISIBLE;
            KeywordTagView keywordTagView = new KeywordTagView(getContext(), new KeywordFilter(true, entry.getKey(), feature), entry.getValue(), false, this);
            container.addView(keywordTagView);
            keywordTagView.setVisibility(visibility);
        }
        container.invalidate();
    }

    private boolean keywordInPipeline(String keyword, List<KeywordFilter> pipeline) {
        for (KeywordFilter filter : pipeline) {
            if (filter.getKeyword().equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void updateAppliedKeywordFilters(List<KeywordFilter> keywordFiltersPipeline) {
        boolean filtersHaveBeenApplied = keywordFiltersPipeline.size() > 0;
        int textViewsVisibility = filtersHaveBeenApplied ? View.VISIBLE : View.GONE;
        clearAppliedFiltersTextView.setVisibility(textViewsVisibility);
        appliedTagsTipTextView.setVisibility(textViewsVisibility);

        ViewGroup flowLayout = (ViewGroup) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();
        if (filtersHaveBeenApplied) {
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
        ViewGroup flowLayout = (ViewGroup) findViewById(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeView(view);

        if (pipelineListener != null) {
            // this will update the keywordFiltersPipeline
            pipelineListener.onRemoveKeywordFilter(view.getKeywordFilter());
            updateAppliedKeywordFilters(pipelineListener.getKeywordFiltersPipeline());
        }

        // unhide tag in container
        if (view.getKeywordFilter().getFeature() != null) {
            int featureContainerId = featureContainerIds.get(view.getKeywordFilter().getFeature());
            ViewGroup container = (ViewGroup) findViewById(featureContainerId);
            for (int i = 0; i < container.getChildCount(); i++) {
                KeywordTagView keywordTagView = (KeywordTagView) container.getChildAt(i);
                if (keywordTagView.getKeywordFilter().getKeyword().equals(view.getKeywordFilter().getKeyword())) {
                    keywordTagView.setVisibility(View.VISIBLE);
                    break;
                }
            }
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
            view.setVisibility(View.GONE);
        }
        updateAppliedKeywordFilters(keywordFiltersPipeline);
        scrollView.scrollTo(0, 0);
    }

    public void reset() {
        // visual reset
        ((Activity) getContext()).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                clearAppliedFilters();
                resetTagsContainers();
            }
        });
    }

    public void setKeywordFilterDrawerController(KeywordFilterDrawerController keywordFilterDrawerController) {
        this.keywordFilterDrawerController = keywordFilterDrawerController;
    }

    public interface KeywordFilterDrawerController {
        void closeKeywordFilterDrawer();

        void openKeywordFilterDrawer();
    }

    public interface KeywordFiltersPipelineListener {
        void onPipelineUpdate(List<KeywordFilter> pipeline);

        void onAddKeywordFilter(KeywordFilter keywordFilter);

        void onRemoveKeywordFilter(KeywordFilter keywordFilter);

        List<KeywordFilter> getKeywordFiltersPipeline();
    }
}
