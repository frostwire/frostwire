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
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.search.KeywordFilter;
import com.frostwire.util.Logger;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public class KeywordTagView extends LinearLayout {

    private static final Logger LOG = Logger.getLogger(KeywordTagView.class);

    private boolean dismissible;
    private KeywordFilter keywordFilter;
    private int count;
    private KeywordTagViewListener listener;
    private TextAppearanceSpan keywordSpan;
    private TextAppearanceSpan countSpan;

    public interface KeywordTagViewListener {
        void onKeywordTagViewDismissed(KeywordTagView view);

        void onKeywordTagViewTouched(KeywordTagView view);
    }

    private KeywordTagView(Context context, AttributeSet attrs, KeywordFilter keywordFilter) {
        super(context, attrs);
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.KeywordTagView, 0, 0);
        count = attributes.getInteger(R.styleable.KeywordTagView_keyword_tag_count, 0);
        dismissible = attributes.getBoolean(R.styleable.KeywordTagView_keyword_tag_dismissable, true);

        if (keywordFilter == null) { // try to build one from attribute values
            boolean inclusive = attributes.getBoolean(R.styleable.KeywordTagView_keyword_tag_inclusive, true);
            String keyword = attributes.getString(R.styleable.KeywordTagView_keyword_tag_keyword);
            if (keyword == null) {
                keyword = ""; // dummy value
            }
        } else {
            this.keywordFilter = keywordFilter;
        }

        attributes.recycle();
        setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        setVisibility(View.VISIBLE);
        inflate(context, R.layout.view_keyword_tag, this);

        // TODO: refactor and move logic to onLayoutInflate
        keywordSpan = new TextAppearanceSpan(getContext(), R.style.keywordTagText);
        countSpan = new TextAppearanceSpan(getContext(), R.style.keywordTagCount);
    }

    public KeywordTagView(Context context, AttributeSet attrs) {
        this(context, attrs, null);
    }

    public KeywordTagView(Context context, KeywordFilter keywordFilter, int count, boolean dismissible, KeywordTagViewListener listener) {
        this(context, null, keywordFilter);
        this.count = count;
        this.dismissible = dismissible;
        this.listener = listener;
        updateComponents();
    }

    private void updateComponents() {
        TextView keywordTextView = (TextView) findViewById(R.id.view_keyword_tag_keyword);
        LinearLayout tagContainer = (LinearLayout) findViewById(R.id.view_keyword_tag_container);

        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb = append(sb, keywordFilter.getKeyword(), keywordSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (count != -1) {
            sb = append(sb, "  (" + String.valueOf(count) + ")", countSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        keywordTextView.setText(sb, TextView.BufferType.SPANNABLE);

        if (isInEditMode()) {
            return;
        }

        if (dismissible) {
            tagContainer.setBackgroundResource(R.drawable.keyword_tag_background_active);
            int drawableResId = keywordFilter.isInclusive() ? R.drawable.keyword_tag_filter_add : R.drawable.keyword_tag_filter_minus;
            keywordTextView.setCompoundDrawablesWithIntrinsicBounds(drawableResId, 0, R.drawable.keyword_tag_close_clear_cancel_full, 0);
            keywordTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.app_text_white));
        } else {
            tagContainer.setBackgroundResource(R.drawable.keyword_tag_background);
            keywordTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            keywordTextView.setTextColor(ContextCompat.getColor(getContext(), R.color.app_text_primary));
        }

        keywordTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onKeywordTagViewTouched();
            }
        });
        keywordTextView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                Drawable d = ((TextView) v).getCompoundDrawables()[2];
                if (d == null) {
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (event.getX() >= (v.getRight() - d.getBounds().width())) {
                        onDismissed();
                        return true;
                    }
                }
                return false;
            }
        });
    }

    private void onKeywordTagViewTouched() {
        if (this.listener != null) {
            this.listener.onKeywordTagViewTouched(this);
        }
    }

    public KeywordFilter getKeywordFilter() {
        return keywordFilter;
    }

    public boolean isDismissible() {
        return dismissible;
    }

    /**
     * Replaces instance of internal KeywordFilter with one that toggles the previous one's inclusive mode
     */
    public KeywordFilter toogleFilterInclusionMode() {
        KeywordFilter oldKeywordFilter = getKeywordFilter();
        KeywordFilter newKeywordFilter = new KeywordFilter(!oldKeywordFilter.isInclusive(), oldKeywordFilter.getKeyword(), oldKeywordFilter.getFeature());
        this.keywordFilter = newKeywordFilter;
        updateComponents();
        return newKeywordFilter;
    }

    public void setListener(KeywordTagViewListener listener) {
        this.listener = listener;
    }

    private void onDismissed() {
        if (listener != null) {
            try {
                listener.onKeywordTagViewDismissed(this);
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
            }
        }
    }

    // for API 16 compatibility
    private static SpannableStringBuilder append(SpannableStringBuilder sb, CharSequence text, Object what, int flags) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(what, start, sb.length(), flags);
        return sb;
    }
}
