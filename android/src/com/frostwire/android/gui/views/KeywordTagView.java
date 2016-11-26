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
import android.content.res.TypedArray;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
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
 * Created on 3/26/17.
 */
public class KeywordTagView extends LinearLayout {

    private static Logger LOG = Logger.getLogger(KeywordTagView.class);
    private boolean dismissable;
    private KeywordFilter keywordFilter;
    private int count;
    private KeywordTagViewListener listener;

    public interface KeywordTagViewListener {
        void onKeywordTagViewDismissed(KeywordTagView view);
        void onKeywordTagViewTouched(KeywordTagView view);
    }

    public KeywordTagView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        if (attrs != null) {
            TypedArray attributes = context.getTheme().obtainStyledAttributes(attrs, R.styleable.KeywordTagView, 0, 0);
            String keyword = attributes.getString(R.styleable.KeywordTagView_keyword_tag_keyword);
            count = attributes.getInteger(R.styleable.KeywordTagView_keyword_tag_count, 0);
            boolean inclusive = attributes.getBoolean(R.styleable.KeywordTagView_keyword_tag_inclusive, true);
            dismissable = attributes.getBoolean(R.styleable.KeywordTagView_keyword_tag_dismissable, true);
            keywordFilter = new KeywordFilter(inclusive, keyword);
            attributes.recycle();
        }
    }

    public KeywordTagView(Context context, KeywordFilter keywordFilter, int count, boolean dismissable, KeywordTagViewListener listener) {
        this(context, null);
        this.keywordFilter = keywordFilter;
        this.count = count;
        this.dismissable = dismissable;
        this.listener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_keyword_tag, this);
        updateComponents();
    }

    private void updateComponents() {
        TextView inclusiveIndicatorTextView = (TextView) findViewById(R.id.view_keyword_tag_inclusive_indicator);
        TextView keywordTextView = (TextView) findViewById(R.id.view_keyword_tag_keyword);
        TextView countTextView = (TextView) findViewById(R.id.view_keyword_tag_count);
        TextView dismissTextView = (TextView) findViewById(R.id.view_keyword_tag_dismiss);
        inclusiveIndicatorTextView.setText(keywordFilter.isInclusive() ? "+":"-");
        keywordTextView.setText(keywordFilter.getKeyword());
        countTextView.setText("(" + String.valueOf(count) + ")");
        if (dismissable) {
            dismissTextView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDismissed();
                }
            });
        } else {
            dismissTextView.setVisibility(View.GONE);
        }
        OnClickListener onClickListener = new OnClickListener() {
            @Override
            public void onClick(View v) {
                onKeywordTagViewTouched();
            }
        };
        inclusiveIndicatorTextView.setOnClickListener(onClickListener);
        keywordTextView.setOnClickListener(onClickListener);
        countTextView.setOnClickListener(onClickListener);
    }

    private void onKeywordTagViewTouched() {
        if (this.listener != null) {
            this.listener.onKeywordTagViewTouched(this);
        }
    }

    public KeywordFilter getKeywordFilter() {
        return keywordFilter;
    }

    public int getCount() {
        return count;
    }

    public boolean isDismissable() {
        return dismissable;
    }

    /** Replaces instance of internal KeywordFilter with one that toggles the previous one's inclusive mode */
    public KeywordFilter toogleFilterInclusionMode() {
        KeywordFilter oldKeywordFilter = getKeywordFilter();
        KeywordFilter newKeywordFilter = new KeywordFilter(!oldKeywordFilter.isInclusive(), oldKeywordFilter.getKeyword());
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
}
