/*
 *     Created by Angel Leon (@gubatron)
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

package com.frostwire.android.gui.views.preference;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;

/**
 * See settings_application.xml
 */
public class CommunityButtonsPreference extends Preference {
    public CommunityButtonsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.view_preference_community_buttons);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        setupCommunityButtons(holder);
    }

    /**
     * Event handlers for community links/imagebuttons
     */
    private void setupCommunityButtons(PreferenceViewHolder holder) {
        Button blogButton = (Button) holder.findViewById(R.id.view_preference_community_blog_button);
        Button rateOurAppButton = (Button) holder.findViewById(R.id.view_preference_community_rate_our_app_button);

        ImageButton facebookButton = (ImageButton) holder.findViewById(R.id.view_preference_community_facebook_button);
        ImageButton githubButton = (ImageButton) holder.findViewById(R.id.view_preference_community_github_button);
        ImageButton redditButton = (ImageButton) holder.findViewById(R.id.view_preference_community_reddit_button);
        ImageButton chatButton = (ImageButton) holder.findViewById(R.id.view_preference_community_slack_button);
        ImageButton twitterButton = (ImageButton) holder.findViewById(R.id.view_preference_community_twitter_button);

        String referrerParam = "?ref=android_preferences";
        UIUtils.setupClickUrl(blogButton, Constants.FROSTWIRE_BLOG_URL + referrerParam);
        UIUtils.setupClickUrl(facebookButton, Constants.SOCIAL_URL_FACEBOOK_PAGE + referrerParam);
        UIUtils.setupClickUrl(twitterButton, Constants.SOCIAL_URL_TWITTER_PAGE + referrerParam);
        UIUtils.setupClickUrl(redditButton, Constants.SOCIAL_URL_REDDIT_PAGE + referrerParam);
        UIUtils.setupClickUrl(githubButton, Constants.SOCIAL_URL_GITHUB_PAGE + referrerParam);
        UIUtils.setupClickUrl(chatButton, Constants.SOCIAL_URL_SLACK_PAGE + referrerParam);

        rateOurAppButton.setOnClickListener(v -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse("market://details?id=" + Constants.APP_PACKAGE_NAME));
                    try {
                        getContext().startActivity(intent);
                    } catch (Throwable ignored) {
                    }
                }
        );
    }

}
