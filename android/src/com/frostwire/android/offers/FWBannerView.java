/*
 *     Created by the FrostWire Android team
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.frostwire.android.R;
import com.frostwire.util.Logger;

/**
 * Simple in-house banner view used to display FrostWire support promotions.
 * This replaces the previous MAX/third-party integration while keeping a
 * compatible public API for the rest of the application.
 */
public class FWBannerView extends LinearLayout {

    public static final String UNIT_ID_HOME = "support_home";
    public static final String UNIT_ID_PREVIEW_PLAYER_VERTICAL = "support_preview_vertical";
    static final String UNIT_ID_SEARCH_HEADER = "support_search_header";
    public static final String UNIT_ID_AUDIO_PLAYER = "support_audio_player";
    public static final String UNIT_ID_PREVIEW_PLAYER_HORIZONTAL = "support_preview_horizontal";
    public static final String UNIT_ID_INTERSTITIAL_MOBILE = "support_interstitial";
    public static final String UNIT_ID_REWARDED_AD = "support_rewarded";

    public enum Layers {
        APPLOVIN,
        FALLBACK,
        ALL
    }

    public interface OnBannerDismissedListener {
        void dispatch();
    }

    public interface OnBannerLoadedListener {
        void dispatch();
    }

    private static final Logger LOG = Logger.getLogger(FWBannerView.class);

    private LinearLayout supportContainer;
    private ImageView supportIcon;
    private TextView supportTitle;
    private TextView supportMessage;
    private TextView supportBadge;
    private Button supportAction;
    private ImageButton dismissButton;

    private OnBannerDismissedListener onBannerDismissedListener;
    private OnBannerLoadedListener onBannerLoadedListener;

    private SupportOffer currentOffer;

    public FWBannerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FWBannerView(Context context,
                        @Nullable AttributeSet attrs,
                        boolean showFallbackBannerOnDismiss,
                        boolean showDismissButton,
                        boolean showRemoveAdsTextView,
                        String adId) {
        super(context, attrs);
        init(context);
        setShowDismissButton(showDismissButton);
    }

    private void init(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.view_frostwire_banner, this, true);

        supportContainer = findViewById(R.id.fwbanner_support_container);
        supportIcon = findViewById(R.id.fwbanner_support_icon);
        supportTitle = findViewById(R.id.fwbanner_support_title);
        supportMessage = findViewById(R.id.fwbanner_support_message);
        supportBadge = findViewById(R.id.fwbanner_support_badge);
        supportAction = findViewById(R.id.fwbanner_support_action);
        dismissButton = findViewById(R.id.fwbanner_dismiss_button);

        dismissButton.setOnClickListener(v -> dismissBanner());
        setVisibility(GONE);
    }

    public void setOnBannerLoadedListener(OnBannerLoadedListener listener) {
        this.onBannerLoadedListener = listener;
    }

    public void setOnBannerDismissedListener(OnBannerDismissedListener listener) {
        this.onBannerDismissedListener = listener;
    }

    public void setShowDismissButton(boolean showDismissButton) {
        if (dismissButton != null) {
            dismissButton.setVisibility(showDismissButton ? VISIBLE : GONE);
        }
    }

    public void loadMaxBanner() {
        if (Offers.disabledAds()) {
            setVisibility(GONE);
            return;
        }
        bindOffer(SupportOffer.random());
    }

    public void loadFallbackBanner(String ignoredAdUnitId) {
        loadMaxBanner();
    }

    public void destroy() {
        onBannerDismissedListener = null;
        onBannerLoadedListener = null;
        currentOffer = null;
        setVisibility(GONE);
    }

    public void setLayersVisibility(Layers layer, boolean visible) {
        int visibility = visible ? VISIBLE : GONE;
        if (layer == Layers.ALL) {
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

    public boolean isLoaded() {
        return getVisibility() == VISIBLE && currentOffer != null;
    }

    private void bindOffer(SupportOffer offer) {
        currentOffer = offer;

        if (supportContainer == null) {
            return;
        }

        if (offer.iconRes != 0) {
            supportIcon.setImageResource(offer.iconRes);
            supportIcon.setVisibility(VISIBLE);
        } else {
            supportIcon.setVisibility(GONE);
        }

        supportTitle.setText(offer.titleRes);
        supportMessage.setText(offer.messageRes);
        supportAction.setText(offer.actionTextRes);

        if (offer.badgeTextRes != 0) {
            supportBadge.setText(offer.badgeTextRes);
            supportBadge.setVisibility(VISIBLE);
        } else {
            supportBadge.setVisibility(GONE);
        }

        View.OnClickListener action = v -> offer.open(getContext());
        supportContainer.setOnClickListener(action);
        supportAction.setOnClickListener(action);

        setVisibility(VISIBLE);
        supportContainer.setVisibility(VISIBLE);

        if (onBannerLoadedListener != null) {
            onBannerLoadedListener.dispatch();
        }
    }

    private void dismissBanner() {
        setVisibility(GONE);
        if (onBannerDismissedListener != null) {
            onBannerDismissedListener.dispatch();
        }
    }
}
