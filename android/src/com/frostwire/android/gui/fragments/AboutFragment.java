/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2018, FrostWire(R). All rights reserved.
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
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.frostwire.android.BuildConfig;
import com.frostwire.android.R;
import com.frostwire.android.core.Constants;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.android.gui.views.AbstractFragment;
import com.frostwire.jlibtorrent.LibTorrent;

import org.apache.commons.io.IOUtils;

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
    protected void initComponents(View rootView, Bundle savedInstanceState) {

        //Title, build number and changelog setup
        TextView title = findView(rootView, R.id.fragment_about_title);
        String basicOrPlus = (String) getText(Constants.IS_GOOGLE_PLAY_DISTRIBUTION ? R.string.basic : R.string.plus);
        title.setText("FrostWire " + basicOrPlus + " v" + Constants.FROSTWIRE_VERSION_STRING);

        TextView buildNumber = findView(rootView, R.id.fragment_about_build_number);
        buildNumber.setText(getText(R.string.build) + " " + BuildConfig.VERSION_CODE + ", sdk level " + Build.VERSION.SDK_INT);

        TextView jlibtorrentVersion = findView(rootView, R.id.fragment_about_jlibtorrent_version);
        jlibtorrentVersion.setText(jlibtorrentVersion());

        TextView changelog = findView(rootView, R.id.fragment_about_changelog);
        setupClickUrl(changelog, Constants.CHANGELOG_URL);

        //Love FrostWire button and social media icons
        Button loveFrostWireButton = findView(rootView, R.id.fragment_about_love_frostwire);
        setupClickUrl(loveFrostWireButton, Constants.FROSTWIRE_GIVE_URL + "plus-about");

        ImageButton facebookButton = findView(rootView, R.id.fragment_about_facebook_button);
        ImageButton twitterButton = findView(rootView, R.id.fragment_about_twitter_button);
        ImageButton redditButton = findView(rootView, R.id.fragment_about_reddit_button);
        ImageButton githubButton = findView(rootView, R.id.fragment_about_github_button);
        ImageButton slackButton = findView(rootView, R.id.fragment_about_slack_button);

        String referrerParam = "?ref=android_about";
        setupClickUrl(facebookButton, Constants.SOCIAL_URL_FACEBOOK_PAGE + referrerParam);
        setupClickUrl(twitterButton, Constants.SOCIAL_URL_TWITTER_PAGE + referrerParam);
        setupClickUrl(redditButton, Constants.SOCIAL_URL_REDDIT_PAGE + referrerParam);
        setupClickUrl(githubButton, Constants.SOCIAL_URL_GITHUB_PAGE + referrerParam);
        setupClickUrl(slackButton, Constants.SOCIAL_URL_SLACK_PAGE + referrerParam);

        //Remaining elements including text content
        TextView supportFrostWire = findView(rootView, R.id.fragment_about_support_frostwire);
        ImageView supportFrostWireDivider = findView(rootView, R.id.fragment_about_support_frostwire_divider);
        TextView translateHelp = findView(rootView, R.id.fragment_about_translate);
        TextView contactUs = findView(rootView, R.id.fragment_about_contact_us);

        setupClickUrl(supportFrostWire, Constants.STICKERS_SHOP_URL);
        setupClickUrl(translateHelp, Constants.TRANSLATE_HELP_URL);
        setupClickUrl(contactUs, Constants.CONTACT_US_URL);

        TextView content = findView(rootView, R.id.fragment_about_content);
        content.setText(Html.fromHtml(getAboutText()));
        content.setMovementMethod(LinkMovementMethod.getInstance());

        if (Constants.IS_GOOGLE_PLAY_DISTRIBUTION) {
            loveFrostWireButton.setVisibility(View.GONE);
            supportFrostWire.setVisibility(View.GONE);
            supportFrostWireDivider.setVisibility(View.GONE);
        }
    }

    private String getAboutText() {
        try {
            InputStream raw = getResources().openRawResource(R.raw.about);
            return IOUtils.toString(raw, "UTF-8");
        } catch (Throwable e) {
            return "";
        }
    }

    private static void setupClickUrl(View v, final String url) {
        v.setOnClickListener(view -> UIUtils.openURL(view.getContext(), url));
    }

    private static String jlibtorrentVersion() {
        StringBuilder sb = new StringBuilder();
        sb.append("jlibtorrent v").append(LibTorrent.jlibtorrentVersion());

        if (LibTorrent.hasArmNeonSupport()) {
            sb.append("(arm neon)");
        }

        return sb.toString();
    }
}
