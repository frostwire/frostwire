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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.search.KeywordDetector;
import com.frostwire.search.KeywordFilter;
import com.google.android.flexbox.FlexboxLayout;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public final class KeywordTagView extends AppCompatTextView {

    private int count;
    private boolean dismissible;
    private OnActionListener listener;

    private KeywordFilter keywordFilter;

    private final TextAppearanceSpan keywordSpan;
    private final TextAppearanceSpan countSpan;
    private final FlexboxLayout.LayoutParams layoutParams;

    private KeywordTagView(Context context, AttributeSet attrs, KeywordFilter keywordFilter) {
        super(context, attrs);

        if (keywordFilter == null) {
            TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.KeywordTagView, 0, 0);
            count = attributes.getInteger(R.styleable.KeywordTagView_keyword_tag_count, 0);
            dismissible = attributes.getBoolean(R.styleable.KeywordTagView_keyword_tag_dismissable, true);
            boolean inclusive = attributes.getBoolean(R.styleable.KeywordTagView_keyword_tag_inclusive, true);
            String keyword = attributes.getString(R.styleable.KeywordTagView_keyword_tag_keyword);
            keywordFilter = new KeywordFilter(inclusive, keyword != null ? keyword : "[Text]", KeywordDetector.Feature.MANUAL_ENTRY);
            attributes.recycle();
        }

        this.keywordFilter = keywordFilter;

        keywordSpan = new TextAppearanceSpan(getContext(), R.style.keywordTagText);
        countSpan = new TextAppearanceSpan(getContext(), R.style.keywordTagCount);

        layoutParams = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, toPx(34));
        layoutParams.setMargins(0, 0, toPx(6), toPx(8));

        setPadding(toPx(12), toPx(4), toPx(12), toPx(4));
        setMinHeight(toPx(34));
        setGravity(Gravity.CENTER_VERTICAL);
        setCompoundDrawablePadding(toPx(6));
    }

    public KeywordTagView(Context context, AttributeSet attrs) {
        this(context, attrs, null);
        updateComponents();
    }

    public KeywordTagView(Context context, KeywordFilter keywordFilter, int count, boolean dismissible, OnActionListener listener) {
        this(context, null, keywordFilter);
        this.count = count;
        this.dismissible = dismissible;
        this.listener = listener;
        updateComponents();
    }

    @Override
    public FlexboxLayout.LayoutParams getLayoutParams() {
        return layoutParams;
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
    public KeywordFilter toggleFilterInclusionMode() {
        keywordFilter = keywordFilter.negate();
        updateComponents();
        invalidate();
        return keywordFilter;
    }

    private void updateComponents() {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb = append(sb, keywordFilter.getKeyword(), keywordSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (count != -1) {
            sb = append(sb, "  (" + String.valueOf(count) + ")", countSpan, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        setText(sb, TextView.BufferType.NORMAL);

        if (dismissible) {
            setBackgroundResource(R.drawable.keyword_tag_background_active);
            int drawableResId = keywordFilter.isInclusive() ? R.drawable.keyword_tag_filter_add : R.drawable.keyword_tag_filter_minus;
            setCompoundDrawablesWithIntrinsicBounds(drawableResId, 0, R.drawable.keyword_tag_close_clear_cancel_full, 0);
            setTextColor(ContextCompat.getColor(getContext(), R.color.app_text_white));
        } else {
            setBackgroundResource(R.drawable.keyword_tag_background);
            setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            setTextColor(ContextCompat.getColor(getContext(), R.color.app_text_primary));
        }

        setOnClickListener(v -> onTouched());
        if (dismissible) {
            setOnTouchListener((v, event) -> {
                if (event.getAction() != MotionEvent.ACTION_UP) {
                    return false;
                }
                TextView tv = (TextView) v;
                if (event.getX() >= tv.getWidth() - tv.getTotalPaddingRight()) {
                    onDismissed();
                    return true;
                }
                return false;
            });
        }
    }

    private void onTouched() {
        if (listener != null) {
            listener.onTouched(this);
        }
    }

    private void onDismissed() {
        if (listener != null) {
            listener.onDismissed(this);
        }
    }

    private int toPx(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    // for API 16 compatibility
    private static SpannableStringBuilder append(SpannableStringBuilder sb, CharSequence text, Object what, int flags) {
        int start = sb.length();
        sb.append(text);
        sb.setSpan(what, start, sb.length(), flags);
        return sb;
    }

    public interface OnActionListener {

        void onTouched(KeywordTagView view);

        void onDismissed(KeywordTagView view);
    }
}
