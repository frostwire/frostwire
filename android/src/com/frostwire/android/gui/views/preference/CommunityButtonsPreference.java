/*
 * Created by Angel Leon (@gubatron)
 * Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 *
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
        UIUtils.setupClickUrl(blogButton, "https://blog.frostwire.com/" + referrerParam);
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
