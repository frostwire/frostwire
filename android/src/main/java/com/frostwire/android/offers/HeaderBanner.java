/*
 *     Created by the FrostWire Android team
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.android.offers;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.util.Logger;

/**
 * In-house header banner used across search related screens.
 */
public final class HeaderBanner extends LinearLayout {

    public enum VisibleBannerType {
        APPLOVIN,
        FALLBACK,
        ALL
    }

    private static final Logger LOG = Logger.getLogger(HeaderBanner.class);
    private static final long DISMISS_INTERVAL_MS = 60_000L;

    private LinearLayout supportContainer;
    private TextView titleView;
    private TextView messageView;
    private Button actionButton;
    private ImageButton dismissButton;

    private long lastDismissedTimestamp = 0L;

    public HeaderBanner(Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.view_header_banner, this, true);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        supportContainer = findViewById(R.id.view_search_header_banner_support_container);
        titleView = findViewById(R.id.view_search_header_banner_title);
        messageView = findViewById(R.id.view_search_header_banner_message);
        actionButton = findViewById(R.id.view_search_header_banner_action);
        dismissButton = findViewById(R.id.view_search_header_banner_dismiss_banner_button);

        if (dismissButton != null) {
            dismissButton.setOnClickListener(v -> dismissBanner());
        }
    }

    public static void onResumeHideOrUpdate(HeaderBanner component) {
        if (component == null) {
            return;
        }
        if (Offers.disabledAds()) {
            component.setBannerViewVisibility(VisibleBannerType.ALL, false);
        } else {
            component.updateComponents();
        }
    }

    public static void destroy(HeaderBanner component) {
        if (component != null) {
            component.onDestroy();
        }
    }

    public void onDestroy() {
        setBannerViewVisibility(VisibleBannerType.ALL, false);
        supportContainer = null;
        titleView = null;
        messageView = null;
        actionButton = null;
        dismissButton = null;
    }

    public void updateComponents() {
        if (Offers.disabledAds()) {
            setBannerViewVisibility(VisibleBannerType.ALL, false);
            return;
        }
        long now = System.currentTimeMillis();
        if ((now - lastDismissedTimestamp) < DISMISS_INTERVAL_MS) {
            LOG.info("HeaderBanner.updateComponents(): debounced by dismissal interval");
            return;
        }
        bindOffer(SupportOffer.random());
    }

    public void setBannerViewVisibility(VisibleBannerType bannerType, boolean visible) {
        int visibility = visible ? VISIBLE : GONE;
        if (bannerType == VisibleBannerType.ALL) {
            setVisibility(visibility);
            return;
        }
        if (supportContainer != null) {
            supportContainer.setVisibility(visibility);
        }
        if (visible) {
            setVisibility(VISIBLE);
        } else if (supportContainer == null || supportContainer.getVisibility() != VISIBLE) {
            setVisibility(GONE);
        }
    }

    private void bindOffer(SupportOffer offer) {
        if (supportContainer == null) {
            return;
        }
        titleView.setText(offer.titleRes);
        messageView.setText(offer.messageRes);
        actionButton.setText(offer.actionTextRes);

        UIUtils.setupClickUrl(supportContainer, offer.getUrl());
        UIUtils.setupClickUrl(actionButton, offer.getUrl());

        setBannerViewVisibility(VisibleBannerType.ALL, true);
        dismissButton.setVisibility(VISIBLE);
    }

    private void dismissBanner() {
        lastDismissedTimestamp = System.currentTimeMillis();
        setBannerViewVisibility(VisibleBannerType.ALL, false);
    }
}
