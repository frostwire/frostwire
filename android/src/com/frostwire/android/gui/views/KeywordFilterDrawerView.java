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
import androidx.annotation.Nullable;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.core.ConfigurationManager;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.search.KeywordDetector;
import com.frostwire.search.KeywordFilter;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public final class KeywordFilterDrawerView extends LinearLayout {

    private KeywordFiltersPipelineListener pipelineListener;
    private final EnumMap<KeywordDetector.Feature, TagsController> featureContainer = new EnumMap<>(KeywordDetector.Feature.class);
    private LinearLayout appliedTagsTipTextViewContainer;
    private TextView clearAppliedFiltersTextView;
    private KeywordFilterDrawerController keywordFilterDrawerController;
    private ScrollView scrollView;
    private ViewGroup pipelineLayout;

    private final KeywordTagListener keywordTagListener = new KeywordTagListener();

    public KeywordFilterDrawerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setKeywordFiltersPipelineListener(KeywordFiltersPipelineListener listener) {
        this.pipelineListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_drawer_search_filters, this);

        scrollView = findView(R.id.view_drawer_search_filters_scrollview);
        appliedTagsTipTextViewContainer = findView(R.id.view_drawer_search_filters_touch_tag_tips_container);
        appliedTagsTipTextViewContainer.setVisibility(View.GONE);
        clearAppliedFiltersTextView = findView(R.id.view_drawer_search_filters_clear_all);
        clearAppliedFiltersTextView.setVisibility(View.GONE);
        pipelineLayout = findView(R.id.view_drawer_search_filters_pipeline_layout);
        findView(R.id.view_drawer_search_filters_exit_button).setOnClickListener(v -> onExitButtonClicked());
        clearAppliedFiltersTextView.setOnClickListener(v -> clearAppliedFilters());
        final ImageButton tagTipsCloseButton = findView(R.id.view_drawer_search_filters_touch_tag_tips_close_button);
        tagTipsCloseButton.setOnClickListener(v -> {
            appliedTagsTipTextViewContainer.setVisibility(View.GONE);
            ConfigurationManager.instance().setBoolean(Constants.PREF_KEY_GUI_SEARCH_KEYWORDFILTERDRAWER_TIP_TOUCHTAGS_DISMISSED, true);
        });
        final EditText keywordEditText = findView(R.id.view_drawer_search_filters_keyword_edittext);
        keywordEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                actionId = EditorInfo.IME_ACTION_DONE;
            }
            return actionId == EditorInfo.IME_ACTION_DONE && onKeywordEntered(v);
        });
        final TextView appliedTagsTipHtmlTextView = findView(R.id.view_drawer_search_filters_touch_tag_tips_text_html_textview);
        appliedTagsTipHtmlTextView.setText(Html.fromHtml(getResources().getString(R.string.tip_touch_tags_to)));

        final ImageButton clearTextButton = findView(R.id.view_drawer_search_filters_keyword_text_button_clear);
        clearTextButton.setVisibility(RelativeLayout.GONE);
        clearTextButton.setOnClickListener(v -> keywordEditText.setText(""));
        keywordEditText.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearTextButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
            }
        });
        featureContainer.put(KeywordDetector.Feature.SEARCH_SOURCE,
                new TagsController(
                        findView(R.id.view_drawer_search_filters_search_sources_textview),
                        findView(R.id.view_drawer_search_filters_search_sources),
                        findView(R.id.view_drawer_search_filters_search_sources_progress)));
        featureContainer.put(KeywordDetector.Feature.FILE_EXTENSION,
                new TagsController(
                        findView(R.id.view_drawer_search_filters_file_extensions_textview),
                        findView(R.id.view_drawer_search_filters_file_extensions),
                        findView(R.id.view_drawer_search_filters_file_extensions_progress)));
        featureContainer.put(KeywordDetector.Feature.FILE_NAME,
                new TagsController(
                        findView(R.id.view_drawer_search_filters_file_names_textview),
                        findView(R.id.view_drawer_search_filters_file_names),
                        findView(R.id.view_drawer_search_filters_file_names_progress)));
    }

    private void onExitButtonClicked() {
        keywordFilterDrawerController.closeKeywordFilterDrawer();
    }

    private void resetTagsContainers() {
        for (TagsController c : featureContainer.values()) {
            c.reset();
            c.showProgressView(true);
        }
        requestLayout();
    }

    private boolean onKeywordEntered(TextView v) {
        String keyword = v.getText().toString().trim().toLowerCase();
        if (keyword.length() == 0) {
            return true;
        }
        KeywordFilter keywordFilter = new KeywordFilter(true, keyword, KeywordDetector.Feature.MANUAL_ENTRY);
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
        pipelineLayout.removeAllViews();
        updateAppliedKeywordFilters(Collections.emptyList());
        for (TagsController c : featureContainer.values()) {
            c.restore();
            c.showProgressView(true);
        }
        scrollView.scrollTo(0, 0);
    }

    public void updateData(KeywordDetector.Feature feature, List<Entry<String, Integer>> filteredHistogram) {
        TagsController tagsController = featureContainer.get(feature);
        tagsController.hideHeader();
        tagsController.showProgressView(true);
        updateSuggestedKeywordFilters(feature, filteredHistogram);
        invalidate();
    }

    private void updateSuggestedKeywordFilters(KeywordDetector.Feature feature, List<Entry<String, Integer>> histogram) {
        TagsController tagsController = featureContainer.get(feature);
        tagsController.showProgressView(true);
        ViewGroup container = tagsController.container;
        container.removeAllViews();
        requestLayout();
        boolean keywordsApplied = false;
        List<KeywordFilter> keywordFiltersPipeline = null;
        if (pipelineListener != null) {
            keywordFiltersPipeline = pipelineListener.getKeywordFiltersPipeline();
            keywordsApplied = keywordFiltersPipeline.size() > 0;
        }
        int visibleTags = 0;
        for (Entry<String, Integer> entry : histogram) {
            int visibility = (keywordsApplied && keywordInPipeline(entry.getKey(), keywordFiltersPipeline)) ? View.GONE : View.VISIBLE;
            KeywordTagView keywordTagView = new KeywordTagView(getContext(), new KeywordFilter(true, entry.getKey(), feature), entry.getValue(), false, keywordTagListener);
            container.addView(keywordTagView);
            keywordTagView.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                visibleTags++;
            }
        }
        if (visibleTags > 0) {
            tagsController.showHeader();
        } else {
            tagsController.hideHeader();
        }
        tagsController.showProgressView(visibleTags == 0);
    }

    private boolean keywordInPipeline(String keyword, List<KeywordFilter> pipeline) {
        for (KeywordFilter filter : pipeline) {
            if (filter.getKeyword().equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    public void updateAppliedKeywordFilters(List<KeywordFilter> keywordFiltersPipeline) {
        boolean filtersHaveBeenApplied = keywordFiltersPipeline.size() > 0;
        int clearAppliedFiltersVisibility = filtersHaveBeenApplied ? View.VISIBLE : View.GONE;
        clearAppliedFiltersTextView.setVisibility(clearAppliedFiltersVisibility);

        // touch tags include/exclude tip container visibility logic
        boolean appliedTagsTipTextViewDismissedBefore = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_GUI_SEARCH_KEYWORDFILTERDRAWER_TIP_TOUCHTAGS_DISMISSED);
        int appliedTagsTipTextViewVisibility = (filtersHaveBeenApplied && !appliedTagsTipTextViewDismissedBefore) ? View.VISIBLE : View.GONE;
        appliedTagsTipTextViewContainer.setVisibility(appliedTagsTipTextViewVisibility);

        pipelineLayout.removeAllViews();
        if (filtersHaveBeenApplied) {
            for (KeywordFilter filter : keywordFiltersPipeline) {
                KeywordTagView keywordTagView = new KeywordTagView(getContext(), filter, -1, true, keywordTagListener);
                pipelineLayout.addView(keywordTagView);
            }
        }
        if (pipelineListener != null) {
            pipelineListener.onPipelineUpdate(keywordFiltersPipeline);
        }
    }

    public void reset() {
        // visual reset
        ((Activity) getContext()).runOnUiThread(() -> {
            clearAppliedFilters();
            resetTagsContainers();
        });
    }

    public void setKeywordFilterDrawerController(KeywordFilterDrawerController keywordFilterDrawerController) {
        this.keywordFilterDrawerController = keywordFilterDrawerController;
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findView(int id) {
        return (T) super.findViewById(id);
    }

    public void showIndeterminateProgressViews() {
        Set<KeywordDetector.Feature> features = featureContainer.keySet();
        for (KeywordDetector.Feature feature : features) {
            TagsController tagsController = featureContainer.get(feature);
            tagsController.showProgressView(tagsController.container.getChildCount() == 0);
        }
    }

    public void hideIndeterminateProgressViews() {
        Set<KeywordDetector.Feature> features = featureContainer.keySet();
        for (KeywordDetector.Feature feature : features) {
            TagsController tagsController = featureContainer.get(feature);
            tagsController.showProgressView(false);
        }
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

    private final class KeywordTagListener implements KeywordTagView.OnActionListener {

        @Override
        public void onTouched(KeywordTagView view) {
            if (pipelineListener == null) {
                return;
            }

            resetTagsContainers();

            // if it's a dismissible one it's one of the applied filters
            List<KeywordFilter> keywordFiltersPipeline = pipelineListener.getKeywordFiltersPipeline();
            KeywordFilter keywordFilter = view.getKeywordFilter();
            if (view.isDismissible() && keywordFiltersPipeline.size() > 0) {
                int oldIndex = keywordFiltersPipeline.indexOf(keywordFilter);
                keywordFilter = view.toggleFilterInclusionMode();
                keywordFiltersPipeline.add(oldIndex, keywordFilter);
                keywordFiltersPipeline.remove(oldIndex + 1);
            } else if (!view.isDismissible()) {
                // attempt to add to pipeline
                if (!keywordFiltersPipeline.contains(keywordFilter)) {
                    pipelineListener.onAddKeywordFilter(keywordFilter);
                }
                view.setVisibility(View.GONE);
            }

            updateAppliedKeywordFilters(keywordFiltersPipeline);
            scrollView.scrollTo(0, 0);
        }

        @Override
        public void onDismissed(KeywordTagView view) {
            pipelineLayout.removeView(view);

            resetTagsContainers();

            if (pipelineListener != null) {
                // this will update the keywordFiltersPipeline
                pipelineListener.onRemoveKeywordFilter(view.getKeywordFilter());
                updateAppliedKeywordFilters(pipelineListener.getKeywordFiltersPipeline());
            }
            // un-hide tag in container
            if (featureContainer != null && view.getKeywordFilter().getFeature() != null) {
                TagsController tagsController = featureContainer.get(view.getKeywordFilter().getFeature());
                if (tagsController == null || tagsController.container == null) {
                    return;
                }
                ViewGroup container = tagsController.container;
                for (int i = 0; i < container.getChildCount(); i++) {
                    KeywordTagView keywordTagView = (KeywordTagView) container.getChildAt(i);
                    if (keywordTagView.getKeywordFilter().getKeyword().equals(view.getKeywordFilter().getKeyword())) {
                        keywordTagView.setVisibility(View.VISIBLE);
                        break;
                    }
                }
            }
        }
    }

    // this is a mini controller for a sub-view of
    // tags, consisting of the label-header and the container
    private static final class TagsController {

        final TextView header;
        final ViewGroup container;
        final ProgressBar progressBar;

        TagsController(TextView header, ViewGroup container, ProgressBar progressBar) {
            this.header = header;
            this.container = container;
            this.progressBar = progressBar;
            this.header.setOnClickListener(v -> toggle());
        }

        boolean isExpanded() {
            return container.getVisibility() == View.VISIBLE;
        }

        void expand() {
            header.setCompoundDrawablesWithIntrinsicBounds(R.drawable.filter_minimize, 0, 0, 0);
            container.setVisibility(View.VISIBLE);
        }

        void collapse() {
            header.setCompoundDrawablesWithIntrinsicBounds(R.drawable.filter_expand, 0, 0, 0);
            container.setVisibility(View.GONE);
        }

        void toggle() {
            if (isExpanded()) {
                collapse();
            } else {
                expand();
            }
        }

        void hideHeader() {
            header.setVisibility(View.GONE);
            header.invalidate();
            showProgressView(false);
        }

        void showHeader() {
            header.setVisibility(View.VISIBLE);
            header.invalidate();
        }

        void restore() {
            int count = container.getChildCount();
            for (int i = 0; i < count; i++) {
                container.getChildAt(i).setVisibility(View.VISIBLE);
            }
            showProgressView(count == 0);
            // in case it was "collapsed"
            expand();
        }

        void reset() {
            container.removeAllViews();
            expand();
        }

        public void showProgressView(boolean visible) {
            progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}
