/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
  *           Marcelina Knitter (@marcelinkaaa)
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

package com.frostwire.android.gui.fragments;

import android.os.Build;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractFragment;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author gubatron
 * @author aldenml
 * @author marcelinkaaa
 */
public final class AboutFragment extends AbstractFragment {

    public AboutFragment() {
        super(R.layout.fragment_about);
    }

    @Override
    protected void initComponents(View rootView) {

        //Title, build number and changelog setup
        final TextView title = findView(rootView, R.id.fragment_about_title);
        final String basicOrPlus = Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? "Basic" : "Plus";
        title.setText("FrostWire " + basicOrPlus + " v" + Constants.FROSTWIRE_VERSION_STRING);

        final TextView buildNumber = findView(rootView, R.id.fragment_about_build_number);
        buildNumber.setText("build " + BuildConfig.VERSION_CODE + ", sdk level " + Build.VERSION.SDK_INT);

        final TextView changelog = findView(rootView, R.id.fragment_about_changelog);

        changelog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtils.openURL(v.getContext(), Constants.CHANGELOG_URL);
            }
        });

        //Love FrostWire button and social media icons
        final Button loveFrostWireButton = findView(rootView, R.id.fragment_about_love_frostwire);
        loveFrostWireButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onLoveFrostWire();
            }
        });

        final ImageButton facebookButton = findView(rootView, R.id.fragment_about_facebook_button);
        final ImageButton twitterButton = findView(rootView, R.id.fragment_about_twitter_button);
        final ImageButton redditButton = findView(rootView, R.id.fragment_about_reddit_button);
        final ImageButton githubButton = findView(rootView, R.id.fragment_about_github_button);

        final String referrerParam = "?ref=android_about";

        facebookButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtils.openURL(v.getContext(), Constants.SOCIAL_URL_FACEBOOK_PAGE + referrerParam);
            }
        });

        twitterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtils.openURL(v.getContext(), Constants.SOCIAL_URL_TWITTER_PAGE + referrerParam);
            }
        });

        redditButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtils.openURL(v.getContext(), Constants.SOCIAL_URL_REDDIT_PAGE + referrerParam);
            }
        });

        githubButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtils.openURL(v.getContext(), Constants.SOCIAL_URL_GITHUB_PAGE + referrerParam);
            }
        });

        //Remaining elements including text content
        final TextView stickersShop = findView(rootView, R.id.fragment_about_stickers);
        final TextView sendFeedback = findView(rootView, R.id.fragment_about_feedback);
        final TextView translateHelp = findView(rootView, R.id.fragment_about_translate);

        stickersShop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtils.openURL(v.getContext(), Constants.STICKERS_SHOP_URL);
            }
        });

        sendFeedback.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtils.openURL(v.getContext(), Constants.CONTACT_US_URL);
            }
        });

        translateHelp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIUtils.openURL(v.getContext(), Constants.TRANSLATE_HELP_URL);
            }
        });

        final TextView content = findView(rootView, R.id.fragment_about_content);
        content.setText(Html.fromHtml(getAboutText()));
        content.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void onLoveFrostWire() {
        UIUtils.openURL(getActivity(), Constants.FROSTWIRE_GIVE_URL + "plus-about");
    }

    private String getAboutText() {
        try {
            InputStream raw = getResources().openRawResource(R.raw.about);
            return IOUtils.toString(raw, "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }
}
