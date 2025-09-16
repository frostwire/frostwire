/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;

import java.util.ArrayList;
import java.util.List;


public class RichNotification extends LinearLayout {

    private static final Typeface ROBOTO_LIGHT;

    static {
        ROBOTO_LIGHT = createRobotoLight();
    }

    private static Typeface createRobotoLight() {
        Typeface r;
        try {
            r = Typeface.create("sans-serif-light", Typeface.NORMAL);
        } catch (Throwable e) {
            // in case of some bad behavior, default to regular roboto
            r = Typeface.SANS_SERIF;
        }
        return r;
    }

	public static final List<Integer> wasDismissed = new ArrayList<>();
	private String title;
	private String description;
	private Drawable icon;
	private int numberOfActionLinks;
    private int actionLinksHorizontalMargin;
	private OnClickListener clickListener;
	
	public RichNotification(Context context, AttributeSet attrs) {
		super(context, attrs);
		TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.RichNotification);
		icon = attributes.getDrawable(R.styleable.RichNotification_rich_notification_icon);
		title = attributes.getString(R.styleable.RichNotification_rich_notification_title);
		description = attributes.getString(R.styleable.RichNotification_rich_notification_description);
		numberOfActionLinks = attributes.getInteger(R.styleable.RichNotification_rich_notification_number_of_action_links, 0);
		actionLinksHorizontalMargin = attributes.getInteger(R.styleable.RichNotification_rich_notification_action_links_horizontal_margin, 5);
        clickListener = null;
        attributes.recycle();
	}

    public void setOnClickListener(OnClickListener listener) {
        clickListener = listener;
    }

    public OnClickListener getOnClickListener() { return clickListener; }

    public boolean wasDismissed() {
        return wasDismissed.contains(this.getId());
    }

    /**
     * Removes all previous action links if they were there
     * and adds the corresponding TextViews.
     */
    public void updateActionLinks(RichNotificationActionLink ... actionLinks) {
		LinearLayout actionLinksContainer = findViewById(R.id.view_rich_notification_action_links);
		boolean gotActionLinks = false;
        if (actionLinks != null && actionLinks.length > 0) {
			gotActionLinks = true;
            actionLinksContainer.setVisibility(View.INVISIBLE);

            while (actionLinksContainer.getChildCount() > 0) {
                actionLinksContainer.getChildAt(0).setOnClickListener(null);
                actionLinksContainer.removeViewAt(0);
            }

            for (RichNotificationActionLink actionLink : actionLinks) {
                View v = actionLink.getView();
                if (v != null) {
                    actionLinksContainer.addView(v);
                    ((LinearLayout.LayoutParams) v.getLayoutParams()).setMargins(actionLinksHorizontalMargin, 0, actionLinksHorizontalMargin, 0);
                    v.requestLayout();
                }
            }
        }
		actionLinksContainer.setVisibility(gotActionLinks? View.VISIBLE : View.GONE);
    }

    public void setDescription(String newDescription) {
        description = newDescription;
        updateTextViewText(R.id.view_rich_notification_description, description, null);
    }

    @Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		View.inflate(getContext(), R.layout.view_rich_notification, this);
		
		ImageView imageViewIcon = findViewById(R.id.view_rich_notification_icon);
		if (imageViewIcon != null && icon != null) {
			imageViewIcon.setBackground(icon);
		}
		
		OnClickListener onClickNotificationListener = v -> onClickNotification();

		TextView textViewTitle = updateTextViewText(R.id.view_rich_notification_title, title, onClickNotificationListener);
		TextView textViewDescription = updateTextViewText(R.id.view_rich_notification_description, description, onClickNotificationListener);
		
		textViewTitle.setTypeface(ROBOTO_LIGHT, Typeface.BOLD);
		textViewDescription.setTypeface(ROBOTO_LIGHT, Typeface.NORMAL);
		
		ImageView dismissButton = findViewById(R.id.view_rich_notification_close_button);
		dismissButton.setOnClickListener(v -> onDismiss());

		LinearLayout actionLinksContainer = findViewById(R.id.view_rich_notification_action_links);
		actionLinksContainer.setVisibility(numberOfActionLinks > 0 ? View.VISIBLE : View.GONE);
	}
	
	private TextView updateTextViewText(int textViewId, CharSequence text, OnClickListener onClickNotificationListener) {
		TextView textView = findViewById(textViewId);

		if (textView != null && (text == null || text.length() == 0)) {
			textView.setVisibility(View.GONE);
			return textView;
		}

		if (textView != null && text != null) {
			textView.setText(text);
		}
		
		if (textView != null && onClickNotificationListener != null) {
			textView.setOnClickListener(onClickNotificationListener);
		}
		
		return textView;
	}


    protected void onDismiss() {
		wasDismissed.add(getId());
		setVisibility(View.GONE);
	}

	protected void onClickNotification() {
		if (clickListener != null) {
			clickListener.onClick(this);
		}
	}
}
